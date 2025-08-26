/*
  PackageManifestSpec.groovy
  Tests for OwnTracks package manifest.

  Testing library/framework: Spock Framework (Groovy) running on the JUnit platform.
  Focus: Validate the JSON manifest structure and content including versions, links,
         release notes formatting, and app/driver entries. Covers happy paths and
         edge/failure expectations via strict assertions.

  These tests intentionally avoid any network calls and validate URLs by pattern.
*/

import spock.lang.*
import groovy.json.JsonSlurper
import java.time.LocalDate

class PackageManifestSpec extends Specification {

  @Shared Map manifest
  @Shared File manifestFile

  def setupSpec() {
    manifestFile = findManifestFile()
    assert manifestFile?.exists() : "Manifest file not found in expected locations."
    manifest = (Map) new JsonSlurper().parse(manifestFile)
  }

  private static File findManifestFile() {
    // Common/expected locations
    String[] candidates = [
      "owntracks-hubitat/packageManifest.json",
      "owntracks-hubitat/PackageManifest.json",
      "packageManifest.json",
      "PackageManifest.json"
    ]
    for (String p : candidates) {
      File f = new File(p)
      if (f.exists()) return f
    }
    // Fallback: search for any JSON in owntracks-hubitat containing a known key
    File dir = new File("owntracks-hubitat")
    if (dir.exists()) {
      File found = dir.listFiles()?.find { it.name.toLowerCase().endsWith(".json") && it.text.contains('"packageName": "OwnTracks"') }
      return found
    }
    return null
  }

  def "manifest loads and has required top-level keys with correct types"() {
    expect:
    manifest != null
    manifest.keySet().containsAll([
      "packageName", "author", "minimumHEVersion", "dateReleased",
      "releaseNotes", "documentationLink", "communityLink", "apps", "drivers"
    ])

    manifest.packageName       instanceof String
    manifest.author            instanceof String
    manifest.minimumHEVersion  instanceof String
    manifest.dateReleased      instanceof String
    manifest.releaseNotes      instanceof String
    manifest.documentationLink instanceof String
    manifest.communityLink     instanceof String
    manifest.apps              instanceof List
    manifest.drivers           instanceof List
  }

  def "package metadata values are sane and non-empty"() {
    expect:
    manifest.packageName == "OwnTracks"
    manifest.author == "Lyle Pakula"
    manifest.minimumHEVersion ==~ /\d+\.\d+(\.\d+)?/        // allow 0.0 or 0.0.0
    manifest.documentationLink ==~ /^https:\/\/.+/
    manifest.communityLink ==~ /^https:\/\/.+/
    !manifest.releaseNotes.trim().isEmpty()
  }

  def "dateReleased is ISO yyyy-MM-dd and not in the future"() {
    when:
    LocalDate released = LocalDate.parse((String) manifest.dateReleased)

    then:
    !released.isAfter(LocalDate.now())
  }

  def "apps entries are well-formed and use expected fields"() {
    expect:
    (manifest.apps as List<Map>).every { app ->
      (app.id               instanceof String) && (app.id ==~ /^[0-9a-fA-F-]{36}$/) &&
      (app.name             instanceof String) &&
      (app.namespace        == "lpakula") &&
      (app.location         instanceof String) && (app.location ==~ /^https:\/\/.*\.groovy(\?.*)?$/) &&
      (app.required         in [true, false]) &&
      (app.version          instanceof String) && (app.version ==~ /\d+\.\d+\.\d+/) &&
      ((app.oauth   == null) || (app.oauth   in [true,false])) &&
      ((app.primary == null) || (app.primary in [true,false]))
    }
  }

  def "drivers entries are well-formed and use expected fields"() {
    expect:
    (manifest.drivers as List<Map>).every { d ->
      (d.id               instanceof String) && (d.id ==~ /^[0-9a-fA-F-]{36}$/) &&
      (d.name             instanceof String) &&
      (d.namespace        == "lpakula") &&
      (d.location         instanceof String) && (d.location ==~ /^https:\/\/.*\.groovy(\?.*)?$/) &&
      (d.required         in [true, false]) &&
      (d.version          instanceof String) && (d.version ==~ /\d+\.\d+\.\d+/)
    }
  }

  def "primary OwnTracks app is required and oauth-enabled"() {
    given:
    Map app = (manifest.apps as List<Map>).find { (it.name == "OwnTracks") || (it.primary == true) } as Map

    expect:
    app != null
    app.required == true
    app.oauth == true
    app.primary == true
  }

  def "app version equals highest version referenced in release notes"() {
    given:
    List<String> versions = extractVersions((String) manifest.releaseNotes)
    and:
    String highest = versions.sort { a, b -> compareSemver(a, b) }.last()

    when:
    def appVersions = (manifest.apps as List<Map>).collect { it.version as String }

    then:
    appVersions.contains(highest)
  }

  def "releaseNotes lines follow 'vX.Y.Z - description' format and are in non-increasing order"() {
    given:
    def lines = ((String) manifest.releaseNotes).readLines().findAll { it?.trim() }

    when:
    def versionPairs = lines.collect { ln ->
      def m = (ln =~ /^v(\d+\.\d+\.\d+)\s+-\s+.+$/)
      [ln, m ? m[0][1] : null]
    }

    then: "every non-blank line matches the expected format"
    versionPairs.every { ln, v -> v != null }

    and: "versions do not increase as you go down (duplicates are allowed)"
    def justVersions = versionPairs.collect { it[1] }
    (0..<justVersions.size()-1).every { i -> compareSemver(justVersions[i], justVersions[i+1]) >= 0 }
  }

  def "locations point to wir3z/hubitat raw GitHub URLs"() {
    expect:
    (manifest.apps + manifest.drivers).every { it.location instanceof String && it.location.startsWith("https://raw.githubusercontent.com/wir3z/hubitat/") }
  }

  // =================
  // Helper functions
  // =================
  static List<String> extractVersions(String notes) {
    return notes.readLines()
      .findAll { it?.trim() }
      .collect { line ->
        def m = (line =~ /^v(\d+\.\d+\.\d+)/)
        m ? m[0][1] : null
      }
      .findAll { it != null }
  }

  static int compareSemver(String a, String b) {
    List<Integer> pa = a.split(/\./)*.toInteger()
    List<Integer> pb = b.split(/\./)*.toInteger()
    for (int i=0; i<3; i++) {
      int diff = (pa[i] ?: 0) - (pb[i] ?: 0)
      if (diff != 0) return diff
    }
    return 0
  }
}