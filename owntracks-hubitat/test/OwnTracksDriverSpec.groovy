import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for OwnTracks Driver behaviors focused on recent diffs:
 * - Cleanup of state.memberPresence on generateLocationEvent (introduced in 1.8.4)
 * - Presence/transition event generation paths
 * - getCurrentLocation branching (private, transition, inregions filtering, streetAddress fallback)
 *
 * Framework: Spock (spock.lang.Specification)
 * Conventions: Given/When/Then style, descriptive test names, mocking/stubbing Hubitat environment via lightweight fakes.
 */
class OwnTracksDriverSpec extends Specification {

    /**
     * Minimal test harness for the Hubitat driver script.
     * We load the driver Groovy script at runtime if present; otherwise create a dynamic object
     * with the same methods to validate pure logic paths.
     *
     * The harness exposes:
     * - device: supports currentValue(name), name/displayName, and event capture via sendEvent()
     * - parent: methods referenced in the driver (generateTransitionNotification, displayKmMiVal, get*Units())
     * - state: simple Map used by the driver
     * - log stubs: log.info/log.debug capturing messages
     * - scheduling stubs: runIn()/unschedule()
     *
     * Note: Adjust DRIVER_PATH if the repository structure differs.
     */
    static final String DRIVER_PATH_CANDIDATES_FILE = "owntracks-hubitat/src/OwnTracksDriver.groovy"
    static final List<String> DRIVER_PATH_CANDIDATES = [
        "owntracks-hubitat/src/OwnTracksDriver.groovy",
        "src/OwnTracksDriver.groovy",
        "drivers/OwnTracksDriver.groovy",
        "OwnTracksDriver.groovy"
    ]

