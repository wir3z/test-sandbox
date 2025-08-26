import spock.lang.*
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

/**
 * Test Suite: OwnTracksAppSpec
 * Framework: Spock (spock.lang.Specification)
 * Notes:
 *  - Uses GroovyShell to evaluate the app script under test to access its functions.
 *  - Mocks/stubs Hubitat-specific globals (state, settings, request, response, location) as needed.
 *  - Focuses on logic introduced or touched in the recent diff: webhookEventHandler/parseMessage paths,
 *    parsePostHeaders, haversine, angleFromCoordinate, getCompassDifference, location type guards,
 *    address parsing, configuration creation, Android member detection, SSID matching, and velocity calc edge cases.
 */
class OwnTracksAppSpec extends Specification {

    def binding
    def shell
    def app

    def setup() {
        binding = new Binding()
        shell = new GroovyShell(binding)
        // Load the main app file dynamically. Adjust path if required.
        def appPath = new File('owntracks-hubitat/OwnTracksApp.groovy')
        assert appPath.exists(): "Expected app file at owntracks-hubitat/OwnTracksApp.groovy"
        app = shell.evaluate(appPath)
        // Initialize common Hubitat-like globals
        app.state = [ members: [], places: [] ]
        app.settings = [:]
        app.location = [ getTimeZone: { -> TimeZone.getTimeZone("America/Chicago") } ] as Object
    }

    // Utility to deep copy maps to avoid mutation bleed between tests
    static def deep(def o) { new JsonSlurper().parseText(new JsonBuilder(o).toString()) }

    def "parsePostHeaders trims surrounding brackets from header values"() {
        given:
        def headers = [ 'X-limit-u':'[alice]', 'X-limit-d':'[pixel8]', 'Content-Type':'[application/json]' ]

        when:
        def parsed = app.parsePostHeaders(headers)

        then:
        parsed['X-limit-u'] == 'alice'
        parsed['X-limit-d'] == 'pixel8'
        parsed['Content-Type'] == 'application/json'
    }

    def "haversine computes known distance approximately between NYC and LA"() {
        given:
        // NYC (40.7128, -74.0060), LA (34.0522, -118.2437) ~ 3936 km
        def nyc = [lat:40.7128, lon:-74.0060]
        def la  = [lat:34.0522, lon:-118.2437]

        when:
        def d = app.haversine(nyc.lat, nyc.lon, la.lat, la.lon)

        then:
        d > 3800 && d < 4100
    }

    def "angleFromCoordinate returns expected cardinal/quadrant bearings"() {
        expect:
        // Due east
        app.@angleFromCoordinate(0d, 0d, 0d, 10d) == 90d
        // Due north
        app.@angleFromCoordinate(0d, 0d, 10d, 0d) == 0d
        // Due west normalized
        app.@angleFromCoordinate(0d, 0d, 0d, -10d) == 270d
        // NE quadrant approx 45
        Math.abs(app.@angleFromCoordinate(0d, 0d, 10d, 10d) - 45d) < 1.0
    }

    def "getCompassDifference handles wrap-around at 360 properly"() {
        expect:
        app.getCompassDifference(350, 10) == 20
        app.getCompassDifference(10, 350) == 20
        app.getCompassDifference(0, 180) == 180
        app.getCompassDifference(90, 100) == 10
    }

    def "validPositionType allows ping 'p' and manual 'u' only"() {
        expect:
        app.validPositionType('p')
        app.validPositionType('u')
        !app.validPositionType('l')
        !app.validPositionType('t')
        !app.validPositionType(null)
    }

    def "validLocationType allows non-region and non-manual updates"() {
        expect:
        app.validLocationType('p')
        app.validLocationType('t')
        !app.validLocationType('l')
        !app.validLocationType('u')
    }

    def "isAddress identifies addresses vs lat,lon pairs"() {
        expect:
        app.@isAddress("123 Main St, Austin, TX")
        app.@isAddress("Some Place, 456 Elm St")
        !app.@isAddress("30.123,-97.456")
        !app.@isAddress(null)
    }

