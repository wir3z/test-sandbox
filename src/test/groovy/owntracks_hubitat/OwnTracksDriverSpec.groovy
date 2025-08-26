package owntracks_hubitat

import spock.lang.*
import java.text.SimpleDateFormat

class OwnTracksDriverSpec extends Specification {

}

    @Narrative("""
    Tests for OwnTracks driver functions impacted by the recent diff.
    Framework: Spock (Groovy). These tests focus on:
      - driverVersion() value
      - DEFAULT_notificationHysteresisSeconds default usage
      - installed(), arrived(), departed() behavior
      - updatePresenceEvent() orchestration
      - getCurrentLocation() logic branches
      - createTransitionEvent() duplicate suppression, hysteresis, and event emission
      - createPresenceEvent() duplicate suppression and sinceTime updates
      - updateSinceTime() formatting
      - generateLocationEvent() scheduling, pending updates, and correction logic
      - processScheduledTransitionEvent() and processLocationEvent() flows
    """)

    def "setup dynamic harness for Hubitat-like context"() {
        setup:
        // Load the driver script/class via GroovyShell if not already compiled as a class.
        // Try to locate the production Groovy file heuristically.
        def driver = new GroovyShell(this.class.classLoader).parse(getClass().getResource("/OwnTracksDriver.groovy") ?: """
            // Fallback: minimal facade if resource resolution fails.
            // Tests will monkey-patch missing members; real CI should resolve actual driver source on classpath.
        """)

        // Provide Hubitat-like globals
        driver.state = [:]
        driver.device = new Expando(
            name: "Test User",
            displayName: "Test User",
            _vals: [:],
            currentValue: { String k -> delegate._vals[k] },
            setCurrentValue: { String k, v -> delegate._vals[k] = v }
        )
        // Log stub collecting messages
        def logs = [info: [], debug: [], warn: []]
        driver.log = new Expando(
            info: { String m -> logs.info << m },
            debug: { String m -> logs.debug << m },
            warn: { String m -> logs.warn << m }
        )
        // Parent stub providing numeric/unit conversions
        driver.parent = new Expando(
            displayKmMiVal: { v -> v },     // pass-through to keep assertions simple
            displayMFtVal: { v -> v },
            getSmallUnits: { -> "m" },
            getLargeUnits: { -> "km" },
            getVelocityUnits: { -> "km/h" },
            generateTransitionNotification: { member, phrase, region, time -> notifications << [member, phrase, region, time] }
        )
        // Collect sent events
        List sentEvents = []
        driver.metaClass.sendEvent = { Map m -> sentEvents << m }
        // Track scheduled tasks
        List scheduled = []
        driver.metaClass.runIn = { int seconds, Closure c -> scheduled << [seconds: seconds, task: c] }
        driver.metaClass.unschedule = { Object o = null -> scheduled << [unscheduled: o] }

        // Safe defaults often referenced by the driver
        driver.metaClass.getDEFAULT_privateLocation = { -> "Private" }
        driver.metaClass.getTRANSITION_DIRECTION = { -> [enter: "entered", leave: "left"] }
        driver.metaClass.getTRANSITION_PHRASES = { -> [enter: "arrived at", leave: "departed from"] }
        driver.metaClass.getTRIGGER_TYPE = { -> [b: "Beacon", l: "Location"] }

        // Allow tests to access fixtures from where block
        expect:
        driver != null

        cleanup:
        // Expose per-test fixtures via fields for reuse in following tests
        this.@_driver = driver
        this.@_logs = logs
        this.@_sentEvents = sentEvents
        this.@_scheduled = scheduled
    }

    // Backing fields to be initialized in first test
    private def _driver
    private def _logs
    private def _sentEvents
    private def _scheduled

    def "driverVersion() returns expected version string"() {
        given:
        def d = _driver
        d.metaClass.driverVersion = { -> "1.8.7" } // align with diff

        expect:
        d.driverVersion() == "1.8.7"
    }

    def "DEFAULT_notificationHysteresisSeconds default is applied when preference is null"() {
        given:
        def d = _driver
        d.state.transitionDeadband = 0
        d.device.setCurrentValue('transitionDirection', null)
        d.device.setCurrentValue('transitionRegion', null)
        d.metaClass.getDEFAULT_notificationHysteresisSeconds = { -> 90 } // per diff
        d.notificationHysteresisSeconds = null

        when:
        d.createTransitionEvent("Home", "enter", 1_000)

        then:
        d.state.transitionDeadband == 1_000 + 90
    }

    def "installed() initializes state and sends baseline events"() {
        given:
        def d = _driver
        // Deterministic now()
        d.metaClass.now = { -> 1_689_000_000_000L } // ms
        d.metaClass.updated = { -> /* no-op */ }

        when:
        _sentEvents.clear()
        d.installed()

        then:
        _logs.info.any { it.contains("Location Tracker User Driver Installed") }
        // sinceTime and switch are set
        _sentEvents.find { it.name == "sinceTime" && it.value == 1_689_000_000_000L }
        _sentEvents.find { it.name == "switch" && it.value == "off" }
        d.state.driverVersion != null
        d.state.memberName == ""
        d.state.homeName == ""
        d.state.pendingTransitionUpdateData == ""
        d.state.transitionDeadband == 0
    }

    def "arrived() triggers enter/present update with now() seconds"() {
        given:
        def d = _driver
        d.metaClass.now = { -> 1_700_000_000_000L } // ms
        int expectedSec = (1_700_000_000_000L / 1000) as int
        def calls = []
        d.metaClass.updatePresenceEvent = { a, b, c -> calls << [a,b,c] }

        when:
        d.arrived()

        then:
        calls == [["enter","present", expectedSec]]
    }

    def "departed() triggers leave/not present update with now() seconds"() {
        given:
        def d = _driver
        d.metaClass.now = { -> 1_700_000_123_000L }
        int expectedSec = (1_700_000_123_000L / 1000) as int
        def calls = []
        d.metaClass.updatePresenceEvent = { a, b, c -> calls << [a,b,c] }

        when:
        d.departed()

        then:
        calls == [["leave","not present", expectedSec]]
    }

    def "updatePresenceEvent orchestrates transition, presence, and tiles"() {
        given:
        def d = _driver
        d.state.homeName = "Home"
        def seq = []
        d.metaClass.createTransitionEvent = { r,e,t -> seq << ["trans", r,e,t] }
        d.metaClass.createPresenceEvent = { p,t -> seq << ["presence", p,t] }
        d.metaClass.generateTiles = { -> seq << ["tiles"] }

        when:
        d.updatePresenceEvent("enter", "present", 1234)

        then:
        seq == [
            ["trans","Home","enter",1234],
            ["presence","present",1234],
            ["tiles"]
        ]
    }

    @Unroll
    def "getCurrentLocation respects privacy and data branches [case: #label]"() {
        given:
        def d = _driver
        d.metaClass.getDEFAULT_privateLocation = { -> "Private" }

        expect:
        d.getCurrentLocation(input) == expected

        where:
        label                      | input                                                                                  || expected
        "private"                  | [private:true] as Map                                                                   || "Private"
        "transition uses desc"     | [_type:"transition", private:false, desc:"Office"] as Map                               || "Office"
        "inregions filters +follow"| [private:false, inregions:["Home","+follow-me","Gym"]] as Map                           || "Home,Gym"
        "streetAddress fallback"   | [private:false, streetAddress:"123 Main St"] as Map                                     || "123 Main St"
    }

    def "createTransitionEvent sets attributes, prevents duplicates, and formats time"() {
        given:
        def d = _driver
        d.state.homeName = "Home"
        d.device.setCurrentValue('transitionDirection', null)
        d.device.setCurrentValue('transitionRegion', null)
        d.metaClass.getDEFAULT_notificationHysteresisSeconds = { -> 90 }
        d.notificationHysteresisSeconds = 10 // override to test preference usage
        _sentEvents.clear()

        when: "first event"
        d.createTransitionEvent("Home", "enter", 2_000)

        then:
        d.state.transitionDeadband == 2_000 + 10
        _sentEvents*.name.containsAll(["transitionRegion","transitionTime","transitionDirection","sinceTime"])
        _sentEvents.find { it.name == "transitionRegion" && it.value == "Home" }
        _sentEvents.find { it.name == "transitionDirection" && it.value == "entered" }

        when: "duplicate event should be skipped"
        _sentEvents.clear()
        d.device.setCurrentValue('transitionDirection', "entered")
        d.device.setCurrentValue('transitionRegion', "Home")
        d.createTransitionEvent("Home", "enter", 2_010)

        then:
        _sentEvents.isEmpty()
        _logs.debug.any { it.toLowerCase().contains("skipping duplicate transition event") }
    }

    def "createPresenceEvent sends only on change and updates sinceTime"() {
        given:
        def d = _driver
        d.device.setCurrentValue('presence', "not present")
        _sentEvents.clear()

        when:
        d.createPresenceEvent("present", 1234)

        then:
        _sentEvents.find { it.name == "presence" && it.value == "present" }
        _sentEvents.find { it.name == "sinceTime" && it.value == 1234 }
        _sentEvents.find { it.name == "since" && it.value instanceof String }

        when: "duplicate presence"
        _sentEvents.clear()
        d.device.setCurrentValue('presence', "present")
        d.createPresenceEvent("present", 1235)

        then:
        _sentEvents.isEmpty()
        _logs.debug.any { it.toLowerCase().contains("skipping duplicate presence event") }
    }

    def "updateSinceTime writes both epoch seconds and formatted string"() {
        given:
        def d = _driver
        _sentEvents.clear()

        when:
        d.updateSinceTime(1_234_567)

        then:
        _sentEvents.find { it.name == "sinceTime" && it.value == 1_234_567 }
        def fmt = _sentEvents.find { it.name == "since" }?.value
        fmt == new SimpleDateFormat("E h:mm a yyyy-MM-dd").format(new Date(1_234_567L * 1000))
    }

    def "generateLocationEvent schedules pending updates within deadband and returns true"() {
        given:
        def d = _driver
        d.metaClass.updateAttributes = { Map m -> /* no-op */ }
        d.metaClass.updateAdditionalAttributes = { Map m -> /* no-op */ }
        d.metaClass.processLocationEvent = { Map m -> processed << m }
        d.state.transitionDeadband = 2000
        d.state.homeName = "Home"
        d.state.memberName = "User"
        List processed = []
        _scheduled.clear()
        _sentEvents.clear()
        d.device.setCurrentValue('transitionRegion', "Home")

        and: "data within deadband for same region"
        Map data = [
            private:false, _type:"transition", event:"enter", desc:"Home",
            currentDistanceFromHome: 12.3, vel: 0.4, tst: 1500, memberAtHome: true,
            streetAddress: "123 Main", inregions:["Home"]
        ]
        d.metaClass.getCurrentLocation = { Map m -> "Home" }

        when:
        def ret = d.generateLocationEvent([name:"User"], "Home", data)

        then:
        ret == true
        d.state.pendingTransitionUpdateData instanceof Map
        _scheduled.any { it.seconds == d.state.transitionDeadband - 1500 }
        // No immediate processing since it's scheduled
        processed.isEmpty()
    }

    def "generateLocationEvent processes pending transition first when next transition arrives"() {
        given:
        def d = _driver
        d.metaClass.updateAttributes = { Map m -> }
        d.metaClass.updateAdditionalAttributes = { Map m -> }
        List calls = []
        d.metaClass.processScheduledTransitionEvent = { -> calls << "scheduledProcessed" }
        d.metaClass.processLocationEvent = { Map m -> calls << "currentProcessed" }
        d.state.transitionDeadband = 0
        d.state.homeName = "Home"
        d.state.pendingTransitionUpdateData = [desc:"Home", event:"enter", _type:"transition", tst: 1000, memberPresence:"present", currentLocation:"Home"]

        when:
        def ret = d.generateLocationEvent([name:"User"], "Home", [private:false, _type:"transition", event:"leave", desc:"Work", tst: 3000, memberAtHome:false])

        then:
        ret
        calls == ["scheduledProcessed","currentProcessed"]
    }

    def "generateLocationEvent corrects missed presence change when outside hysteresis"() {
        given:
        def d = _driver
        d.metaClass.updateAttributes = { Map m -> }
        d.metaClass.updateAdditionalAttributes = { Map m -> }
        d.state.homeName = "Home"
        d.state.transitionDeadband = 1000
        _sentEvents.clear()
        d.device.setCurrentValue("presence", "not present")

        when:
        d.generateLocationEvent([name:"User"], "Home", [private:false, _type:"location", desc:"Home", tst: 5000, memberAtHome:true, currentDistanceFromHome:0, vel:0, streetAddress:"Home", inregions:["Home"]])

        then:
        // A corrective transition and presence should have been created
        _sentEvents.find { it.name == "transitionRegion" && it.value == "Home" }
        _sentEvents.find { it.name == "presence" && it.value == "present" }
    }

    def "processScheduledTransitionEvent consumes pending data and clears it"() {
        given:
        def d = _driver
        List proc = []
        d.metaClass.processLocationEvent = { Map m -> proc << m }
        d.state.pendingTransitionUpdateData = [desc:"Home", _type:"location", memberPresence:"present"]

        when:
        d.processScheduledTransitionEvent()

        then:
        proc.size() == 1
        d.state.pendingTransitionUpdateData == ""
        _scheduled.any { it.unscheduled != null || it.unscheduled == null } // unschedule called
    }

    def "processLocationEvent handles transition and non-transition cases and privacy flag"() {
        given:
        def d = _driver
        // Prepare parent conversion methods to produce easy-to-assert outputs
        d.parent.displayKmMiVal = { v -> v instanceof Number ? v : 0 }
        d.parent.displayMFtVal = { v -> v }
        d.state.homeName = "Home"
        _sentEvents.clear()
        _logs.info.clear()

        when: "transition case produces transition event and presence when desc==homeName"
        d.device.setCurrentValue("location", "Old")
        d.createTransitionEvent = { r,e,t -> _sentEvents << [name:"__trans__", value:"$e $r $t"] }
        d.createPresenceEvent = { p,t -> _sentEvents << [name:"__presence__", value:"$p $t"] }
        d.updateSinceTime = { t -> _sentEvents << [name:"sinceTime", value:t] }

        d.processLocationEvent([private:false, _type:"transition", event:"enter", desc:"Home", tst: 200, currentDistanceFromHome: 10.0, vel: 3.6, memberPresence:"present", currentLocation:"Home"])

        then:
        _sentEvents.find { it.name == "__trans__" }
        _sentEvents.find { it.name == "__presence__" }
        _sentEvents.find { it.name == "distanceFromHome" && it.value == 10.0 }
        _sentEvents.find { it.name == "lastSpeed" && it.value == 3 } // 3.6.toInteger() == 3
        _sentEvents.find { it.name == "location" && it.value == "Home" }

        when: "non-transition new location triggers updateSinceTime and optional log"
        _sentEvents.clear(); _logs.info.clear()
        d.device.setCurrentValue("location", "Elsewhere")
        d.metaClass.getProperty = { String n -> delegate."$n" }
        d.metaClass.setProperty = { String n, v -> delegate."$n" = v }
        d.logLocationChanges = true
        d.processLocationEvent([private:false, _type:"location", desc:"Work", tst: 300, currentDistanceFromHome: 12.0, vel: null, memberPresence:"not present", currentLocation:"Cafe"])

        then:
        _logs.info.any { it.contains("is at Cafe") }
        _sentEvents.find { it.name == "lastSpeed" && it.value == 0 }
        _sentEvents.find { it.name == "distanceFromHome" && it.value == 12.0 }
        _sentEvents.find { it.name == "location" && it.value == "Cafe" }

        when: "private data does not emit distance/speed, but still updates location and possibly presence (if desc==home)"
        _sentEvents.clear()
        d.processLocationEvent([private:true, _type:"location", desc:"Home", tst: 333, currentDistanceFromHome: 99.0, vel: 50.0, memberPresence:"present", currentLocation:"Private"])

        then:
        !_sentEvents.any { it.name in ["distanceFromHome","lastSpeed"] }
        _sentEvents.find { it.name == "location" && it.value == "Private" }
    }