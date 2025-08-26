/**
 * Spec created by automation to cover OwnTracks driver behaviors.
 * Framework: Spock (Groovy) - Specification-based testing.
 */
import spock.lang.*

class OwnTracksDriverSpec extends Specification {
}

@groovy.transform.CompileStatic
class TestHarness {
  // Minimal driver-like script object that we can Spy on and inject state/device/parent.
  final def subject
  final Map<String,Object> attr = [:]
  final List<Map> events = []
  final List<String> logs = []
  final List<Map> scheduled = []
  final Map state = [:]

  TestHarness(def subject) {
    this.subject = subject
    // Inject fake platform globals
    subject.@state = state
    subject.metaClass.device = [
      name: 'Test Device',
      displayName: 'Test Device',
      currentValue: { String k -> attr[k] }
    ]
    // Default parent stub with simple numeric/string conversions
    subject.metaClass.parent = [
      displayKmMiVal: { def v -> v == null ? '0' : String.valueOf(v) },
      displayMFtVal:  { def v -> v == null ? '0' : String.valueOf(v) },
      getSmallUnits:  { -> "ft" },
      getLargeUnits:  { -> "mi" },
      getVelocityUnits: { -> "mph" },
      generateTransitionNotification: { String member, String phrase, String region, String time -> /* no-op */ }
    ]
    // Hook sendEvent to capture and update attr
    subject.metaClass.sendEvent = { Map evt ->
      events << evt
      if (evt.name && evt.containsKey('value')) {
        attr[evt.name as String] = evt.value
      }
    }
    // Logging helpers
    subject.metaClass.log = [
      info: { Object m -> logs << "INFO:${String.valueOf(m)}" },
      warn: { Object m -> logs << "WARN:${String.valueOf(m)}" },
      error:{ Object m -> logs << "ERROR:${String.valueOf(m)}" },
      debug:{ Object m -> logs << "DEBUG:${String.valueOf(m)}" },
    ]
    subject.metaClass.logDebug = { String m -> logs << "DEBUG:${m}" }
    subject.metaClass.logDescriptionText = { String m -> logs << "DESC:${m}" }

    // Time helpers
    subject.metaClass.now = { -> System.currentTimeMillis() }

    // Scheduling helpers
    subject.metaClass.unschedule = { Closure c -> scheduled << [op:'unschedule', fn: c.getClass().name] }
    subject.metaClass.runIn = { Integer seconds, Closure c -> scheduled << [op:'runIn', after: seconds, fn: c.getClass().name] }

    // Tiles placeholders
    subject.metaClass.generateTiles = { -> /* no-op for tests */ }
    subject.metaClass.generatePresenceTile = { -> /* no-op for tests */ }
    subject.metaClass.generateMemberTile = { -> /* no-op for tests */ }

    // Stubs for methods that may exist elsewhere but are called by snippet
    if (!subject.metaClass.respondsTo(subject, 'updateAttributes', Object)) {
      subject.metaClass.updateAttributes = { Object data -> /* no-op */ }
    }
    if (!subject.metaClass.respondsTo(subject, 'updateAdditionalAttributes', Object)) {
      subject.metaClass.updateAdditionalAttributes = { Object member -> /* no-op */ }
    }
    if (!subject.metaClass.respondsTo(subject, 'updated')) {
      subject.metaClass.updated = { -> /* no-op */ }
    }
  }
}

class DriverLoader {
  static def loadDriver() {
    // Load Groovy script under test dynamically
    // Attempt common paths; adjust if repository layout differs
    String[] candidates = [
      "owntracks-hubitat/OwnTracks User Driver.groovy",
      "owntracks-hubitat/OwnTracksUserDriver.groovy",
      "OwnTracks User Driver.groovy",
      "src/OwnTracksUserDriver.groovy",
      "drivers/OwnTracksUserDriver.groovy"
    ]
    File found = candidates
      .collect { new File(it) }
      .find { it.exists() }
    if (!found) {
      // Fallback: search by name heuristics
      def guess = (["fd", "-a", "-t", "f", "OwnTracks", "."] as String[] )
      try {
        def proc = new ProcessBuilder(guess).redirectErrorStream(true).start()
        proc.waitFor()
        def out = proc.inputStream.text.readLines()
        found = out.collect { new File(it) }.find { it.name.toLowerCase().contains("driver") && it.name.toLowerCase().endsWith(".groovy") }
      } catch (Throwable ignore) { }
    }
    assert found != null : "Driver script not found. Please ensure the OwnTracks driver Groovy script exists."
    return new GroovyShell().parse(found)
  }
}

