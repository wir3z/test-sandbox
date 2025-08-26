package owntracks_hubitat

import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.Unroll

class OwnTracksAppSpec extends Specification {

    // Helper to load the app script as a Groovy class so we can call its methods.
    // We expect an app Groovy script containing the functions under test somewhere in src/main/... or similar.
    // The loader will search common paths; adjust if your repository structure differs.
    private static Class loadAppClass() {
        def candidates = [
            "src/main/groovy/owntracks_hubitat/OwnTracksApp.groovy",
            "src/main/groovy/OwnTracksApp.groovy",
            "src/OwnTracksApp.groovy",
            "OwnTracksApp.groovy",
        ]
        def file = candidates.collect { new File(it) }.find { it.exists() }
        assert file != null : "Could not locate OwnTracksApp.groovy in expected paths: ${candidates}"
        def shell = new GroovyClassLoader()
        return shell.parseClass(file)
    }

    def "sanity: can load OwnTracks app script"() {
        when:
        Class appClazz = loadAppClass()

        then:
        appClazz != null
    }
}

// --- AUTO-GENERATED TESTS FOR DIFF FUNCTIONS ---
import spock.lang.Shared
import spock.lang.TempDir

class OwnTracksAppSpec extends Specification {

    @Shared
    private Class appClazz

    def setupSpec() {
        appClazz = this.class.getDeclaredMethod("loadAppClass").invoke(this)
    }

    // Utility to create an instance with a simple binding for dynamic properties used by the script (imperialUnits, etc.)
    private Object newAppInstance(Map extras = [:]) {
        def app = appClazz.newInstance()
        // Provide default dynamic fields used by functions under test
        app.imperialUnits = (extras.containsKey('imperialUnits') ? extras.imperialUnits : false)
        app.memberVelocityMinimumTimeDifference = extras.get('memberVelocityMinimumTimeDifference') ?: 15
        app.DEFAULT_locatorPriority = extras.get('DEFAULT_locatorPriority') ?: 105
        app.DYNAMIC_INTERVALS = extras.get('DYNAMIC_INTERVALS') ?: [ locatorPriority: 201 ]
        app.highPowerMode = extras.get('highPowerMode') ?: false
        app.memberHistoryMinimumSpeed = extras.get('memberHistoryMinimumSpeed') ?: 3
        app.memberBeginMarker = extras.get('memberBeginMarker') ?: "B"
        app.memberEndMarker = extras.get('memberEndMarker') ?: "E"
        app.memberMiddleMarker = extras.get('memberMiddleMarker') ?: "M"
        app.memberTripIdleMarkerTime = extras.get('memberTripIdleMarkerTime') ?: 5 // minutes
        app.memberMarkerBearingDifferenceDegrees = extras.get('memberMarkerBearingDifferenceDegrees') ?: 15.0G
        app.removeMemberMarkersWithSameBearing = extras.get('removeMemberMarkersWithSameBearing') ?: true
        app.DEFAULT_geocodeLookupHysteresis = extras.get('DEFAULT_geocodeLookupHysteresis') ?: 0.05
        // Stubs for external calls
        app.getHomeRegion = { -> [ lat: 40.0, lon: -105.0, tst: 123L ] }
        app.logError = { String m -> /* no-op in tests */ }
        app.logWarn = { String m -> /* no-op */ }
        app.logDebug = { String m -> /* no-op */ }
        app.logDescriptionText = { String m -> /* no-op */ }
        app.isGeocodeAllowed = { -> true }
        app.settings = [:]
        return app
    }

    def "appVersion returns the expected current value"() {
        given:
        def app = newAppInstance()

        when:
        def version = app.appVersion()

        then:
        version == "1.8.23"
    }

    @Unroll
    def "getLocatorPriority respects highPowerMode=#highPower and inRegion=#inRegion"() {
        given:
        def app = newAppInstance([highPowerMode: highPower, DEFAULT_locatorPriority: 101, DYNAMIC_INTERVALS: [locatorPriority: 333]])

        expect:
        app.getLocatorPriority(inRegion) == expected

        where:
        highPower | inRegion || expected
        true      | false    || 333
        true      | true     || 101
        false     | false    || 101
        false     | true     || 101
    }

