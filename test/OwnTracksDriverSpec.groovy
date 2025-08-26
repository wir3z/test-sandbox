import spock.lang.Specification
import spock.lang.Title
import spock.lang.Narrative

@Title("OwnTracksDriver unit tests")
@Narrative("""
Tests for core presence/location event handling paths. 
Testing library/framework: Spock Framework (Groovy).
""")
class OwnTracksDriverSpec extends Specification {
}

    def mkDriver(Map overrides = [:]) {
        // Create a dynamic Groovy object to simulate the driver script with required fields/methods.
        def drv = new GroovyObjectSupport() {
            def state = [:]
            def device = [
                name: "Test Device",
                displayName: "Test Device",
                currentValues: [:],
                currentValue: { String k -> currentValues[k] },
            ] as GroovyObject
            def parent = [
                displayKmMiVal: { n -> n },
                getLargeUnits: { "km" },
                getSmallUnits: { "m" },
                getVelocityUnits: { "km/h" },
                displayMFtVal: { n -> n },
                generateTransitionNotification: { member, phrase, region, timeStr -> },
            ] as GroovyObject

            // Captured events/logs for assertions
            List<Map> sentEvents = []
            List<String> infos = []
            List<String> debugs = []
            List<String> descriptions = []

            // Constants used by methods
            String DEFAULT_privateLocation = "Private"
            Map TRANSITION_DIRECTION = [enter: "arrived", leave: "departed"]
            Map TRANSITION_PHRASES   = [enter: "arrived at", leave: "left"]
            Map TRIGGER_TYPE         = [lwt:"Last Will", beacon:"Beacon"]

            Integer DEFAULT_notificationHysteresisSeconds = 60
            Boolean logLocationChanges = true
            Boolean debugLogAddresses = false
            Integer notificationHysteresisSeconds = null

            // Methods under test or called by them
            def driverVersion = { "1.0.0" }

            def log = [ info: { String s -> infos << s } ] as GroovyObject
            def logDebug = { String s -> debugs << s }
            def logDescriptionText = { String s -> descriptions << s }

            def sendEvent = { Map evt -> sentEvents << evt }

            // Scheduling hooks
            Integer scheduledDelay = null
            def unschedule = { Closure c -> scheduledDelay = null }
            def runIn = { Integer seconds, Closure c -> scheduledDelay = seconds }

            // Stubs for UI tile generators and attribute updaters
            def generateTiles = { /* noop */ }
            def generatePresenceTile = { /* noop */ }
            def generateMemberTile = { /* noop */ }
            def updateAttributes = { data -> /* noop */ }
            def updateAdditionalAttributes = { member -> /* noop */ }

            // Methods from the production code (transcribed signatures)
            def installed = {
                log.info "${device.name}: Location Tracker User Driver Installed"
                sendEvent(name:"sinceTime", value: now())
                sendEvent(name:"switch", value:"off")
                state.driverVersion = driverVersion()
                state.memberName = ""
                state.homeName = ""
                state.pendingTransitionUpdateData = ""
                state.transitionDeadband = 0
                updated()
            }

            def updated = { /* usually does preference sync; keep as stub */ }

            def arrived = {
                updatePresenceEvent("enter", "present", (now()/1000).toInteger())
            }

            def departed = {
                updatePresenceEvent("leave", "not present", (now()/1000).toInteger())
            }

            def updatePresenceEvent = { dataEvent, presenceState, dataTst ->
                createTransitionEvent(state.homeName, dataEvent, dataTst)
                createPresenceEvent(presenceState, dataTst)
                generateTiles()
            }

            def getCurrentLocation = { data ->
                def currentLocation
                if (data.private) {
                    currentLocation = DEFAULT_privateLocation
                } else {
                    if (data._type == "transition") {
                        currentLocation = data.desc
                    } else {
                        if (data.inregions) {
                            def locationList = ""
                            data.inregions.each { place ->
                                if (place[0] != "+") {
                                    locationList += "$place,"
                                }
                            }
                            currentLocation = locationList.substring(0, locationList.length() - 1)
                        } else {
                            currentLocation = data.streetAddress
                        }
                    }
                }
                return currentLocation
            }

            def createTransitionEvent = { dataRegion, dataEvent, dataTst ->
                state.transitionDeadband = dataTst + (notificationHysteresisSeconds == null ? DEFAULT_notificationHysteresisSeconds : notificationHysteresisSeconds)
                if ((TRANSITION_DIRECTION[dataEvent] != device.currentValue('transitionDirection')) || (dataRegion != device.currentValue('transitionRegion'))) {
                    def dataTime = new java.text.SimpleDateFormat("E h:mm a yyyy-MM-dd").format(new Date((long)dataTst * 1000))
                    def descriptionText = device.displayName +  " has ${TRANSITION_PHRASES[dataEvent]} " + dataRegion
                    logDescriptionText("$descriptionText")
                    sendEvent(name:"transitionRegion", value:dataRegion)
                    sendEvent(name:"transitionTime", value:dataTime)
                    sendEvent(name:"transitionDirection", value:TRANSITION_DIRECTION[dataEvent])
                    sendEvent(name:"sinceTime", value:dataTst)
                    parent.generateTransitionNotification(state.memberName, TRANSITION_PHRASES[dataEvent], dataRegion, dataTime)
                } else {
                    logDebug("Skipping duplicate transition event '${dataEvent} ${dataRegion} at ${new java.text.SimpleDateFormat("E h:mm:ss a yyyy-MM-dd").format(new Date((long)dataTst * 1000))}'.")
                }
            }

            def createPresenceEvent = { presenceState, dataTst ->
                if (device.currentValue("presence") != presenceState) {
                    def descriptionText = device.displayName + " is " + presenceState
                    sendEvent(name:"presence", value:presenceState, descriptionText:descriptionText)
                    logDescriptionText("$descriptionText")
                    updateSinceTime(dataTst)
                    generatePresenceTile()
                } else {
                    logDebug("Skipping duplicate presence event '${state.memberName} ${presenceState}' at ${new java.text.SimpleDateFormat("E h:mm:ss a yyyy-MM-dd").format(new Date((long)dataTst * 1000))}.")
                }
            }

            def updateSinceTime = { dataTst ->
                sendEvent(name:"sinceTime", value:dataTst)
                sendEvent(name:"since", value:new java.text.SimpleDateFormat("E h:mm a yyyy-MM-dd").format(new Date((long)dataTst * 1000)))
            }

            Boolean generateLocationEvent(member, homeName, data) {
                if (state.memberPresence) state.remove("memberPresence")
                if (state.driverVersion != driverVersion()) state.driverVersion = driverVersion()
                if (state.memberName != member.name) state.memberName = member.name
                if (state.homeName != homeName) state.homeName = homeName
                if (state.transitionDeadband == null) state.transitionDeadband = 0
                updateAttributes(data)
                updateAdditionalAttributes(member)

                if (data.private) {
                    logDebug("Updating '${(data.event ? "Event ${data.event}" : (data.t ? TRIGGER_TYPE[data.t] : "Location"))}' presence for ${device.displayName} -- ${(data.memberAtHome ? "'present'" : "'not present'")}, accuracy: ${parent.displayMFtVal(data.acc)} ${parent.getSmallUnits()} ${(data?.SSID ? ", SSID: ${data.SSID}" : "")}")
                } else {
                    logDebug("Updating '${(data.event ? "Event ${data.event}" : (data.t ? TRIGGER_TYPE[data.t] : "Location"))}' presence for ${device.displayName} -- ${(data.memberAtHome ? "'present'" : "'not present'")} (Home Wifi: ${data.memberWiFiHome}, High Accuracy: ${member.dynamicLocaterAccuracy}), " +
                             "${parent.displayKmMiVal(data.currentDistanceFromHome)} ${parent.getLargeUnits()} from Home, ${(data.batt != null ? "Battery: ${data.batt}%, ":"")}${(data.vel != null ? "Velocity: ${parent.displayKmMiVal(data.vel)} ${parent.getVelocityUnits()}, ":"")}" +
                             "accuracy: ${parent.displayMFtVal(data.acc)} ${parent.getSmallUnits() }" +
                             (debugLogAddresses ? ", Location: [${data.lat},${data.lon}] ${(data?.address ? ", Address: [${data.address}]" : "")} ${(data?.streetAddress ? ", Street Address: [${data.streetAddress}]" : "")} " : "") +
                             "${(data?.inregions ? ", Regions: ${data.inregions}" : "")} ${(data?.SSID ? ", SSID: ${data.SSID}" : "")} " )
                }

                sendEvent(name:"lastLocationtime", value:new java.text.SimpleDateFormat("E h:mm a yyyy-MM-dd").format(new Date()))

                def updateData = [
                    desc: data.desc,
                    event: data.event,
                    _type: data._type,
                    currentDistanceFromHome: data.currentDistanceFromHome,
                    vel: data.vel,
                    tst: data.tst,
                    memberPresence: (data.memberAtHome ? "present" : "not present"),
                    memberAtHome: data.memberAtHome,
                    currentLocation: getCurrentLocation(data),
                ]

                if ((data?.desc == device.currentValue("transitionRegion")) && (data.tst < state.transitionDeadband)) {
                    state.transitionDeadband = data.tst + (notificationHysteresisSeconds == null ? DEFAULT_notificationHysteresisSeconds : notificationHysteresisSeconds)
                    state.pendingTransitionUpdateData = updateData
                    unschedule(processScheduledTransitionEvent)
                    runIn((state.transitionDeadband - data.tst), processScheduledTransitionEvent)
                } else {
                    if (data._type == "transition") {
                        processScheduledTransitionEvent()
                    }
                    processLocationEvent(updateData)
                }

                if ((device.currentValue("presence") != updateData.memberPresence) && (data.tst > state.transitionDeadband)) {
                    createTransitionEvent(state.homeName, (updateData.memberPresence == "present" ? "enter" : "leave"), data.tst)
                    createPresenceEvent(updateData.memberPresence, updateData.tst)
                    logDescriptionText("Correcting presence of '${state.memberName}' to '${updateData.memberPresence}'.")
                }

                return true
            }

            void processScheduledTransitionEvent() {
                unschedule(processScheduledTransitionEvent)
                if (state.pendingTransitionUpdateData) processLocationEvent(state.pendingTransitionUpdateData)
                state.pendingTransitionUpdateData = ""
            }

            void processLocationEvent(data) {
                if (!data.private) {
                    if (data._type == "transition") {
                        createTransitionEvent(data.desc, data.event, data.tst)
                    } else {
                        if (device.currentValue("location") != data.currentLocation) {
                            if (logLocationChanges) log.info "${device.displayName} is at $data.currentLocation"
                            updateSinceTime(data.tst)
                        }
                    }
                    sendEvent(name:"distanceFromHome", value: parent.displayKmMiVal(data.currentDistanceFromHome))
                    sendEvent(name:"lastSpeed", value: (data?.vel ? parent.displayKmMiVal(data.vel).toInteger() : 0))
                }
                if (state.homeName == data.desc) {
                    createPresenceEvent(data.memberPresence, data.tst)
                }
                sendEvent(name:"location", value:data.currentLocation)
                generateMemberTile()
            }

            // Helpers to make time testable
            def now = { System.currentTimeMillis() }
        }

        // apply any overrides (e.g., constants or hooks)
        overrides.each { k, v -> drv."$k" = v }
        return drv
    }

    def "installed sets defaults, emits sinceTime and switch off, and calls updated"() {
        given:
        def calledUpdated = false
        def drv = mkDriver(updated: { calledUpdated = true })

        when:
        drv.installed()

        then: "sendEvent called for sinceTime and switch"
        drv.sentEvents.find { it.name == "sinceTime" && it.value instanceof Long }
        drv.sentEvents.find { it.name == "switch" && it.value == "off" }
        and: "state defaults set"
        drv.state.driverVersion == "1.0.0"
        drv.state.memberName == ""
        drv.state.homeName == ""
        drv.state.pendingTransitionUpdateData == ""
        drv.state.transitionDeadband == 0
        and:
        calledUpdated
    }

    def "arrived invokes updatePresenceEvent with enter/present and current timestamp seconds"() {
        given:
        def capturedArgs
        def nowMs = 1_700_000_000_000L
        def drv = mkDriver(now: { nowMs }, updatePresenceEvent: { a, b, c -> capturedArgs = [a,b,c] })

        when:
        drv.arrived()

        then:
        capturedArgs[0] == "enter"
        capturedArgs[1] == "present"
        capturedArgs[2] == (nowMs/1000).toInteger()
    }

    def "departed invokes updatePresenceEvent with leave/not present and current timestamp seconds"() {
        given:
        def capturedArgs
        def nowMs = 1_700_000_100_000L
        def drv = mkDriver(now: { nowMs }, updatePresenceEvent: { a, b, c -> capturedArgs = [a,b,c] })

        when:
        drv.departed()

        then:
        capturedArgs[0] == "leave"
        capturedArgs[1] == "not present"
        capturedArgs[2] == (nowMs/1000).toInteger()
    }

    def "updatePresenceEvent delegates to createTransitionEvent, createPresenceEvent and generateTiles"() {
        given:
        def calls = []
        def drv = mkDriver(
            state: [homeName: "Home"],
            createTransitionEvent: { region, ev, tst -> calls << ["cte", region, ev, tst] },
            createPresenceEvent: { pres, tst -> calls << ["cpe", pres, tst] },
            generateTiles: { calls << ["tiles"] }
        )

        when:
        drv.updatePresenceEvent("enter", "present", 1234)

        then:
        calls == [["cte","Home","enter",1234], ["cpe","present",1234], ["tiles"]]
    }

    def "getCurrentLocation returns DEFAULT_privateLocation when data.private is true"() {
        given:
        def drv = mkDriver()

        expect:
        drv.getCurrentLocation([private:true]) == "Private"
    }

    def "getCurrentLocation returns desc on transition event"() {
        given:
        def drv = mkDriver()

        expect:
        drv.getCurrentLocation([private:false, _type:"transition", desc:"Office"]) == "Office"
    }

    def "getCurrentLocation joins inregions excluding +prefix and trims trailing comma"() {
        given:
        def drv = mkDriver()

        expect:
        drv.getCurrentLocation([private:false, inregions:["Home","+follow-abc","Yard"]]) == "Home,Yard"
    }

    def "getCurrentLocation uses streetAddress if no regions"() {
        given:
        def drv = mkDriver()

        expect:
        drv.getCurrentLocation([private:false, streetAddress:"123 Main St"]) == "123 Main St"
    }

    def "createTransitionEvent sets deadband, emits events, not duplicates"() {
        given:
        def drv = mkDriver()
        drv.state.memberName = "Alice"
        drv.device.currentValues.transitionDirection = "departed"
        drv.device.currentValues.transitionRegion = "Away"

        when:
        drv.createTransitionEvent("Home", "enter", 1_600_000_000)

        then:
        drv.state.transitionDeadband == 1_600_000_000 + drv.DEFAULT_notificationHysteresisSeconds
        drv.sentEvents.find { it.name == "transitionRegion" && it.value == "Home" }
        drv.sentEvents.find { it.name == "transitionTime" && it.value instanceof String }
        drv.sentEvents.find { it.name == "transitionDirection" && it.value == "arrived" }
        drv.sentEvents.find { it.name == "sinceTime" && it.value == 1_600_000_000 }

        when: "call again with same direction and region -> duplicate skipped"
        def beforeCount = drv.sentEvents.size()
        drv.device.currentValues.transitionDirection = "arrived"
        drv.device.currentValues.transitionRegion = "Home"
        drv.createTransitionEvent("Home", "enter", 1_600_000_001)

        then:
        drv.sentEvents.size() == beforeCount
        drv.debugs.any { it.contains("Skipping duplicate transition event") }
    }

    def "createPresenceEvent emits presence when changed and updates sinceTime; otherwise logs duplicate"() {
        given:
        def drv = mkDriver()
        drv.device.currentValues.presence = "not present"

        when:
        drv.createPresenceEvent("present", 1_600_000_000)

        then:
        drv.sentEvents.find { it.name == "presence" && it.value == "present" }
        drv.sentEvents.find { it.name == "sinceTime" && it.value == 1_600_000_000 }
        drv.sentEvents.find { it.name == "since" && it.value == new java.text.SimpleDateFormat("E h:mm a yyyy-MM-dd").format(new Date((long)1_600_000_000 * 1000)) }

        when: "duplicate"
        drv.device.currentValues.presence = "present"
        def before = drv.sentEvents.size()
        drv.createPresenceEvent("present", 1_600_000_100)

        then:
        drv.sentEvents.size() == before
        drv.debugs.any { it.contains("Skipping duplicate presence event") }
    }

    def "updateSinceTime sets sinceTime numeric and since string formatted"() {
        given:
        def ts = 1_600_000_000
        def drv = mkDriver()

        when:
        drv.updateSinceTime(ts)

        then:
        drv.sentEvents.find { it.name == "sinceTime" && it.value == ts }
        drv.sentEvents.find { it.name == "since" && it.value == new java.text.SimpleDateFormat("E h:mm a yyyy-MM-dd").format(new Date((long)ts * 1000)) }
    }

    def "generateLocationEvent schedules when in hysteresis deadband and same region"() {
        given:
        def drv = mkDriver()
        drv.device.currentValues.transitionRegion = "Home"
        drv.state.transitionDeadband = 1_700_000_100
        def data = [
            private:false, _type:"transition", desc:"Home", event:"enter",
            currentDistanceFromHome: 0.2, vel: 0, tst: 1_700_000_000,
            memberAtHome:true, streetAddress:"Home Street"
        ]
        def member = [ name:"Alice", dynamicLocaterAccuracy: true ]

        when:
        def result = drv.generateLocationEvent(member, "Home", data)

        then:
        result
        drv.state.pendingTransitionUpdateData instanceof Map
        drv.scheduledDelay == drv.state.transitionDeadband - data.tst
    }

    def "generateLocationEvent processes immediately for transition when not in deadband and corrects presence when mismatched"() {
        given:
        def calls = []
        def drv = mkDriver(
            processScheduledTransitionEvent: { calls << "processedPending" },
            processLocationEvent: { d -> calls << "processedNow" },
            createTransitionEvent: { region, ev, tst -> calls << "correctTransition" },
            createPresenceEvent: { pres, tst -> calls << "correctPresence" }
        )
        drv.device.currentValues.transitionRegion = "Work"
        drv.device.currentValues.presence = "not present"
        drv.state.transitionDeadband = 1_700_000_000
        def data = [
            private:false, _type:"transition", desc:"Office", event:"leave",
            currentDistanceFromHome: 12.3, vel: 5, tst: 1_800_000_010,
            memberAtHome:false, streetAddress:"123 Office Rd"
        ]
        def member = [ name:"Bob", dynamicLocaterAccuracy: false ]

        when:
        drv.generateLocationEvent(member, "Home", data)

        then:
        calls.containsAll(["processedPending","processedNow","correctTransition","correctPresence"])
    }

    def "processScheduledTransitionEvent unschedules, processes pending and clears it"() {
        given:
        def processed = false
        def drv = mkDriver(processLocationEvent: { m -> processed = (m == [foo:"bar"]) })
        drv.state.pendingTransitionUpdateData = [foo:"bar"]

        when:
        drv.processScheduledTransitionEvent()

        then:
        processed
        drv.state.pendingTransitionUpdateData == ""
    }

    def "processLocationEvent handles transition and non-transition updates correctly"() {
        given:
        def transCalls = []
        def drv = mkDriver(createTransitionEvent: { region, ev, tst -> transCalls << [region, ev, tst] })
        drv.state.homeName = "Home"

        when: "transition event calls createTransitionEvent and presence updated if desc equals home"
        drv.processLocationEvent([private:false, _type:"transition", desc:"Home", event:"enter", tst:111, currentDistanceFromHome:1.2, vel:3, memberPresence:"present", currentLocation:"Home"])

        then:
        transCalls == [["Home","enter",111]]
        drv.sentEvents.find { it.name == "location" && it.value == "Home" }
        drv.sentEvents.find { it.name == "lastSpeed" && it.value == 3 }
        drv.sentEvents.find { it.name == "distanceFromHome" && it.value == 1.2 }
        drv.sentEvents.find { it.name == "presence" && it.value == "present" }

        when: "non-transition with location change updates sinceTime"
        drv.device.currentValues.location = "Old Place"
        def beforeSince = drv.sentEvents.count { it.name == "sinceTime" }
        drv.processLocationEvent([private:false, _type:"location", desc:null, event:null, tst:222, currentDistanceFromHome:0.4, vel:null, memberPresence:"present", currentLocation:"New Place"])

        then:
        drv.sentEvents.count { it.name == "sinceTime" } == beforeSince + 1
        drv.sentEvents.find { it.name == "lastSpeed" && it.value == 0 }
        drv.sentEvents.find { it.name == "location" && it.value == "New Place" }
    }

    def "getCurrentLocation throws when all inregions are filtered out (documenting current behavior)"() {
        given:
        def drv = mkDriver()

        when:
        drv.getCurrentLocation([private:false, inregions:["+tmp","+follow"]])

        then:
        thrown(StringIndexOutOfBoundsException)
    }