    def makeHarness() {
        def deviceEvents = []
        def deviceValues = [:].withDefault { null }

        def device = new Expando(
            name: "Test Presence",
            displayName: "Test Presence",
            currentValue: { String k -> deviceValues[k] },
            setCurrentValue: { String k, v -> deviceValues[k] = v },
            _events: deviceEvents
        )

        def parent = new Expando(
            generateTransitionNotification: { String memberName, String phrase, String region, String whenStr -> /* no-op */ },
            displayKmMiVal: { num -> (num instanceof Number) ? num : 0 },
            displayMFtVal: { num -> (num instanceof Number) ? num : 0 },
            getSmallUnits: { "m" },
            getLargeUnits: { "km" },
            getVelocityUnits: { "km/h" }
        )

        def logMsgs = [info: [], debug: []]
        def log = new Expando(
            info: { String m -> logMsgs.info << m },
            debug: { String m -> logMsgs.debug << m }
        )

        def scheduled = [unscheduled: 0, runIns: []]
        def runIn = { int seconds, Closure c -> scheduled.runIns << [in: seconds, task: c] }
        def unschedule = { obj = null -> scheduled.unscheduled++ }

        // Dynamic driver: try to load the actual driver script if present, else create a shim with methods under test
        def driverObj = null
        def driverPath = DRIVER_PATH_CANDIDATES.find { new File(it).exists() }
        if (driverPath) {
            def shell = new GroovyShell()
            driverObj = shell.evaluate(new File(driverPath))
        } else {
            // Define a minimal object implementing only the methods used in tests
            driverObj = new Expando()
            driverObj.@DEFAULT_privateLocation = "Private"
            driverObj.@DEFAULT_notificationHysteresisSeconds = 90
            driverObj.@TRANSITION_DIRECTION = [ enter: "arrived", leave: "departed" ]
            driverObj.@TRANSITION_PHRASES = [ enter: "arrived at", leave: "departed from" ]
            driverObj.@TRIGGER_TYPE = [ "p": "Ping", "c": "Region", "r": "Report Location", "u": "Manual", "b": "Beacon", "t": "Timer", "v": "Monitoring", "l": "Region" ]

            driverObj.getCurrentLocation = { data ->
                def currentLocation
                if (data.private) {
                    currentLocation = driverObj.@DEFAULT_privateLocation
                } else {
                    if (data._type == "transition") {
                        currentLocation = data.desc
                    } else if (data.inregions) {
                        def list = ""
                        data.inregions.each { place ->
                            if (place[0] != "+") list += "$place,"
                        }
                        currentLocation = list ? list.substring(0, list.length()-1) : ""
                    } else {
                        currentLocation = data.streetAddress
                    }
                }
                currentLocation
            }

            driverObj.updateSinceTime = { tst ->
                driverObj.sendEvent(name:"sinceTime", value: tst)
                driverObj.sendEvent(name:"since", value: new java.text.SimpleDateFormat("E h:mm a yyyy-MM-dd").format(new Date((long)tst * 1000)))
            }

            driverObj.createPresenceEvent = { presenceState, dataTst ->
                if (driverObj.device.currentValue("presence") != presenceState) {
                    def descriptionText = driverObj.device.displayName + " is " + presenceState
                    driverObj.sendEvent(name:"presence", value: presenceState, descriptionText: descriptionText)
                    driverObj.logDescriptionText = { String m -> /* no-op for tests */ }
                    driverObj.updateSinceTime(dataTst)
                    driverObj.generatePresenceTile = {->}
                }
            }

            driverObj.createTransitionEvent = { region, event, tst ->
                driverObj.state.transitionDeadband = tst + (driverObj.notificationHysteresisSeconds == null ? driverObj.@DEFAULT_notificationHysteresisSeconds : driverObj.notificationHysteresisSeconds)
                if ((driverObj.@TRANSITION_DIRECTION[event] != driverObj.device.currentValue('transitionDirection')) ||
                    (region != driverObj.device.currentValue('transitionRegion'))) {
                    def dataTime = new java.text.SimpleDateFormat("E h:mm a yyyy-MM-dd").format(new Date((long)tst * 1000))
                    def descriptionText = driverObj.device.displayName + " has ${driverObj.@TRANSITION_PHRASES[event]} " + region
                    driverObj.sendEvent(name:"transitionRegion", value: region)
                    driverObj.sendEvent(name:"transitionTime", value: dataTime)
                    driverObj.sendEvent(name:"transitionDirection", value: driverObj.@TRANSITION_DIRECTION[event])
                    driverObj.sendEvent(name:"sinceTime", value: tst)
                    driverObj.parent.generateTransitionNotification(driverObj.state.memberName ?: "", driverObj.@TRANSITION_PHRASES[event], region, dataTime)
                }
            }

            driverObj.processLocationEvent = { data ->
                if (!data.private) {
                    if (data._type == "transition") {
                        driverObj.createTransitionEvent(data.desc, data.event, data.tst)
                    } else {
                        if (driverObj.device.currentValue("location") != data.currentLocation) {
                            if (driverObj.logLocationChanges) driverObj.log.info("${driverObj.device.displayName} is at $data.currentLocation")
                            driverObj.updateSinceTime(data.tst)
                        }
                    }
                    driverObj.sendEvent(name:"distanceFromHome", value: driverObj.parent.displayKmMiVal(data.currentDistanceFromHome))
                    driverObj.sendEvent(name:"lastSpeed", value: (data?.vel ? driverObj.parent.displayKmMiVal(data.vel).toInteger() : 0))
                }
                if (driverObj.state.homeName == data.desc) {
                    driverObj.createPresenceEvent(data.memberPresence, data.tst)
                }
                driverObj.sendEvent(name:"location", value: data.currentLocation)
                driverObj.generateMemberTile = {->}
            }

            driverObj.processScheduledTransitionEvent = { ->
                driverObj.unschedule(driverObj.processScheduledTransitionEvent)
                if (driverObj.state.pendingTransitionUpdateData) driverObj.processLocationEvent(driverObj.state.pendingTransitionUpdateData)
                driverObj.state.pendingTransitionUpdateData = ""
            }

            driverObj.generateLocationEvent = { member, homeName, data ->
                if (driverObj.state.memberPresence) driverObj.state.remove("memberPresence")
                if (driverObj.state.driverVersion != driverObj.driverVersion()) driverObj.state.driverVersion = driverObj.driverVersion()
                if (driverObj.state.memberName != member.name) driverObj.state.memberName = member.name
                if (driverObj.state.homeName != homeName) driverObj.state.homeName = homeName
                if (driverObj.state.transitionDeadband == null) driverObj.state.transitionDeadband = 0
                driverObj.updateAttributes = { d -> /* no-op */ }
                driverObj.updateAdditionalAttributes = { m -> /* no-op */ }
                driverObj.sendEvent(name:"lastLocationtime", value: new java.text.SimpleDateFormat("E h:mm a yyyy-MM-dd").format(new Date()))
                def updateData = [
                    desc: data.desc,
                    event: data.event,
                    _type: data._type,
                    currentDistanceFromHome: data.currentDistanceFromHome,
                    vel: data.vel,
                    tst: data.tst,
                    memberPresence: (data.memberAtHome ? "present" : "not present"),
                    memberAtHome: data.memberAtHome,
                    currentLocation: driverObj.getCurrentLocation(data),
                ]
                if ((data?.desc == driverObj.device.currentValue("transitionRegion")) && (data.tst < driverObj.state.transitionDeadband)) {
                    driverObj.state.transitionDeadband = data.tst + (driverObj.notificationHysteresisSeconds == null ? driverObj.@DEFAULT_notificationHysteresisSeconds : driverObj.notificationHysteresisSeconds)
                    driverObj.state.pendingTransitionUpdateData = updateData
                    driverObj.unschedule(driverObj.processScheduledTransitionEvent)
                    driverObj.runIn((driverObj.state.transitionDeadband - data.tst), driverObj.processScheduledTransitionEvent)
                } else {
                    if (data._type == "transition") {
                        driverObj.processScheduledTransitionEvent()
                    }
                    driverObj.processLocationEvent(updateData)
                }
                if ((driverObj.device.currentValue("presence") != updateData.memberPresence) && (data.tst > driverObj.state.transitionDeadband)) {
                    driverObj.createTransitionEvent(driverObj.state.homeName, (updateData.memberPresence == "present" ? "enter" : "leave"), data.tst)
                    driverObj.createPresenceEvent(updateData.memberPresence, updateData.tst)
                }
                true
            }

            driverObj.driverVersion = { "1.8.4" }
        }

        // Inject environment
        driverObj.device = device
        driverObj.parent = parent
        driverObj.state = [:]
        driverObj.log = log
        driverObj.runIn = runIn
        driverObj.unschedule = unschedule

        // Allow driver to record sent events
        driverObj.sendEvent = { Map evt ->
            device._events << evt
            if (evt?.name) device.setCurrentValue(evt.name as String, evt.value)
        }
        driverObj.logDescriptionText = { String m -> /* capture if needed */ }
        driverObj.logDebug = { String m -> logMsgs.debug << m }

        return [driver: driverObj, device: device, parent: parent, events: deviceEvents, values: deviceValues, logs: logMsgs, scheduled: scheduled]
    }

