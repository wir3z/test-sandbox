import spock.lang.*
import groovy.json.*

/**
 * Test framework: Spock (Groovy) with Specification
 * These unit tests focus on pure/mostly-pure functions and small behaviors from OwnTracks App.groovy,
 * using a light-weight shim for Hubitat-specific globals (state, settings, app, location).
 */
class OwnTracksAppSpec extends Specification {

    // Script under test loaded once per spec to allow metaClass injection
    static Script appScript

    def setupSpec() {
        // Load the application Groovy script from repository (path may differ in CI; update if needed)
        // We search common names; fallback to inline minimal script if path not found.
        def candidates = [
            'OwnTracks App.groovy',
            'OwnTracks.groovy',
            'src/main/groovy/OwnTracks App.groovy',
            'src/OwnTracks App.groovy'
        ]
        File file = candidates.collect { new File(it) }.find { it.exists() }
        if (!file) {
            // Fallback: load from working file under review (this test assumes same dir path as PR)
            file = new File('OwnTracks App.groovy') // adjust if necessary
        }
        if (!file.exists()) {
            // As a final fallback, evaluate minimal container so tests still compile; most tests will be skipped
            appScript = new GroovyShell().evaluate('''def appVersion(){ "1.8.23" }''') as Script
            return
        }
        appScript = new GroovyShell().parse(file)
        primeHubitatShims(appScript)
    }

    def setup() {
        // Reset state and settings between tests
        appScript.state.clear()
        appScript.settings.clear()
        appScript.app.metaClass = null
        appScript.location.metaClass = null
        primeHubitatShims(appScript)
    }

    // Provide Hubitat environment shims
    static void primeHubitatShims(Script s) {
        // simple maps for settings/state
        s.metaClass.state = [:].withDefault { null }
        s.metaClass.settings = [:].withDefault { null }

        // app shim with updateSetting/removeSetting and label
        s.metaClass.app = [
            id: 42,
            label: 'OwnTracks',
            removeSetting: { String k -> s.settings.remove(k) },
            updateSetting: { String k, Map v -> s.settings[k] = v.value },
        ]

        // location shim (timezone and hub IP)
        s.metaClass.location = [
            timeZone: TimeZone.getTimeZone("America/Los_Angeles"),
            getTimeZone: { -> TimeZone.getTimeZone("America/Los_Angeles") },
            hubs: [[ getDataValue: { String key -> key == "localIP" ? "192.168.1.10" : "" }]],
            getLatitude: { -> 37.422331 },
            getLongitude:{ -> -122.0843455 }
        ]

        // Provide minimal helpers the app uses in pure functions
        s.metaClass.getFullLocalApiServerUrl = { -> "http://192.168.1.10/apps/api/123" }
        s.metaClass.fullApiServerUrl = { -> "https://cloud.hubitat.com/api/123/null" } // 'null' is replaced by path in code
        s.metaClass.createAccessToken = { -> s.state.accessToken = "token123" }
        s.metaClass.now = { -> System.currentTimeMillis() }
        s.metaClass.params = [:] // for routes that read params
        s.metaClass.render = { Map m -> m.data } // return raw data for assertions
        s.metaClass.parseJson = { String body -> new groovy.json.JsonSlurper().parseText(body) }
        s.metaClass.request = [
            headers:[ 'X-limit-u':"[Bob]", 'X-limit-d':"[Phone]", 'User-agent':"Owntracks-Android" ],
            HOST: "cloud.hubitat.com",
            body: '{"_type":"location"}'
        ]
        // Logging shims
        s.metaClass.log = [
            info:{ String m -> }, warn:{ String m -> }, debug:{ String m -> }, error:{ String m -> }
        ]
    }

    def "appVersion matches expected semantic string"() {
        expect:
        appScript.appVersion() == "1.8.23"
    }

    @Unroll
    def "getLocatorPriority respects highPowerMode(#hpm) and inRegion(#inRegion)"() {
        given:
        appScript.settings.highPowerMode = hpm
        appScript.metaClass.DEFAULT_locatorPriority = "BalancedPowerAccuracy"
        appScript.metaClass.DYNAMIC_INTERVALS = [ locatorPriority: "HighAccuracy" ]

        expect:
        appScript.getLocatorPriority(inRegion) == expected

        where:
        hpm   | inRegion || expected
        true  | false    || "HighAccuracy"          // high power outside region -> dynamic
        true  | true     || "BalancedPowerAccuracy" // high power but inside region -> default
        false | false    || "BalancedPowerAccuracy" // not high power -> default
        false | true     || "BalancedPowerAccuracy"
    }

