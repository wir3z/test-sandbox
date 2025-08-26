import spock.lang.*
import java.util.regex.Pattern
import java.net.HttpURLConnection

/**
 * Test stack: Spock Framework (Groovy) with JDK standard library.
 *
 * These tests validate the documentation content relevant to the PR diff.
 * They focus on:
 *  - Required section headings presence
 *  - Critical URLs and their formatting
 *  - Guarding against specific regressions/typos observed in previous revisions
 *  - Optional network link reachability (can be disabled by setting DOCS_OFFLINE=1)
 *
 * To run only offline-safe assertions:
 *   DOCS_OFFLINE=1 ./gradlew test
 */
class ReadmeSpec extends Specification {

  static File locateReadme() {
    // Prefer README.md within owntracks-hubitat module, with fallbacks
    List<String> candidates = [
      "owntracks-hubitat/README.md",
      "owntracks-hubitat/README.MD",
      "owntracks-hubitat/Readme.md",
      "owntracks-hubitat/readme.md",
      "README.md",
      "README.MD"
    ]
    for (String path : candidates) {
      File f = new File(path)
      if (f.exists() && f.isFile()) return f
    }
    return null
  }

  @Shared
  File readmeFile = locateReadme()

  @Shared
  String readme = readmeFile?.getText("UTF-8") ?: ""

  def "readme should exist and be non-empty"() {
    expect:
    readmeFile != null && readmeFile.exists() && readmeFile.length() > 0
  }

  def "top-level title should be present"() {
    given: "A canonical acceptable title variants"
    def patterns = [
      ~/^#\s*OwnTracks\b.*$/m,
      ~/^#\s*Owntracks\b.*$/m  // in case of lowercase 't' variant
    ]
    expect:
    patterns.any { pat -> (readme =~ pat).find() }
  }

  @Unroll
  def "should include required section heading: #heading"() {
    expect:
    readme.toLowerCase().contains(heading.toLowerCase())

    where:
    heading << [
      "## Hubitat Installation",
      "## Mobile App Configuration",
      "### Geocode API Settings - Converts latitude/longitude to address",
      "### Google Map API Settings - Creates a combined family map and adds radius bubbles on the 'Region' 'Add/Edit/Delete' page maps",
      "#### Member History and Pin Colors",
      "#### Region Pin Colors",
      "## Enable OwnTracks Recorder",
      "## Enable Optional OwnTracks Frontend",
      "## Link Secondary Hub",
      "### Where do I get the Mobile application?",
      "### How do I set up Hubitat to support this integration?",
      "### What if I need help?"
    ]
  }

  def "should list supported geocode providers explicitly"() {
    expect:
    readme.contains("Google") && readme.toLowerCase().contains("geoapify") && readme.toLowerCase().contains("opencage")
  }

  def "Hubitat Community anchor should include target=_blank"() {
    given:
    def anchorPattern = ~/<a\\s+href="https?:\\/\\/community\\.hubitat\\.com\\/t\\/release-owntracks\\/\\d+"\\s+target="_blank">\\[RELEASE] OwnTracks for Hubitat Presence Detection<\\/a>/
    expect:
    (readme =~ anchorPattern).find()
  }

  @Unroll
  def "critical URLs should appear in README: #url"() {
    expect:
    readme.contains(url)

    where:
    url << [
      // Official OwnTracks
      "https://owntracks.org/",
      // Hubitat Community thread
      "https://community.hubitat.com/t/release-owntracks/130821",
      // Google Cloud Console APIs dashboard (as referenced)
      "https://console.cloud.google.com/apis/dashboard",
      // OwnTracks Recorder booklet page
      "https://owntracks.org/booklet/clients/recorder/",
      // OwnTracks Frontend repository
      "https://github.com/owntracks/frontend/"
    ]
  }

  def "example recorder URLs should use http scheme with example host and default ports 8083/8082"() {
    expect:
    readme.contains("http://enter.your.recorder.ip:8083")
    readme.contains("http://enter.your.recorder.ip:8082")
  }

  @Unroll
  def "README should not contain common regressions/typos: '#bad' (ensure correct: '#good')"() {
    expect:
    !readme.contains(bad)

    where:
    bad           | good
    "Fronend"     | "Frontend"
    "Seconday"    | "Secondary"
    "lest opaque" | "less opaque"
    "quote period"| "quota period"
    "the the"     | "the"
  }

  @Unroll
  def "URL format sanity check for: #name"() {
    given:
    Pattern p = ~/(?i)\\bhttps?:\\/\\/[\\w\\-\\.]+(?:\\:[0-9]{2,5})?(?:\\/[\\w\\-\\.\\/%\\?&=#]+)?/
    def matches = (readme =~ p).collect { it[0] }.toSet()

    expect:
    matches.any { it.startsWith(expectedPrefix) && it.contains(expectedHostPart) }

    where:
    name                           | expectedPrefix | expectedHostPart
    "Hubitat Community"            | "https://"     | "community.hubitat.com"
    "OwnTracks site"               | "https://"     | "owntracks.org"
    "Google Cloud Console"         | "https://"     | "console.cloud.google.com"
    "OwnTracks Frontend GitHub"    | "https://"     | "github.com"
  }

  def "optional: external URLs should be reachable (2xx/3xx) unless DOCS_OFFLINE=1"() {
    given:
    boolean offline = System.getenv("DOCS_OFFLINE") != null && System.getenv("DOCS_OFFLINE") != "0"
    assumeTrue(!offline) // skip when offline

    // Only check a curated subset to avoid flaky tests and to keep runtime small
    List<String> urls = [
      "https://owntracks.org/",
      "https://community.hubitat.com/t/release-owntracks/130821",
      "https://console.cloud.google.com/apis/dashboard",
      "https://github.com/owntracks/frontend/"
    ]

    expect:
    urls.every { url ->
      try {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection()
        conn.setRequestMethod("HEAD")
        conn.setConnectTimeout(4000)
        conn.setReadTimeout(4000)
        conn.setInstanceFollowRedirects(false)
        int code = conn.getResponseCode()
        // Accept 2xx or 3xx
        return (code >= 200 && code < 400)
      } catch (Throwable t) {
        // If the environment blocks network, this test should be run with DOCS_OFFLINE=1 to skip.
        return false
      }
    }
  }

  def "geocode provider quota language should reference quotas and periods correctly"() {
    given:
    def quotaTerms = [
      "quota", "usage", "period", "resets", "Google resets the total at the first of every month"
    ]
    expect:
    quotaTerms.every { term -> readme.toLowerCase().contains(term.toLowerCase()) }
  }

  def "Google Map API section should describe member UI indicators and auto-zoom behavior"() {
    expect:
    readme.contains("member thumbnails")
    readme.toLowerCase().contains("battery")
    readme.toLowerCase().contains("wifi")
    readme.toLowerCase().contains("mobile data")
    readme.toLowerCase().contains("auto-zoom")
  }

  // Helper to skip a test with Spock in a JUnit-compatible way
  static void assumeTrue(boolean condition) {
    if (!condition) {
      org.spockframework.runtime.model.SpecInfo specInfo = org.spockframework.runtime.SpockRuntimeUtil.getCurrentSpecInfo()
      // Throwing Spock's AssumptionViolatedException would require JUnit; mimic by Spock's built-in skip:
      org.spockframework.runtime.SpockRuntimeUtil.getCurrentSpecInfo()?.getBottomSpec()?.skip()
    }
  }
}