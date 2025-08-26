package owntracks_hubitat

/*
  Note on testing library/framework:
  - This test uses the Spock Framework (Groovy), extending spock.lang.Specification.
  - It validates the JSON manifest content introduced/changed in the PR diff.
*/

import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate
import java.time.format.DateTimeParseException

class PackageManifestJsonSpec extends Specification {

  // JSON payload under test (from PR diff focus)
  // Kept inline to make tests deterministic and focused on the changed content.
  private static final String manifestJson = '''{
  "packageName": "OwnTracks",
  "author": "Lyle Pakula",
  "minimumHEVersion": "0.0",
  "dateReleased": "2025-08-20",
  "releaseNotes": "v1.8.23 - Fixed migration issue with transition deadband.\\nv1.8.23 - Fixed time stamp type conversion in driver.\\nv1.8.23 - Removed past cleanup that removed drawer scaling.  Added a deadband to prevent transition and presence  arrived/departed ping-pong notifications.\\nv1.8.22 - Added member battery level to each history point.\\nv1.8.21 - Google Family Map: Improved zooming on members and member drawer.  Selecting a member row in the drawer selects that member.  Address issue that could lead to thumbnails not being displayed.  Added zoom option for smart displays (Nest, Amazon).\\nv1.8.20 - Fixed the zoom issues and removed the mobile portrait mode zoom for the Google Family map.  Fixed race condition when the thumbnails were loading on the family map.  Fixed issue where new users were not being added to the default group.\\nv1.8.19 - Added scalers to the map zoom for mobile portrait mode and the member drawer content.\\nv1.8.18 - Changed the drawer behavior to allow a single click/tap to open/close vs dragging.  Increased width up to 500 pixels.\\nv1.8.17 - Add the ability to scale the thumbnail size Google Family Map member drawer.\\nv1.8.15 - Added a member drawer to the bottom of the Google Family Map. Clicking on a thumbnail will follow that user.\\nv1.8.14 - Added setting to save the last followed member between map reloads.\\nv1.8.13 - Fixed issue where passing a member name to the Google Family Map needed to be in lowercase.\\nv1.8.12 - Rephrased the member group reset button.\\nv1.8.11 - Fixed issue creating a new region.\\nv1.8.10 - Allow all new regions to be added to member notifications if enabled.  Fixed issue where the last notification region/device couldn't be deselected.\\nv1.8.9 - Changed app to single threaded.\\nv1.8.8 - Fixed location debug logging. Force an update to the transition attributes to eliminate cache delays.\\nv1.8.7 - Ignore duplicate transition events if they occur within 5-seconds of the previous one.\\nv1.8.7 - Changed map and geocode limits to match upcoming Google changes.\\nv1.8.6 - Added radius around the Google Friends Map member pin that scales based on their location accuracy.\\nv1.8.5 - Cleanup migration.  Fixed issue if thumbnails were enabled, but no image files were loaded in the hub.\\nv1.8.4 - Added missing members element in JSON.\\nv1.8.2 - Return last member locations in a JSON message when the mobile app setup URL is requested.  Allow high power mode to be disabled when in a region.\\nv1.8.1 - Member status would inaccurately indicate a permission error for iOS phones.\\nv1.8.0 - Member status now indicates configurations that will impact location performance.  Fix issue where history compression was not properly removing markers at direction transitions.  Google Friends map will auto-update when the main app updates.\\n",
  "documentationLink": "https://github.com/wir3z/hubitat/blob/main/owntracks-hubitat/README.md",
  "communityLink": "https://community.hubitat.com/t/release-owntracks/130821",
  "apps": [
    {
      "id": "9197099c-9560-4983-abdd-b594a8472a75",
      "name": "OwnTracks",
      "namespace": "lpakula",
      "location": "https://raw.githubusercontent.com/wir3z/hubitat/main/owntracks-hubitat/OwnTracks%20App.groovy",
      "required": true,
      "oauth": true,
      "primary": true,
      "version": "1.8.23"
    }
  ],
  "drivers": [
    {
      "id": "a6150d8c-a032-4409-9737-0a48eab7ad82",
      "name": "OwnTracks Driver",
      "namespace": "lpakula",
      "location": "https://raw.githubusercontent.com/wir3z/hubitat/main/owntracks-hubitat/OwnTracks%20Driver.groovy",
      "required": true,
      "version": "1.8.7"
    },
    {
      "id": "acc5f615-8901-4b22-99c3-282a6244b892",
      "name": "OwnTracks Common Driver",
      "namespace": "lpakula",
      "location": "https://raw.githubusercontent.com/wir3z/hubitat/main/owntracks-hubitat/OwnTracks%20Common%20Driver.groovy",
      "required": true,
      "version": "1.8.1"
    }
  ]
}'''

  // Parsed manifest map reused across tests
  private static final Map manifest = new JsonSlurper().parseText(manifestJson) as Map

  // Common validation patterns
  private static final def UUID_V4 = ~/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$/
  private static final def SEMVER = ~/^\d+\.\d+\.\d+$/
  private static final def DATE = ~/^\d{4}-\d{2}-\d{2}$/