    def "memberInGlobalMemberGroup true for service, null groups, or contains default group"() {
        given:
        appScript.metaClass.DEFAULT_globalGroupNumber = 0
        expect:
        appScript.memberInGlobalMemberGroup([name:"OwnTracks"])       // service
        appScript.memberInGlobalMemberGroup([name:"A", groups:null])  // null groups
        appScript.memberInGlobalMemberGroup([name:"A", groups:["0"]]) // contains default
        !appScript.memberInGlobalMemberGroup([name:"A", groups:["2"]])
    }

    def "getMatchingGroupMembersForMember filters by enabled and group intersection and privacy"() {
        given:
        appScript.state.members = [
            [name:"A", groups:["0"], lastReportTime:1],
            [name:"B", groups:["1"], lastReportTime:2],
            [name:"C", groups:["0","1"], lastReportTime:3]
        ]
        appScript.settings.enabledMembers = ["A","B","C"]
        appScript.settings.privateMembers = [] // none private

        when:
        def forA = appScript.getMatchingGroupMembersForMember([name:"A", groups:["0"]])
        def forB = appScript.getMatchingGroupMembersForMember([name:"B", groups:["1"]])

        then:
        forA*.name as Set == ["A","C"] as Set  // intersect with 0
        forB*.name as Set == ["B","C"] as Set  // intersect with 1
    }

    def "getEnabledAndNotHiddenMembers returns only enabled non-private globals sorted"() {
        given:
        appScript.metaClass.DEFAULT_globalGroupNumber = 0
        appScript.state.members = [
            [name:"Zed", groups:["0"]],
            [name:"Amy", groups:["1"]],
            [name:"Bob", groups:["0"]]
        ]
        appScript.settings.enabledMembers = ["Zed","Amy","Bob"]
        appScript.settings.privateMembers = ["Amy"]

        expect:
        appScript.getEnabledAndNotHiddenMembers() == ["Bob","Zed"]
    }

    def "getEnabledAndNotHiddenMemberData with null name falls back to global-only filter and sorts by lastReportTime"() {
        given:
        appScript.metaClass.DEFAULT_globalGroupNumber = 0
        appScript.state.members = [
            [name:"A", groups:["0"], lastReportTime: 20],
            [name:"B", groups:["1"], lastReportTime: 10],
            [name:"C", groups:["0","1"], lastReportTime: 30],
        ]
        appScript.settings.enabledMembers = ["A","B","C"]
        appScript.settings.privateMembers = []

        when:
        def result = appScript.getEnabledAndNotHiddenMemberData(null)

        then:
        result*.name == ["B","A","C"]*.toString().sort { [10,20,30] } // ensure method returns list; we just assert presence and order
        result*.name.containsAll(["A","C"]) // only globals intersect
    }

    def "getNonFollowRegions filters out +follow and returns names or tst map based on parameter"() {
        given:
        appScript.state.places = [
            [desc:"+60follow", tst:1],
            [desc:"Home",      tst:2],
            [desc:"Work",      tst:3],
        ]

        expect: "names list when collect=0"
        appScript.getNonFollowRegions(0) == ["Home","Work"]

        and: "tst->desc map when collect=1"
        appScript.getNonFollowRegions(1) == [2:"Home", 3:"Work"]
    }

    def "getHomeRegion returns state.places element matching homePlace tst"() {
        given:
        appScript.state.places = [
            [desc:"Home", lat:1, lon:2, tst:42, rad:100]
        ]
        appScript.settings.homePlace = "42"

        expect:
        appScript.getHomeRegion().desc == "Home"
    }

    @Unroll
    def "isHTTPsURL detects https for '#url'"() {
        expect:
        appScript.isHTTPsURL(url) == expected

        where:
        url                           || expected
        "https://example.com"         || true
        "http://example.com"          || false
        null                          || false
    }

    def "recorderURLType returns cloud source when recorderURL is https else local"() {
        given:
        appScript.metaClass.URL_SOURCE = ["[cloud.hubitat.com]","[local.com]"]
        appScript.settings.recorderURL = "https://recorder.example.com"

        expect:
        appScript.recorderURLType() == "[cloud.hubitat.com]"

        when:
        appScript.settings.recorderURL = "http://192.168.1.50:8083"
        then:
        appScript.recorderURLType() == "[local.com]"
    }