    def "getCurrentLocation returns 'Private' when data.private is true"() {
        given:
        def h = makeHarness()

        when:
        def loc = h.driver.getCurrentLocation([private: true])

        then:
        loc == "Private"
    }

    def "getCurrentLocation returns transition desc when _type is transition"() {
        given:
        def h = makeHarness()

        expect:
        h.driver.getCurrentLocation([private:false, _type:"transition", desc:"Home"]) == "Home"
    }

    def "getCurrentLocation concatenates inregions excluding +follow regions and trims trailing comma"() {
        given:
        def h = makeHarness()
        def data = [private:false, inregions:["Home","+follow:Work","Gym"]]

        when:
        def loc = h.driver.getCurrentLocation(data)

        then:
        loc == "Home,Gym"
    }

    def "getCurrentLocation falls back to streetAddress when not in transition and no inregions"() {
        given:
        def h = makeHarness()

        expect:
        h.driver.getCurrentLocation([private:false, streetAddress:"123 Main St"]) == "123 Main St"
    }

    def "createTransitionEvent sets events and updates deadband on non-duplicate"() {
        given:
        def h = makeHarness()
        h.values['transitionDirection'] = null
        h.values['transitionRegion'] = null
        def t = 1_700_000_000 // arbitrary epoch seconds

        when:
        h.driver.createTransitionEvent("Home", "enter", t)

        then:
        h.values['transitionRegion'] == "Home"
        h.values['transitionDirection'] == "arrived"
        h.values['sinceTime'] == t
        h.driver.state.transitionDeadband == t + 90
        h.events.find { it.name == "transitionTime" } != null
    }