class OwnTracksDriverSpec_DiffCoverage extends Specification {

  def "driverVersion returns expected string"() {
    given:
    def driver = DriverLoader.loadDriver()
    def h = new TestHarness(driver)

    expect:
    driver.driverVersion() == "1.8.7"
  }

  def "constants maps contain expected key mappings"() {
    given:
    def driver = DriverLoader.loadDriver()
    new TestHarness(driver)

    expect:
    driver.@MONITORING_MODE[1] == "Significant"
    driver.@BATTERY_STATUS[3] == "Full"
    driver.@DATA_CONNECTION["w"] == "WiFi"
    driver.@TRIGGER_TYPE["c"] == "Region"
    driver.@TRANSITION_DIRECTION["enter"] == "arrived"
    driver.@TRANSITION_PHRASES["leave"] == "departed from"
  }

  def "default configuration flags are set as expected"() {
    given:
    def driver = DriverLoader.loadDriver()
    new TestHarness(driver)

    expect:
    driver.@DEFAULT_displayExtendedAttributes
    !driver.@DEFAULT_displayMemberTile
    !driver.@DEFAULT_displayLastLocationTile
    driver.@DEFAULT_colorMemberTile
    driver.@DEFAULT_descriptionTextOutput
    !driver.@DEFAULT_debugOutput
    !driver.@DEFAULT_debugLogAddresses
    !driver.@DEFAULT_logLocationChanges
    driver.@DEFAULT_privateLocation == "Private"
    driver.@DEFAULT_pastLocationSearchWindow == 0.5
    driver.@DEFAULT_notificationHysteresisSeconds == 90
  }

  def "installed initializes state and sends startup events then calls updated"() {
    given:
    def driver = Spy(DriverLoader.loadDriver())
    def h = new TestHarness(driver)

    when:
    driver.installed()

    then:
    1 * driver.updated()
    h.attr.switch == "off"
    h.state.driverVersion == "1.8.7"
    h.state.memberName == ""
    h.state.homeName == ""
    h.state.pendingTransitionUpdateData == ""
    h.state.transitionDeadband == 0
    h.attr.containsKey('sinceTime')
  }

  def "arrived calls updatePresenceEvent with enter/present and current epoch seconds"() {
    given:
    def driver = Spy(DriverLoader.loadDriver())
    new TestHarness(driver)
    Integer nowSec = (System.currentTimeMillis() / 1000) as int

    when:
    driver.arrived()

    then:
    1 * driver.updatePresenceEvent("enter", "present", { it instanceof Integer && Math.abs(it - nowSec) <= 2 })
  }

  def "departed calls updatePresenceEvent with leave/not present and current epoch seconds"() {
    given:
    def driver = Spy(DriverLoader.loadDriver())
    new TestHarness(driver)
    Integer nowSec = (System.currentTimeMillis() / 1000) as int

    when:
    driver.departed()

    then:
    1 * driver.updatePresenceEvent("leave", "not present", { it instanceof Integer && Math.abs(it - nowSec) <= 2 })
  }

  def "updatePresenceEvent triggers transition, presence, and tiles"() {
    given:
    def driver = Spy(DriverLoader.loadDriver())
    new TestHarness(driver)

    when:
    driver.updatePresenceEvent("enter", "present", 12345)

    then:
    1 * driver.createTransitionEvent(driver.@state.homeName, "enter", 12345)
    1 * driver.createPresenceEvent("present", 12345)
    1 * driver.generateTiles()
  }

  def "getCurrentLocation returns Private when data is private"() {
    given:
    def driver = DriverLoader.loadDriver()
    new TestHarness(driver)

    expect:
    driver.getCurrentLocation([private:true]) == driver.@DEFAULT_privateLocation
  }

  def "getCurrentLocation returns transition desc when _type is transition"() {
    given:
    def driver = DriverLoader.loadDriver()
    new TestHarness(driver)

    expect:
    driver.getCurrentLocation([private:false, _type:"transition", desc:"Home"]) == "Home"
  }