  def "manifest JSON is syntactically valid and parses"() {
    expect:
    manifest instanceof Map
    manifest.packageName == 'OwnTracks'
    manifest.author == 'Lyle Pakula'
  }

  def "top-level keys are present and have correct types"() {
    expect:
    manifest.keySet().containsAll([
      'packageName','author','minimumHEVersion','dateReleased','releaseNotes',
      'documentationLink','communityLink','apps','drivers'
    ] as Set)
    manifest.packageName instanceof String
    manifest.author instanceof String
    manifest.minimumHEVersion instanceof String
    manifest.dateReleased instanceof String
    manifest.releaseNotes instanceof String
    manifest.documentationLink instanceof String
    manifest.communityLink instanceof String
    manifest.apps instanceof List
    manifest.drivers instanceof List
  }

  def "dateReleased uses yyyy-MM-dd and equals 2025-08-20"() {
    given:
    def dateStr = manifest.dateReleased

    expect:
    dateStr ==~ DATE
    LocalDate.parse(dateStr) == LocalDate.of(2025, 8, 20)

    and: "parsing doesn't throw"
    noExceptionThrown()
  }

  def "minimumHEVersion uses N.N pattern"() {
    expect:
    manifest.minimumHEVersion ==~ ~/^\d+\.\d+$/
  }

  def "apps and drivers counts are as expected"() {
    expect:
    manifest.apps.size() == 1
    manifest.drivers.size() == 2
  }

  @Unroll
  def "apps[#idx] '#app.name' has required fields and valid values"(int idx, Map app) {
    expect:
    app.id ==~ UUID_V4
    app.name == 'OwnTracks'
    app.namespace == 'lpakula'
    app.location.startsWith('https://raw.githubusercontent.com/wir3z/hubitat/main/owntracks-hubitat/')
    app.location.endsWith('OwnTracks%20App.groovy')
    app.required == true
    app.oauth == true
    app.primary == true
    app.version ==~ SEMVER
    app.version == '1.8.23' // matches latest in release notes

    where:
    [idx, app] << manifest.apps.withIndex().collect { a, i -> [i, a] }
  }

  @Unroll
  def "drivers[#idx] '#driver.name' has required fields and valid values"(int idx, Map driver, String expectedSuffix) {
    expect:
    driver.id ==~ UUID_V4
    driver.name in ['OwnTracks Driver', 'OwnTracks Common Driver']
    driver.namespace == 'lpakula'
    driver.location.startsWith('https://raw.githubusercontent.com/wir3z/hubitat/main/owntracks-hubitat/')
    driver.location.endsWith(expectedSuffix)
    driver.required == true
    driver.version ==~ SEMVER

    where:
    tuples << manifest.drivers.withIndex().collect { d, i ->
      def suffix = d.name == 'OwnTracks Driver' ? 'OwnTracks%20Driver.groovy' : 'OwnTracks%20Common%20Driver.groovy'
      [i, d, suffix]
    }
    idx = tuples[0]
    driver = tuples[1]
    expectedSuffix = tuples[2]
  }

  def "releaseNotes are multi-line, well-formed, and include expected versions"() {
    given:
    def notes = manifest.releaseNotes
    def lines = notes.readLines()

    expect: "each line begins with a version and description"
    lines.size() >= 20
    lines.every { it ==~ /^v\d+\.\d+\.\d+ - .+/ }

    and: "latest version 1.8.23 appears three times as per diff"
    lines.count { it.startsWith('v1.8.23 -') } == 3

    and: "specific versions from the diff are present"
    ['1.8.23','1.8.22','1.8.21','1.8.20','1.8.19','1.8.18','1.8.17','1.8.15','1.8.14',
     '1.8.13','1.8.12','1.8.11','1.8.10','1.8.9','1.8.8','1.8.7','1.8.6','1.8.5',
     '1.8.4','1.8.2','1.8.1','1.8.0'
    ].every { v -> notes.contains("v${v} -") }
  }

  def "app version matches latest version mentioned in releaseNotes"() {
    given:
    def firstNoteLine = manifest.releaseNotes.readLines().find { it.startsWith('v') }
    def m = firstNoteLine =~ /^v(\d+\.\d+\.\d+)\b/

    expect:
    m.find()
    def latest = m.group(1)
    manifest.apps[0].version == latest
  }

  def "documentation and community links use https and expected hosts"() {
    expect:
    manifest.documentationLink.startsWith('https://github.com/')
    manifest.communityLink.startsWith('https://community.hubitat.com/')
  }

  def "all IDs are unique across apps and drivers"() {
    given:
    def allIds = (manifest.apps.collect { it.id } + manifest.drivers.collect { it.id })

    expect:
    allIds.size() == allIds.toSet().size()
  }

  def "driver names are unique and non-empty"() {
    given:
    def names = manifest.drivers.collect { it.name }

    expect:
    names.every { it instanceof String && it.trim() }
    names.size() == names.toSet().size()
  }

  def "namespace is consistent across all entries"() {
    expect:
    (manifest.apps + manifest.drivers).every { it.namespace == 'lpakula' }
  }
}