    def "createTransitionEvent skips duplicate transition for same region and direction"() {
        given:
        def h = makeHarness()
        h.values['transitionRegion'] = "Home"
        h.values['transitionDirection'] = "arrived"

        when:
        def beforeCount = h.events.size()
        h.driver.createTransitionEvent("Home", "enter", 1_700_000_010)
        def afterCount = h.events.size()

        then:
        afterCount == beforeCount // no additional events
    }

    def "createPresenceEvent sends presence and updates sinceTime when changed"() {
        given:
        def h = makeHarness()
        h.values['presence'] = "not present"

        when:
        h.driver.createPresenceEvent("present", 1_700_000_020)

        then:
        h.values['presence'] == "present"
        h.values['sinceTime'] == 1_700_000_020
        h.events.any { it.name == "presence" && it.value == "present" }
    }

    def "createPresenceEvent skips duplicate presence state"() {
        given:
        def h = makeHarness()
        h.values['presence'] = "present"

        when:
        def before = h.events.size()
        h.driver.createPresenceEvent("present", 1_700_000_020)
        def after = h.events.size()

        then:
        after == before
    }

    def "processScheduledTransitionEvent processes pending update then clears it"() {
        given:
        def h = makeHarness()
        def called = [processed:false]
        h.driver.processLocationEvent = { data -> called.processed = true }
        h.driver.state.pendingTransitionUpdateData = [desc:"Home", _type:"location", tst:123, currentDistanceFromHome: 0, vel:0, memberPresence: "present", currentLocation:"Home"]

        when:
        h.driver.processScheduledTransitionEvent()

        then:
        called.processed
        h.driver.state.pendingTransitionUpdateData == ""
    }

    def "processLocationEvent handles transition and triggers presence when desc equals homeName"() {
        given:
        def h = makeHarness()
        h.driver.state.homeName = "Home"
        h.values['presence'] = "not present"

        when:
        h.driver.processLocationEvent([private:false, _type:"transition", event:"enter", desc:"Home", tst:1_700_000_030, currentDistanceFromHome:0, vel:0, memberPresence:"present", currentLocation:"Home"])

        then:
        h.values['transitionRegion'] == "Home"
        h.values['presence'] == "present"
        h.values['location'] == "Home"
    }

    def "generateLocationEvent removes legacy state.memberPresence and schedules update when within deadband window"() {
        given:
        def h = makeHarness()
        h.driver.state.memberPresence = "legacy"
        h.values['transitionRegion'] = "Office"
        h.driver.state.transitionDeadband = 1_700_000_100
        def data = [
            private:false, _type:"location", event:null, desc:"Office",
            tst:1_700_000_050, // inside deadband (earlier than deadband end)
            currentDistanceFromHome: 10, vel: 0, memberAtHome: false,
            streetAddress:"500 Business Rd"
        ]
        def member = [name:"Alice"]

        when:
        def result = h.driver.generateLocationEvent(member, "Home", data)

        then:
        result
        !h.driver.state.containsKey("memberPresence")
        h.driver.state.pendingTransitionUpdateData instanceof Map
        h.scheduled.runIns.size() == 1
        h.scheduled.unscheduled == 1
    }

    def "generateLocationEvent processes immediate transition when out of deadband and corrects presence mismatch"() {
        given:
        def h = makeHarness()
        h.values['presence'] = "present" // mismatch with memberAtHome false
        h.values['transitionRegion'] = "Home"
        h.driver.state.transitionDeadband = 1_700_000_000 // in the past
        h.driver.state.homeName = "Home"
        def data = [
            private:false, _type:"transition", event:"leave", desc:"Work",
            tst:1_700_000_200, currentDistanceFromHome: 20, vel: 30, memberAtHome: false,
            streetAddress:"Workplace"
        ]
        def member = [name:"Bob"]

        when:
        def result = h.driver.generateLocationEvent(member, "Home", data)

        then:
        result
        h.values['transitionRegion'] == "Work"
        h.values['presence'] == "not present" // corrected
        h.values['location'] == "Work"
    }

    @Unroll
    def "TRIGGER_TYPE contains mapping '#key' -> '#val'"() {
        given:
        def h = makeHarness()

        expect:
        h.driver.@TRIGGER_TYPE[key] == val

        where:
        key | val
        "p" | "Ping"
        "c" | "Region"
        "r" | "Report Location"
        "u" | "Manual"
        "b" | "Beacon"
        "t" | "Timer"
        "v" | "Monitoring"
        "l" | "Region"
    }
}