  def "getCurrentLocation filters +follow regions and joins names"() {
    given:
    def driver = DriverLoader.loadDriver()
    new TestHarness(driver)
    def data = [private:false, inregions:["+follow-1","Work","+special","+foo","Gym"]]

    expect:
    driver.getCurrentLocation(data) == "Work,Gym"
  }

  def "getCurrentLocation falls back to streetAddress when not in regions"() {
    given:
    def driver = DriverLoader.loadDriver()
    new TestHarness(driver)
    def data = [private:false, streetAddress:"123 Main St"]

    expect:
    driver.getCurrentLocation(data) == "123 Main St"
  }

  def "createTransitionEvent sets events and notifies parent when not duplicate"() {
    given:
    def driver = DriverLoader.loadDriver()
    def h = new TestHarness(driver)
    h.attr.transitionDirection = "departed"  // ensure mismatch so not duplicate
    h.attr.transitionRegion = "Other"

    when:
    driver.createTransitionEvent("Home", "enter", 20000)

    then:
    h.attr.transitionRegion == "Home"
    h.attr.transitionDirection == "arrived"
    h.attr.sinceTime == 20000
    h.attr.transitionTime instanceof String
    h.logs.find { it.startsWith("DESC:") } // description logged
  }

  def "createTransitionEvent skips duplicate transition updates"() {
    given:
    def driver = DriverLoader.loadDriver()
    def h = new TestHarness(driver)
    // Set device current values to match incoming (duplicate)
    h.attr.transitionDirection = "arrived"
    h.attr.transitionRegion = "Home"

    when:
    driver.createTransitionEvent("Home", "enter", 20000)

    then:
    // No new sinceTime set (would equal incoming); check log contains skip message
    h.logs.find { it.contains("Skipping duplicate transition event") }
  }

  def "createPresenceEvent updates presence and sinceTime when state changes"() {
    given:
    def driver = DriverLoader.loadDriver()
    def h = new TestHarness(driver)
    h.attr.presence = "not present"

    when:
    driver.createPresenceEvent("present", 11111)

    then:
    h.attr.presence == "present"
    h.attr.sinceTime == 11111
    h.attr.since instanceof String
    h.logs.find { it.startsWith("DESC:") }
  }

  def "createPresenceEvent logs and skips when presence unchanged"() {
    given:
    def driver = DriverLoader.loadDriver()
    def h = new TestHarness(driver)
    h.attr.presence = "present"

    when:
    driver.createPresenceEvent("present", 22222)

    then:
    h.logs.find { it.contains("Skipping duplicate presence event") }
  }

  def "updateSinceTime sets sinceTime numeric and formatted since string"() {
    given:
    def driver = DriverLoader.loadDriver()
    def h = new TestHarness(driver)

    when:
    driver.updateSinceTime(33333)

    then:
    h.attr.sinceTime == 33333
    (h.attr.since as String).contains(" ")
  }

  def "generateLocationEvent initializes state, updates attributes, and returns true"() {
    given:
    def driver = Spy(DriverLoader.loadDriver())
    def h = new TestHarness(driver)
    def member = [name:"Alice"]
    def data = [
      private:false, _type:"location", desc:"Office", event:null,
      currentDistanceFromHome: 2.5, vel: 10, tst: 44444, memberAtHome: false,
      streetAddress: "456 Oak St"
    ]

    when:
    def result = driver.generateLocationEvent(member, "MyHome", data)

    then:
    result
    h.state.driverVersion == "1.8.7"
    h.state.memberName == "Alice"
    h.state.homeName == "MyHome"
    1 * driver.updateAttributes(data)
    1 * driver.updateAdditionalAttributes(member)
    h.attr.lastLocationtime instanceof String
    h.attr.location in ["Office","456 Oak St"] // depending on inregions logic
  }