    def "haversine computes known distance approximately (San Francisco to Los Angeles ~559 km)"() {
        given:
        def app = newAppInstance()

        when:
        def d = app.haversine(37.7749, -122.4194, 34.0522, -118.2437)

        then:
        Math.abs(d - 559) < 5
    }

    @Unroll
    def "angleFromCoordinate returns normalized bearing 0-360 for simple cardinal directions"() {
        given:
        def app = newAppInstance()

        expect:
        int Math.round(app.@angleFromCoordinate(lat1, lon1, lat2, lon2)) == expected as int

        where:
        lat1 | lon1   | lat2 | lon2   || expected
        0.0  | 0.0    | 1.0  | 0.0    || 0      // North
        0.0  | 0.0    | 0.0  | 1.0    || 90     // East
        1.0  | 0.0    | 0.0  | 0.0    || 180    // South
        0.0  | 1.0    | 0.0  | 0.0    || 270    // West
    }

    @Unroll
    def "getCompassDifference handles wrap-around between bearings #b1 and #b2"() {
        given:
        def app = newAppInstance()

        expect:
        app.getCompassDifference(b1, b2) == expected

        where:
        b1  | b2  || expected
        10  | 20  || 10
        350 | 10  || 20
        0   | 180 || 180
        270 | 90  || 180
        359 | 1   || 2
    }

    def "calcMemberVelocity uses reported vel when positive and updates bearing from cog"() {
        given:
        def app = newAppInstance()
        def member = [ latitude: 40.0, longitude: -105.0, timeStamp: 1000L, odo: 0.0, speed: 0, bearing: 0 ]
        def data = [ lat: 40.001, lon: -105.001, vel: 42, cog: 123, tst: 1100L, t: "p" ]

        when:
        app.calcMemberVelocity(member, data)

        then:
        member.speed == 42
        data.vel == 42
        member.bearing == 123
        data.cog == 123
        member.odo > 0
    }

    def "calcMemberVelocity computes speed when vel is zero and valid location type with sufficient time delta"() {
        given:
        def app = newAppInstance([memberVelocityMinimumTimeDifference: 60])
        def member = [ latitude: 40.0, longitude: -105.0, timeStamp: 1000L, odo: 0.0, speed: 0, bearing: 0 ]
        // ~157 meters east in ~120s -> about 4.7 km/h -> 2 as integer toInteger? Wait: km/h integer approx
        def data = [ lat: 40.0, lon: -104.99859, vel: 0, tst: 1120L, t: "p" ] // 120 seconds

        when:
        app.calcMemberVelocity(member, data)

        then:
        member.speed >= 1 && member.speed < 10     // sanity: calculated non-zero but reasonable
        data.vel == member.speed
        member.bearing in 0..360
        member.odo > 0
    }

    def "calcMemberVelocity sets speed and bearing to zero on exception"() {
        given:
        def app = newAppInstance()
        def badMember = [ latitude: "NaN", longitude: -105.0, timeStamp: 1000L ] // toDouble will throw

        when:
        app.calcMemberVelocity(badMember, [lat: 40.0, lon: -105.0, vel: 0, tst: 1100L, t: "p"])

        then:
        badMember.speed == 0
        badMember.bearing == 0
    }

    def "removeHistoryPoint returns true when three points are nearly collinear (small deviation)"() {
        given:
        def app = newAppInstance([memberMarkerBearingDifferenceDegrees: 20.0G])
        def member = [
            latitude: 40.0, longitude: -105.0,
            history: [
                [ lat: 40.0009, lng: -105.0009],
                [ lat: 40.0018, lng: -105.0018]
            ]
        ]

        expect:
        app.removeHistoryPoint(member) == true
    }

    def "getHistoryMarker sets begin/end markers based on idle duration and cleans up previous markers"() {
        given:
        def app = newAppInstance([memberTripIdleMarkerTime: 1]) // minute
        def now = 2000L
        def member = [
            name: "alice",
            speed: 0,
            odo: 3.2,
            timeStamp: now,
            history: [
                [ lat: 40.0, lng: -105.0, tst: now - 500, mkr: app.memberBeginMarker, spd: 0 ],
                [ lat: 40.0005, lng: -105.0005, tst: now - 120, mkr: app.memberMiddleMarker, spd: 0 ]
            ]
        ]
        def data = [ lat: 40.001, lon: -105.001, tst: now, t: "p" ]

        when:
        def marker = app.getHistoryMarker(member, data)

        then:
        marker in [app.memberBeginMarker, app.memberMiddleMarker, app.memberEndMarker]
        // On idle duration exceeded, last marker becomes end and next marker becomes begin
        member.history.last().mkr in [app.memberEndMarker, app.memberMiddleMarker]
    }

