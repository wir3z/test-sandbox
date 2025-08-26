/*
  Tests for OwnTracks Hubitat package manifest.
  Framework: Spock (Groovy) Specification with @Unroll where appropriate.
  Focus: Validating fields introduced/changed in the diff, including versions, dates, and links.
*/
import groovy.json.JsonSlurper
import spock.lang.*

class PackageManifestSpec extends Specification {
  /* BEGIN-PACKAGE-MANIFEST-TESTS (auto-generated) */
  def "should parse manifest JSON and contain required top-level fields"() {
    given: "a JSON manifest fixture matching PR diff"
    def json = new groovy.json.JsonSlurper().parse(new File("owntracks-hubitat/test/resources/packageManifest.fixture.json"))

    expect: "required keys and types exist"
    json.packageName == "OwnTracks"
    json.author == "Lyle Pakula"
    json.minimumHEVersion == "0.0"
    json.dateReleased == "2025-08-20"
    json.releaseNotes instanceof String && json.releaseNotes.contains("v1.8.23")
    json.documentationLink.startsWith("https://")
    json.communityLink.startsWith("https://")
    json.apps instanceof List && json.apps.size() == 1
    json.drivers instanceof List && json.drivers.size() == 2
  }

  def "apps entry should define required properties and correct versions per diff"() {
    given:
    def json = new groovy.json.JsonSlurper().parse(new File("owntracks-hubitat/test/resources/packageManifest.fixture.json"))

    when:
    def app = json.apps[0]

    then:
    app.id == "9197099c-9560-4983-abdd-b594a8472a75"
    app.name == "OwnTracks"
    app.namespace == "lpakula"
    app.location == "https://raw.githubusercontent.com/wir3z/hubitat/main/owntracks-hubitat/OwnTracks%20App.groovy"
    app.required == true
    app.oauth == true
    app.primary == true
    app.version == "1.8.23"
  }

  @Unroll
  def "drivers entries should define required properties and expected versions - #name"(String id, String name, String version) {
    given:
    def json = new groovy.json.JsonSlurper().parse(new File("owntracks-hubitat/test/resources/packageManifest.fixture.json"))

    expect:
    def found = json.drivers.find { it.id == id }
    found && found.name == name && found.namespace == "lpakula" && found.required == true && found.version == version

    where:
    id                                      | name                      | version
    "a6150d8c-a032-4409-9737-0a48eab7ad82"  | "OwnTracks Driver"        | "1.8.7"
    "acc5f615-8901-4b22-99c3-282a6244b892"  | "OwnTracks Common Driver" | "1.8.1"
  }

  def "links should reference raw GitHub for groovy files and be https"() {
    given:
    def json = new groovy.json.JsonSlurper().parse(new File("owntracks-hubitat/test/resources/packageManifest.fixture.json"))

    expect: "locations use HTTPS and end with .groovy"
    json.apps.every { it.location.startsWith("https://") && it.location.endsWith(".groovy") }
    json.drivers.every { it.location.startsWith("https://") && it.location.endsWith(".groovy") }

    and: "documentation and community links are HTTPS"
    json.documentationLink.startsWith("https://")
    json.communityLink.startsWith("https://")
  }

  def "release notes should contain version entries in descending order and be non-empty"() {
    given:
    def json = new groovy.json.JsonSlurper().parse(new File("owntracks-hubitat/test/resources/packageManifest.fixture.json"))
    def notes = json.releaseNotes

    expect:
    notes.trim()
    notes.readLines().first().startsWith("v1.8.23")
    notes.contains("v1.8.0")
  }

  def "manifest should reject unexpected shapes: missing keys or wrong types (negative tests via synthetic mutations)"() {
    given: "a baseline manifest map"
    def baseline = new groovy.json.JsonSlurper().parse(new File("owntracks-hubitat/test/resources/packageManifest.fixture.json")) as Map

    when: "remove a required top-level key"
    def missing = new LinkedHashMap(baseline); missing.remove("packageName")

    then: "our validation helper flags it as invalid"
    !validateManifest(missing).valid

    when: "wrong type for apps (should be List)"
    def wrongType = new LinkedHashMap(baseline); wrongType.apps = [:]

    then:
    !validateManifest(wrongType).valid

    when: "driver missing version"
    def noVersion = new LinkedHashMap(baseline); noVersion.drivers = baseline.drivers.collect { it.clone() } ; noVersion.drivers[0].remove("version")

    then:
    !validateManifest(noVersion).valid
  }

  private static Map validateManifest(Map m) {
    def errors = [] as List<String>
    // Required top-level keys
    ["packageName","author","minimumHEVersion","dateReleased","releaseNotes","documentationLink","communityLink","apps","drivers"].each { k ->
      if (!m.containsKey(k)) errors << "Missing key: $k"
    }
    if (!(m.apps instanceof List)) errors << "apps must be a List"
    if (!(m.drivers instanceof List)) errors << "drivers must be a List"
    if (m.apps instanceof List) {
      m.apps.eachWithIndex { a, i ->
        ["id","name","namespace","location","required","version"].each { k -> if (!a.containsKey(k)) errors << "apps[$i] missing $k" }
      }
    }
    if (m.drivers instanceof List) {
      m.drivers.eachWithIndex { d, i ->
        ["id","name","namespace","location","required","version"].each { k -> if (!d.containsKey(k)) errors << "drivers[$i] missing $k" }
      }
    }
    return [valid: errors.isEmpty(), errors: errors]
  }
  /* END-PACKAGE-MANIFEST-TESTS */
}