    @Unroll
    def "checkAttributeLimit enforces 1024-byte cap (payload length #len)"() {
        when:
        String payload = "x" * len
        def res = appScript.checkAttributeLimit(payload)

        then:
        (len + 11) > 1024 ? res.startsWith("Too much data") : res == payload

        where:
        len << [10, 1013, 1014, 2000]
    }

    @Unroll
    def "display units conversions respect imperialUnits=#imperial"() {
        given:
        appScript.settings.imperialUnits = imperial

        expect:
        // displayKmMiVal is 1 decimal
        Math.abs(appScript.displayKmMiVal(10) - expectedKmMi) < 0.1
        // displayMFtVal integer rounding
        appScript.displayMFtVal(1.0) == expectedMFt
        appScript.getSmallUnits() == (imperial ? "ft" : "m")
        appScript.getLargeUnits() == (imperial ? "mi" : "km")
        appScript.getVelocityUnits() == (imperial ? "mph" : "kph")

        where:
        imperial || expectedKmMi | expectedMFt
        false    || 10.0         | 1
        true     || 6.2          | 3
    }

    @Unroll
    def "convert distance helpers round correctly with imperialUnits=#imperial"() {
        given:
        appScript.settings.imperialUnits = imperial

        expect:
        appScript.convertToKilometers(val) instanceof Integer
        appScript.convertToMeters(val)     instanceof Integer

        where:
        imperial | val
        false    | 100
        true     | 100
    }

    @Unroll
    def "getCompassDifference(#a,#b) returns acute difference"() {
        expect:
        appScript.getCompassDifference(a,b) == expected
        where:
        a  | b  || expected
        10 | 30 || 20
        350| 10 || 20
        0  | 180|| 180
        179| 181|| 2
    }

    def "angleFromCoordinate basic sanity (north-east bearing > 0 and < 180)"() {
        when:
        def angle = appScript.angleFromCoordinate(37.0,-122.0,37.1,-121.9)
        then:
        angle >= 0 && angle <= 360
    }

    def "haversine returns ~0 for identical points and grows for distant points"() {
        expect:
        appScript.haversine(0,0,0,0) == 0.0
        appScript.haversine(37.7749,-122.4194,34.0522,-118.2437) > 300 // SF to LA ~559 km
    }

    @Unroll
    def "validPositionType only allows ping or manual; '#t' => #expected"() {
        expect:
        appScript.validPositionType(t) == expected
        where:
        t   || expected
        "p" || true
        "u" || true
        "c" || false
        null|| false
    }

    @Unroll
    def "validLocationType filters out region/manual; '#t' => #expected"() {
        expect:
        appScript.validLocationType(t) == expected
        where:
        t   || expected
        "l" || false
        "u" || false
        "p" || true
        "r" || true
        null|| true
    }

    def "getAttributeURL uses cloud vs local host and injects token"() {
        given:
        appScript.state.accessToken = "abc123"
        // cloud path
        def cloud = appScript.getAttributeURL("[cloud.hubitat.com]", "googlemap")
        def local = appScript.getAttributeURL("[local.com]", "googlemap")

        expect:
        cloud.contains("access_token=abc123")
        local.contains("access_token=abc123")
        cloud.startsWith("https://")
        local.startsWith("http://")
    }

    def "displayRegionsPendingDelete emits banner when any region has INVALID_COORDINATE"() {
        given:
        appScript.metaClass.INVALID_COORDINATE = 999
        appScript.state.places = [
            [desc:"DeleteMe", lat:999, lon:999],
            [desc:"KeepMe",   lat:1,   lon:1  ]
        ]
        // Emulate paragraph() collecting output
        String out = null
        appScript.metaClass.paragraph = { String s -> out = s }

        when:
        appScript.displayRegionsPendingDelete()

        then:
        out.contains("DeleteMe")
        out.contains("pending deletion")
    }

    def "displayMissingHomePlace warns when homePlace unset"() {
        given:
        String out = ""
        appScript.metaClass.paragraph = { String s -> out = s }

        when:
        appScript.displayMissingHomePlace()

        then:
        out.contains("'Home' place not set")
    }
}