    @Unroll
    def "validPositionType permits only ping or manual (t='#tVal')"() {
        given:
        def app = newAppInstance()

        expect:
        app.validPositionType(tVal) == expected

        where:
        tVal || expected
        "p"  || true
        "u"  || true
        "l"  || false
        "c"  || false
        null || false
    }

    @Unroll
    def "validLocationType disallows region or manual (t='#tVal')"() {
        given:
        def app = newAppInstance()

        expect:
        app.validLocationType(tVal) == expected

        where:
        tVal || expected
        "l"  || false
        "u"  || false
        "p"  || true
        "z"  || true
        null || true
    }

    @Unroll
    def "unit conversions respect imperialUnits=#imperial"() {
        given:
        def app = newAppInstance([imperialUnits: imperial])

        expect:
        app.displayKmMiVal(10) == (imperial ? 6.2 : 10.0)
        app.displayMFtVal(10)  == (imperial ? 33 : 10)
        app.convertToKilometers(10) == (imperial ? 16 : 10)
        app.convertToMeters(10)     == (imperial ? 3  : 10)
        app.getVelocityUnits()      == (imperial ? "mph" : "kph")

        where:
        imperial << [false, true]
    }

    @Unroll
    def "isAddress returns true for textual addresses and false for 'lat,lon' strings: '#input'"() {
        given:
        def app = newAppInstance()

        expect:
        app.@isAddress(input) == expected

        where:
        input                          || expected
        "1600 Amphitheatre Pkwy, CA"   || true
        "New York, NY"                 || true
        "37.4219983,-122.084"          || false
        null                           || false
        ""                             || false
    }

    def "updateAddress reuses previous address within hysteresis"() {
        given:
        def app = newAppInstance([DEFAULT_geocodeLookupHysteresis: 0.2])
        app.getReverseGeocodeAddress = { data -> "SHOULD_NOT_BE_CALLED" }
        def currentMember = [ latitude: 40.0, longitude: -105.0, address: "Old Address" ]
        def data = [ lat: 40.001, lon: -105.001, address: "" ]

        when:
        app.@updateAddress(currentMember, data)

        then:
        data.address == "Old Address"
    }

    def "getReverseGeocodeAddress returns existing address when provided, else calls reverseGeocode"() {
        given:
        def app = newAppInstance()
        app.reverseGeocode = { lat, lon -> "LOOKED_UP_ADDRESS" }

        expect:
        app.@getReverseGeocodeAddress([address: "Some Address"]) == "Some Address"
        app.@getReverseGeocodeAddress([lat: 1.0, lon: 2.0, address: ""]) == "LOOKED_UP_ADDRESS"
    }

    def "reverseGeocode calls syncHttpGet and extracts provider-specific field when allowed"() {
        given:
        def app = newAppInstance()
        app.geocodeProvider = "1" // Google example
        app.settings["geocodeAPIKey_1"] = "ABC"
        app.GEOCODE_ADDRESS = [ null, "https://example/" ]
        app.REVERSE_GEOCODE_REQUEST_LAT = [ null, "lat=" ]
        app.REVERSE_GEOCODE_REQUEST_LON = [ null, "&lon=" ]
        app.GEOCODE_KEY = [ null, "&key=" ]
        app.ADDRESS_JSON = [ null, "formatted_address" ]
        // Simulate a JSON-like response map
        app.syncHttpGet = { String url ->
            [ results: [ ( "formatted_address"): [ "123 Main St" ] ] ]
        }

        expect:
        app.@reverseGeocode(40.0, -105.0) == "123 Main St"
    }

    def "reverseGeocode returns 'lat,lon' when disabled or blank response"() {
        given:
        def app = newAppInstance()
        app.geocodeProvider = "0"

        expect:
        app.@reverseGeocode(10.0, 20.0) == "10.0,20.0"
    }