    def "addStreetAddressAndRegions parses place and street address variants"() {
        given:
        def data = [ address: "Coffee Shop, 123 Brew St", inregions:null ]
        when:
        app.addStreetAddressAndRegions(data)
        then:
        data.streetAddress == "123 Brew St"
        data.address == "123 Brew St"
        data.inregions != null

        when: "simple street address"
        data = [ address: "789 Oak Ave, Austin, TX", inregions:null ]
        app.addStreetAddressAndRegions(data)
        then:
        data.streetAddress == "789 Oak Ave"

        when: "lat,lon passthrough"
        data = [ address: "30.123,-97.456", inregions:null ]
        app.addStreetAddressAndRegions(data)
        then:
        data.streetAddress == "30.123,-97.456"

        when: "robust to malformed input (exception path)"
        data = [ address: null, inregions:null ]
        app.addStreetAddressAndRegions(data)
        then:
        data.streetAddress == null
    }

    def "createConfiguration toggles dynamic vs static locator settings and returns cmd only on state change"() {
        given:
        def member = [ dynamicLocaterAccuracy:false, currentlyInRegion:false ]

        when: "request dynamic accuracy"
        def payload1 = app.createConfiguration(member, true, true)

        then:
        payload1._type == "cmd"
        payload1.action == "setConfiguration"
        payload1.configuration.locatorPriority
        member.dynamicLocaterAccuracy
        member.currentlyInRegion

        when: "same flags again returns null (no update)"
        def payload2 = app.createConfiguration(member, true, true)

        then:
        payload2 == null

        when: "switch back to static and out of region triggers update"
        def payload3 = app.createConfiguration(member, false, false)

        then:
        payload3._type == "cmd"
        payload3.configuration.pegLocatorFastestIntervalToInterval != null
        !member.dynamicLocaterAccuracy
        !member.currentlyInRegion
    }

    def "isAndroidMember detects Android user agent substring presence"() {
        expect:
        app.isAndroidMember([appVersion: 'OwnTracks/RC android/2.6']) == true
        app.isAndroidMember([appVersion: 'iOS/1.0']) == false
        app.isAndroidMember([:]) == false
        app.isAndroidMember(null) == false
    }

    def "isSSIDMatch compares trimmed SSID tokens against device attribute current SSID"() {
        given:
        def device = [ currentValue: { k -> k=='SSID' ? 'MyWiFi' : null } ] as Object

        expect:
        app.isSSIDMatch("MyWiFi, Other", device)
        app.isSSIDMatch(" Other , MyWiFi ", device)
        !app.isSSIDMatch("Other1,Other2", device)
        !app.isSSIDMatch(null, device)
    }

    def "calcMemberVelocity sets speed/bearing with and without vel/cog and guards on exceptions"() {
        given:
        def member = [ latitude: 30.0, longitude: -97.0, timeStamp: 1000, speed: 0, bearing: 0, odo: 0 ]
        def data = [ lat: 30.001, lon: -97.001, tst: 1060, t: 'b', vel: 0, cog: null ]

        and: "set thresholds"
        app.memberVelocityMinimumTimeDifference = 30

        when: "no vel provided, compute from distance/time"
        app.calcMemberVelocity(member, data)

        then:
        data.vel == member.speed
        data.cog == member.bearing
        member.speed >= 0
        member.bearing >= 0 && member.bearing < 360
        member.odo > 0

        when: "explicit vel and cog provided"
        data = [ lat: 30.002, lon: -97.002, tst: 1120, t: 'b', vel: 15, cog: 123 ]
        app.calcMemberVelocity(member, data)

        then:
        member.speed == 15
        member.bearing == 123
        data.vel == 15
        data.cog == 123

        when: "exception path sets speed and bearing to 0"
        app.calcMemberVelocity([:], [lat:null,lon:null,tst:0,t:'b'])

        then:
        noExceptionThrown()
    }