  def "generateLocationEvent schedules transition update when within deadband"() {
    given:
    def driver = DriverLoader.loadDriver()
    def h = new TestHarness(driver)
    h.state.transitionDeadband = 55560
    h.attr.transitionRegion = "Park"

    def data = [
      private:false, _type:"location", desc:"Park", event:null,
      currentDistanceFromHome: 1, vel: 0, tst: 55500, memberAtHome: true,
      streetAddress:"789 Pine"
    ]

    when:
    def result = driver.generateLocationEvent([name:"Bob"], "Home", data)

    then:
    result
    h.state.pendingTransitionUpdateData instanceof Map
    h.scheduled.find { it.op == 'unschedule' }
    h.scheduled.find { it.op == 'runIn' && it.after == (h.state.transitionDeadband - data.tst) }
  }

  def "generateLocationEvent processes pending then current when receiving transition type"() {
    given:
    def driver = Spy(DriverLoader.loadDriver())
    def h = new TestHarness(driver)
    h.state.pendingTransitionUpdateData = [desc:"Old", event:"leave", _type:"transition", currentDistanceFromHome: 0, vel:0, tst: 10, memberPresence:"not present", memberAtHome:false, currentLocation:"Old"]
    def data = [
      private:false, _type:"transition", desc:"Home", event:"enter",
      currentDistanceFromHome: 0, vel: 0, tst: 66666, memberAtHome: true
    ]

    when:
    driver.generateLocationEvent([name:"Carl"], "Home", data)

    then:
    1 * driver.processScheduledTransitionEvent()
    1 * driver.processLocationEvent(_ as Map)
  }

  def "generateLocationEvent corrects presence if missed and outside deadband"() {
    given:
    def driver = Spy(DriverLoader.loadDriver())
    def h = new TestHarness(driver)
    h.state.transitionDeadband = 100
    h.state.homeName = "Home"
    h.attr.presence = "not present"

    def data = [
      private:false, _type:"location", desc:"Home", event:null,
      currentDistanceFromHome: 0, vel: 0, tst: 200, memberAtHome: true
    ]

    when:
    driver.generateLocationEvent([name:"Dana"], "Home", data)

    then:
    1 * driver.createTransitionEvent("Home", "enter", 200)
    1 * driver.createPresenceEvent("present", 200)
  }

  def "processScheduledTransitionEvent cancels schedule, processes pending once, and clears pending state"() {
    given:
    def driver = Spy(DriverLoader.loadDriver())
    def h = new TestHarness(driver)
    h.state.pendingTransitionUpdateData = [desc:"Gym", event:null, _type:"location", currentDistanceFromHome: 3, vel:0, tst: 777, memberPresence:"not present", memberAtHome:false, currentLocation:"Gym"]

    when:
    driver.processScheduledTransitionEvent()

    then:
    h.scheduled.find { it.op == 'unschedule' }
    1 * driver.processLocationEvent(_ as Map)
    h.state.pendingTransitionUpdateData == ""
  }

  def "processLocationEvent handles transition type and updates metrics"() {
    given:
    def driver = Spy(DriverLoader.loadDriver())
    def h = new TestHarness(driver)
    h.state.homeName = "Home"
    def data = [desc:"Home", event:"enter", _type:"transition", currentDistanceFromHome: 1.25, vel: 12.7, tst: 888, memberPresence:"present", memberAtHome:true, currentLocation:"Home"]

    when:
    driver.processLocationEvent(data)

    then:
    1 * driver.createTransitionEvent("Home", "enter", 888)
    h.attr.distanceFromHome == "1.25"
    h.attr.lastSpeed == 12 // toInteger() of "12.7" becomes 12 after displayKmMiVal stub returns string; our stub returns "12.7" -> toInteger() == 12
    h.attr.location == "Home"
    // presence event triggered because state.homeName == desc
    1 * driver.createPresenceEvent("present", 888)
  }

  def "processLocationEvent updates since time when location changed (non-transition)"() {
    given:
    def driver = Spy(DriverLoader.loadDriver())
    def h = new TestHarness(driver)
    h.attr.location = "Old"
    def data = [desc:"Elsewhere", event:null, _type:"location", currentDistanceFromHome: 2, vel: null, tst: 999, memberPresence:"not present", memberAtHome:false, currentLocation:"NewPlace"]

    when:
    driver.processLocationEvent(data)

    then:
    0 * driver.createTransitionEvent(_,_,_)
    1 * driver.updateSinceTime(999)
    h.attr.distanceFromHome == "2"
    h.attr.lastSpeed == 0
    h.attr.location == "NewPlace"
  }
}