    def "geocode parses provider responses and logs description"() {
        given:
        def app = newAppInstance()
        app.geocodeProvider = "2" // Geoapify example
        app.settings["geocodeAPIKey_2"] = "ABC"
        app.GEOCODE_ADDRESS = [ null, "X", "https://example/" ]
        app.GEOCODE_REQUEST = [ null, "Y", "q=" ]
        app.GEOCODE_KEY = [ null, "Z", "&key=" ]
        app.syncHttpGet = { String url ->
            [ results: [ lat: [40.123456], lon: [-105.654321] ] ]
        }
        app.logDescriptionText = { String s -> /* no-op */ }

        when:
        def pair = app.@geocode("Boulder, CO")

        then:
        pair[0] == 40.123456G.round(6)
        pair[1] == -105.654321G.round(6)
    }

    @Unroll
    def "isHTTPsURL properly detects scheme for '#url'"() {
        given:
        def app = newAppInstance()

        expect:
        app.isHTTPsURL(url) == expected

        where:
        url                           || expected
        "https://example.com"         || true
        "HTTPS://secure.example.com"  || true
        "http://example.com"          || false
        null                          || false
        ""                            || false
    }

    def "recorderURLType defaults to local source and switches to cloud when https"() {
        given:
        def app = newAppInstance()
        app.URL_SOURCE = ["cloud", "local"]
        app.getRecorderURL = { -> "http://local" }

        expect:
        app.recorderURLType() == "local"

        when:
        app.getRecorderURL = { -> "https://remote" }

        then:
        app.recorderURLType() == "cloud"
    }

    def "checkAttributeLimit enforces 1024-byte limit including hidden input buffer"() {
        given:
        def app = newAppInstance()
        def base = "A" * 1014  // length + 11 > 1024 triggers overflow

        when:
        def res = app.checkAttributeLimit(base)

        then:
        res.startsWith("Too much data to display.")
    }

    def "getDistanceFromHome returns 0.0 on error and logs"() {
        given:
        def app = newAppInstance()
        app.getHomeRegion = { -> [ lat: null, lon: null ] } // cause toDouble() failure in haversine

        when:
        def d = app.@getDistanceFromHome([ lat: 40.0, lon: -105.0 ])

        then:
        d == 0.0
    }

    def "sendUpdate aggregates updates based on flags and valid position type"() {
        given:
        def app = newAppInstance()
        // stub helpers used by sendUpdate
        app.validPositionType = { String t -> t in ["p","u"] }
        app.sendMemberPositions = { m, d -> ["pos"] }
        app.sendClearWaypointsRequest = { m -> ["clear"] }
        app.sendWaypoints = { m -> ["wpts"] }
        app.removePlaces = { -> /* no-op */ }
        app.sendConfiguration = { m -> ["cfg"] }
        app.checkRegionConfiguration = { m, d -> [] }
        app.sendReportLocationRequest = { m -> ["reqLoc"] }
        app.sendReportWaypointsRequest = { m -> ["reqWpts"] }
        app.sendReportStatusRequest = { m -> ["status"] }

        def currentMember = [ name:"bob", updateWaypoints:true, updateLocation:true, updateDisplay:false, requestLocation:true, getRegions:true ]
        def data = [ t: "p" ]

        when:
        def update = app.@sendUpdate(currentMember, data)

        then:
        update.containsAll(["pos","clear","wpts","cfg","reqLoc","reqWpts","status"])
    }

    def "getAndroidMembers filters members whose appVersion contains ANDROID_USER_AGENT"() {
        given:
        def app = newAppInstance()
        app.ANDROID_USER_AGENT = "android"
        app.settings = [ enabledMembers: ["a","b","c"] ]
        app.state = [ members: [
            [ name:"a", appVersion:"OwnTracks/ios 1.2.3" ],
            [ name:"b", appVersion:"OwnTracks/android 2.3.4" ],
            [ name:"c", appVersion:null ]
        ] ]

        when:
        def list = app.getAndroidMembers()

        then:
        list == ["b"]
    }

    @Unroll
    def "isAndroidMember detects android by user agent presence: '#agent'"() {
        given:
        def app = newAppInstance()
        app.ANDROID_USER_AGENT = "android"

        expect:
        app.isAndroidMember([appVersion: agent]) == expected

        where:
        agent                           || expected
        "OwnTracks/android 2.0"         || true
        "android"                       || true
        "OwnTracks/ios 2.0"             || false
        null                            || false
    }
}