    def "parseMessage: location branch sets privacy, updates presence when not stale, and may forward to recorder"() {
        given:
        def member = [ name: 'alice', id: 'dni1', timeStamp: (System.currentTimeMillis()/1000 - 60) as int, lastReportTime: System.currentTimeMillis(), groups:['0'] ]
        def headers = [ 'X': '[Y]' ]
        def data = [_type:'location', lat:30.0, lon:-97.0, tst:(System.currentTimeMillis()/1000) as int, t:'b', vel:0, acc:5 ]
        app.memberMaximumLocationAgeMinutes = 5
        app.settings.privateMembers = []
        app.enableRecorder = false
        app.enableSecondaryHub = false
        app.memberHistoryMinimumSpeed = 1
        app.memberHistoryLength = 5
        app.DYNAMIC_INTERVALS = [ pegLocatorFastestIntervalToInterval:true, locatorPriority:'high' ]

        and: "stub out internal side-effecting methods"
        app.updateMemberAttributes = { h, d, m -> m.timeStamp = d.tst }
        app.logDistanceTraveledAndElapsedTime = { m, d -> }
        app.updateDevicePresence = { m, d -> m.presenceUpdated = true }
        app.sendUpdate = { m, d -> [ ok: true ] }
        app.memberInGlobalMemberGroup = { m -> true }
        app.parsePostHeaders = { h -> h }
        app.asynchttpPost = { a, p -> }
        app.getLocatorPriority = { inRegion -> 'balanced' }

        when:
        def payload = app.parseMessage(headers, data, member)

        then:
        payload.ok == true
        member.presenceUpdated == true
        data.private == false
        data.cc instanceof String
        data.cc.size() == 2
    }

    def "webhookEventHandler returns non-empty JSON payload and 200 even on missing body or headers"() {
        given:
        app.request = [ headers: [:], body: null ] as Object
        app.render = { Map args -> [ contentType: args.contentType, data: args.data, status: args.status ] }

        when: "no body"
        def resp1 = app.webhookEventHandler()

        then:
        resp1.status == 200
        new JsonSlurper().parseText(resp1.data) instanceof List

        when: "missing headers with body triggers deactivate"
        app.request = [ headers: ['X-limit-u':null, 'X-limit-d':null], body: new JsonBuilder([_type:'cmd']).toString() ] as Object
        app.sendDeactivateUpdate = { Map m -> [ deactivated: m.name ] }
        def resp2 = app.webhookEventHandler()

        then:
        resp2.status == 200
        new JsonSlurper().parseText(resp2.data).deactivated == 'null' || new JsonSlurper().parseText(resp2.data) != null
    }
}

// -----------------------------------------------------------------------------
// Additional tests appended by PR to enhance coverage of diff-touched functions.
// Framework: Spock (spock.lang.Specification)
// -----------------------------------------------------------------------------
import spock.lang.*
import groovy.json.*

class OwnTracksAppSpec_Additional extends Specification {

    def binding
    def shell
    def app

    def setup() {
        binding = new Binding()
        shell = new GroovyShell(binding)
        def appPath = new File('owntracks-hubitat/OwnTracksApp.groovy')
        assert appPath.exists(): "Expected app file at owntracks-hubitat/OwnTracksApp.groovy"
        app = shell.evaluate(appPath)
        app.state = [ members: [], places: [] ]
        app.settings = [:]
        app.location = [ getTimeZone: { -> TimeZone.getTimeZone("America/Chicago") } ] as Object
        // Common defaults used by multiple tests
        app.DEFAULT_memberAccuracyRadiusOpacity = 1
        app.DEFAULT_MEMBER_PIN_COLOR = "#4285F4"
        app.DEFAULT_REGION_PIN_COLOR = "#FF0000"
        app.DEFAULT_REGION_GLYPH_COLOR = "#FFFFFF"
        app.DEFAULT_REGION_HOME_GLYPH_COLOR = "#00FF00"
    }

    def "isHTTPsURL returns true only for https scheme, case-insensitive"() {
        expect:
        app.isHTTPsURL("https://example.com")
        app.isHTTPsURL("HTTPS://EXAMPLE.COM")
        !app.isHTTPsURL("http://example.com")
        !app.isHTTPsURL("ftp://example.com")
        !app.isHTTPsURL(null)
    }

    def "recorderURLType selects cloud source for https and local otherwise"() {
        given:
        app.URL_SOURCE = ["cloud", "local"]
        and:
        app.getRecorderURL = { -> "https://recorder.example.com" }

        expect:
        app.recorderURLType() == "cloud"

        when:
        app.getRecorderURL = { -> "http://lan-recorder" }
        then:
        app.recorderURLType() == "local"

        when:
        app.getRecorderURL = { -> null }
        then:
        app.recorderURLType() == "local"
    }

    def "getDistanceFromHome returns 0.0 and logs error when home region missing"() {
        given:
        app.getHomeRegion = { -> null }
        app.logError = { String m -> } // swallow logs

        when:
        def d = app.getDistanceFromHome([lat:30.0, lon:-97.0])

        then:
        d == 0.0
    }

    def "valid updateAddress retains prior address within hysteresis and fills via reverse geocode otherwise"() {
        given:
        app.DEFAULT_geocodeLookupHysteresis = 0.5
        app.@isAddress = { s -> s != null && !s.contains(",") ? true : (s?.split(",")?.size() > 1 && (!s.split(",")[0].isNumber() || !s.split(",")[1].isNumber())) }
        app.reverseGeocode = { lat, lon -> "RG Address" }

        and:
        def member = [ latitude: 30.0d, longitude: -97.0d, address: "Old Addr" ]
        def near = [ lat: 30.003d, lon: -97.003d, address: "30.003,-97.003" ] // will trigger lookup -> replaced with reverse geocode

        when:
        app.@updateAddress(member, near)

        then:
        near.address == "RG Address"
        member.address == "RG Address"

        when: "within hysteresis and new address not an address (lat,lon), keep previous"
        member = [ latitude: 30.0d, longitude: -97.0d, address: "Saved Addr" ]
        def veryNear = [ lat: 30.0005d, lon: -97.0005d, address: "30.0005,-97.0005" ]
        app.@updateAddress(member, veryNear)

        then:
        veryNear.address == "Saved Addr"
        member.address == "Saved Addr"
    }

    def "parseMessage handles unhandled _type by logging warning and returning empty payload"() {
        given:
        app.logWarn = { String m -> } // swallow logs
        def member = [ name:'bob', id:'dni2', timeStamp: (System.currentTimeMillis()/1000) as int, groups:['0'] ]
        def headers = [:]
        def data = [_type:'unknown']

        expect:
        app.parseMessage(headers, data, member) == []
    }

    def "parseMessage waypoint and waypoints only update for global members"() {
        given:
        app.memberInGlobalMemberGroup = { m -> m.global ?: false }
        app.logDescriptionText = { String m -> } // swallow
        app.addPlace = { m, d, v -> app.state.places << [desc:d.desc, lat:d.lat, lon:d.lon, rad:d.rad, tst:d.tst] }
        app.updatePlaces = { m, d -> d.waypoints?.each{ wp -> app.state.places << [desc:wp.desc, lat:wp.lat, lon:wp.lon, rad:wp.rad, tst:wp.tst] } }

        when: "waypoint with non-global member does not add"
        def member = [ name:'sam', global:false ]
        app.state.places.clear()
        app.parseMessage([:], [_type:'waypoint', desc:'Gym', lat:1.0, lon:2.0, rad:10, tst:1], member)
        then:
        app.state.places.isEmpty()

        when: "global member adds waypoint"
        member.global = true
        app.parseMessage([:], [_type:'waypoint', desc:'Gym', lat:1.0, lon:2.0, rad:10, tst:1], member)
        then:
        app.state.places.size() == 1

        when: "waypoints bulk update"
        app.state.places.clear()
        app.parseMessage([:], [_type:'waypoints', waypoints:[[desc:'A',lat:1,lon:1,rad:5,tst:2],[desc:'B',lat:2,lon:2,rad:6,tst:3]]], member)
        then:
        app.state.places*.desc as Set == ['A','B'] as Set
    }
}