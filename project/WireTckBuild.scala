import sbt._
import sjsonnew.shaded.scalajson.ast.unsafe._
import sjsonnew.support.scalajson.unsafe.Parser

/** Independent report reconciliation: a test runner exit code alone is not coverage evidence. */
object WireTckBuild {
  def revision(root: File): String = {
    import scala.sys.process._
    val head = Process(Seq("git", "rev-parse", "HEAD"), root).!!.trim
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val sources = Vector("wire-testkit", "wire-tck", "wire-tck-compare")
      .flatMap(module => (root / module ** "*.scala").get)
      .filterNot(_.getPath.contains("/target/"))
    val specification = (root / "docs/wire-tck" ** "*.tsv").get :+
      (root / "docs/wire-tck/baseline.md")
    (sources ++ specification :+ (root / "build.sbt") :+ (root / "project" / "WireTckBuild.scala") :+
      (root / "project" / "Http4sPlugin.scala"))
      .sortBy(_.getPath)
      .foreach { source =>
        digest.update(IO.relativize(root, source).get.getBytes("UTF-8"))
        digest.update(IO.readBytes(source))
      }
    head + ":" + digest.digest().map(b => f"${b & 0xff}%02x").mkString
  }

  def inventoryRows(root: File): Seq[String] =
    (root / "docs/wire-tck/inventory" ** "*.tsv").get
      .sortBy(_.getName)
      .flatMap(file => IO.readLines(file).drop(1))
      .toVector

  // BuildInfo repeats values in Scaladoc. Raw grammar examples can contain
  // comment delimiters; encode the exact UTF-8 bytes instead of rewriting them.
  def encodeRows(rows: Seq[String]): Seq[String] =
    rows.map(row => java.util.Base64.getEncoder.encodeToString(row.getBytes("UTF-8")))

  def checkSources(root: File): Unit = {
    val files = Vector("wire-testkit", "wire-tck", "wire-tck-compare")
      .flatMap(module => (root / module ** "*.scala").get)
      .filterNot(_.getPath.contains("/target/"))
      .filterNot(_.getName == "WireSuite.scala")
    val forbidden =
      "\\.(ignore|pending|flaky|only)\\b|@(?:munit\\.)?IgnoreSuite|\\bmunitIgnore\\b|\\bassume\\s*\\(".r
    // Protocol bytes and stable IDs can legitimately contain words such as ".ignore".
    // This source check screens ordinary annotations; runtime event reconciliation also
    // rejects every suppressed comparison case, including indirect suppression.
    val literalsAndComments =
      "(?s)\"\"\".*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])'|//[^\\r\\n]*|/\\*.*?\\*/".r
    val violations = files.filter { file =>
      val code = literalsAndComments.replaceAllIn(IO.read(file), " ")
      forbidden.findFirstIn(code).nonEmpty
    }
    require(
      violations.isEmpty,
      s"Local test suppression is forbidden: ${violations.mkString(", ")}",
    )
    val tracking = ignoreRows(root).map { row =>
      val columns = row.split("\t", -1)
      require(columns.length == 6 && columns.forall(_.nonEmpty), s"Invalid ignore row: $row")
      columns(5)
    }
    tracking.foreach { reference =>
      val parts = reference.split("#", 2)
      require(parts.length == 2 && (root / parts(0)).isFile, s"Missing tracking record: $reference")
      val headings = IO.readLines(root / parts(0)).filter(_.startsWith("## ")).map(_.drop(3))
      require(headings.contains(parts(1)), s"Missing tracking anchor: $reference")
    }
  }

  def ignoreRows(root: File): Vector[String] =
    IO.readLines(root / "docs/wire-tck/ignores.tsv").drop(1).toVector

  private val comparisonName =
    "(org\\.http4s\\.wire\\.(Compare(?:Ember|Netty)Http[12](?:Server|Client)Suite))(?:\\.(.+))?".r

  private def comparisonIdentity(detail: sbt.testing.Event): Option[(String, String)] =
    detail.fullyQualifiedName match {
      case comparisonName(qualifiedSuite, suite, namedCase) =>
        val selected = detail.selector match {
          case selector: sbt.testing.TestSelector => selector.testName
          case selector: sbt.testing.NestedTestSelector
              if selector.suiteId == qualifiedSuite || selector.suiteId == suite =>
            selector.testName
          case _ => ""
        }
        val id = selected.stripPrefix(qualifiedSuite + ".").stripPrefix(suite + ".")
        // Retain an anonymous event on conflicting or absent selectors so that
        // reconciliation fails, even when aggregate pass/failure counts agree.
        Some(suite -> (if (namedCase == null || namedCase == id) id else ""))
      case _ => None
    }

  /** SuiteResult drops selectors, so retain runner events independently of case reports. */
  def eventListener(root: File, version: String, platform: String): TestReportListener =
    new TestReportListener {
      def startGroup(name: String): Unit = ()
      def endGroup(name: String, error: Throwable): Unit = ()
      def endGroup(name: String, result: TestResult): Unit = ()
      def testEvent(event: TestEvent): Unit = synchronized {
        event.detail.foreach { detail =>
          comparisonIdentity(detail).foreach { case (suite, id) =>
            val directory = root / "target" / "wire-tck-reports" / version /
              "comparison" / platform / suite
            IO.createDirectory(directory)
            IO.append(directory / "munit-events.tsv", s"$id\t${detail.status}\n")
          }
        }
      }
    }

  private val adapterSuite = "org.http4s.wire.EmberTargetSuite"

  private def adapterEventsFile(root: File, version: String, platform: String): File =
    root / "target" / "wire-tck-reports" / version / "comparison" / "harness" /
      s"$platform-EmberTargetSuite.tsv"

  /** testOnly returns Unit; record actual framework events rather than treating discovery as execution. */
  def adapterListener(root: File, version: String, platform: String): TestReportListener =
    new TestReportListener {
      def startGroup(name: String): Unit = ()
      def endGroup(name: String, error: Throwable): Unit = ()
      def endGroup(name: String, result: TestResult): Unit = ()
      def testEvent(event: TestEvent): Unit = synchronized {
        event.detail.foreach { detail =>
          val name = detail.fullyQualifiedName
          if (name == adapterSuite || name.startsWith(adapterSuite + ".")) {
            val selected = detail.selector match {
              case selector: sbt.testing.TestSelector => selector.testName
              case selector: sbt.testing.NestedTestSelector if selector.suiteId == adapterSuite =>
                selector.testName
              case _ => ""
            }
            val id = selected.stripPrefix(adapterSuite + ".")
            val consistent = name == adapterSuite || name == adapterSuite + "." + id
            val encoded = java.util.Base64.getEncoder.encodeToString(
              (if (consistent) id else "").getBytes("UTF-8")
            )
            val file = adapterEventsFile(root, version, platform)
            IO.createDirectory(file.getParentFile)
            IO.append(file, s"$encoded\t${detail.status}\n")
          }
        }
      }
    }

  def adapterEvents(root: File, version: String, platform: String): Vector[(String, String)] = {
    val file = adapterEventsFile(root, version, platform)
    if (!file.isFile) Vector.empty
    else
      IO.readLines(file).toVector.map { line =>
        line.split("\t", -1).toVector match {
          case Vector(id, status) => id -> status
          case _ => "" -> "Invalid"
        }
      }
  }

  private def expectedEvent(status: String): String = status match {
    case "pass" => "Success"
    // WireSuite deliberately reports each non-success through MUnit's assertion API.
    case "fail" | "inconclusive" | "error" => "Failure"
    case _ => "Invalid"
  }

  private def outcomeProblems(
      outcomes: Map[String, String],
      counts: (Int, Int, Int),
      events: Vector[(String, String)],
  ): Vector[String] = {
    val problems = Vector.newBuilder[String]
    val expected = outcomes.map { case (id, status) => id -> expectedEvent(status) }
    val categories = Vector("Success", "Failure", "Error")
    val expectedCounts = categories.map(category => expected.values.count(_ == category))
    val actualCounts = Vector(counts._1, counts._2, counts._3)
    if (expectedCounts != actualCounts || expected.values.exists(_ == "Invalid"))
      problems += s"MUnit outcome categories disagree: expected=$expectedCounts actual=$actualCounts"
    if (
      events.size != actualCounts.sum || categories
        .map(c => events.count(_._2 == c)) != actualCounts
    )
      problems += "MUnit event evidence does not reconcile with runner counts"
    val identified = events.filter(_._1.nonEmpty)
    if (identified.size != events.size)
      problems += "Anonymous MUnit events cannot establish per-case outcomes"
    if (identified.map(_._1).distinct.size != identified.size)
      problems += "Duplicate MUnit case identities"
    identified.foreach { case (id, status) =>
      if (!expected.get(id).contains(status))
        problems += s"MUnit case outcome disagrees: $id/$status"
    }
    if (identified.map(_._1).toSet != outcomes.keySet)
      problems += "MUnit case identities do not reconcile with manifest"
    problems.result()
  }

  /** Small build-native mutation checks: disagreement is data, fabricated evidence is fatal. */
  def checkReconciliation(): Unit = {
    checkTrackingReferences()
    checkEvidence()
    val outcomes = Map("one" -> "pass", "two" -> "fail", "three" -> "inconclusive")
    val events = Vector("one" -> "Success", "two" -> "Failure", "three" -> "Failure")
    require(
      outcomeProblems(outcomes, (1, 2, 0), events).isEmpty,
      "Protocol disagreements must be allowed",
    )
    require(
      outcomeProblems(outcomes, (3, 0, 0), events).nonEmpty,
      "Pass/failure inversion must block",
    )
    require(outcomeProblems(Map("one" -> "pass"), (0, 1, 0), Vector("one" -> "Failure")).nonEmpty)
    require(outcomeProblems(Map("one" -> "fail"), (1, 0, 0), Vector("one" -> "Success")).nonEmpty)
    require(
      outcomeProblems(
        outcomes,
        (1, 2, 0),
        events.map {
          case ("one", _) => "one" -> "Failure"
          case ("two", _) => "two" -> "Success"
          case event => event
        },
      ).nonEmpty,
      "Equal-count case inversions must block",
    )
    require(
      outcomeProblems(outcomes, (1, 2, 0), events.updated(0, "unknown" -> "Success")).nonEmpty
    )
    require(outcomeProblems(outcomes, (1, 2, 0), events.take(2)).nonEmpty)
    require(outcomeProblems(outcomes, (1, 1, 1), events.updated(2, "three" -> "Error")).nonEmpty)
    require(
      outcomeProblems(outcomes, (1, 2, 0), events.map(e => "" -> e._2)).nonEmpty,
      "Anonymous events cannot establish per-case outcomes",
    )
    require(
      outcomeProblems(outcomes, (1, 2, 0), events.updated(0, "" -> "Success")).nonEmpty,
      "Partially anonymous events must block",
    )
    require(
      outcomeProblems(
        outcomes,
        (1, 2, 0),
        Vector("" -> "Failure", "" -> "Success", "" -> "Failure"),
      ).nonEmpty,
      "Anonymous equal-count case inversions must block",
    )
    require(
      outcomeProblems(outcomes, (1, 2, 0), events.updated(2, "two" -> "Failure")).nonEmpty,
      "Duplicate identities cannot replace a missing case",
    )
    checkEventListener()
    val passed = new SuiteResult(TestResult.Passed, 1, 0, 0, 0, 0, 0, 0)
    val failed = new SuiteResult(TestResult.Failed, 0, 1, 0, 0, 0, 0, 0)
    val suppressed = new SuiteResult(TestResult.Passed, 0, 0, 0, 0, 1, 0, 0)
    def kit(result: SuiteResult): Vector[(String, Result[Tests.Output], Set[String])] =
      Vector(
        (
          "synthetic/testkit",
          Value(
            Tests.Output(result.result, Map("OracleChallengeSuite" -> result), Nil)
          ),
          Set("OracleChallengeSuite"),
        )
      )
    require(
      harnessProblems(
        kit(passed),
        Vector(("adapter", Value(()), Vector("test" -> "Success"))),
      ).isEmpty
    )
    require(harnessProblems(kit(failed), Vector.empty).nonEmpty, "Harness failure must block")
    require(
      harnessProblems(kit(suppressed), Vector.empty).nonEmpty,
      "Harness suppression must block",
    )
    require(harnessProblems(Vector.empty, Vector(("adapter", Value(()), Vector.empty))).nonEmpty)
    val zero = new SuiteResult(TestResult.Passed, 0, 0, 0, 0, 0, 0, 0)
    require(harnessProblems(kit(zero), Vector.empty).nonEmpty, "Zero-test harness must block")
    def comparisonHarness(result: SuiteResult): Tests.Output =
      Tests.Output(TestResult.Failed, Map("org.http4s.wire.NettyTargetSuite" -> result), Nil)
    require(comparisonHarnessProblems("jvm", comparisonHarness(passed)).isEmpty)
    require(comparisonHarnessProblems("jvm", comparisonHarness(zero)).nonEmpty)
    require(comparisonHarnessProblems("jvm", comparisonHarness(suppressed)).nonEmpty)
    require(
      comparisonHarnessProblems("jvm", Tests.Output(TestResult.Passed, Map.empty, Nil)).nonEmpty
    )
    require(
      comparisonHarnessProblems("js", Tests.Output(TestResult.Passed, Map.empty, Nil)).isEmpty
    )
    val partiallyFiltered = Vector(
      (
        "filtered",
        Value(Tests.Output(TestResult.Passed, Map("one" -> passed), Nil)),
        Set("one", "two"),
      )
    )
    require(
      harnessProblems(partiallyFiltered, Vector.empty).nonEmpty,
      "A filtered harness suite must block even when another suite runs",
    )
    require(
      harnessProblems(
        Vector(
          (
            "filtered",
            Value(Tests.Output(TestResult.Passed, Map.empty, Nil)),
            Set("OracleChallengeSuite"),
          )
        ),
        Vector.empty,
      ).nonEmpty
    )
    Vector("Ignored", "Skipped", "Pending", "Canceled", "Failure", "Error").foreach { status =>
      require(
        harnessProblems(
          Vector.empty,
          Vector(("adapter", Value(()), Vector("test" -> status))),
        ).nonEmpty
      )
    }
    require(
      harnessProblems(
        Vector.empty,
        Vector(("adapter", Value(()), Vector("" -> "Success"))),
      ).nonEmpty
    )
    require(
      harnessProblems(
        Vector.empty,
        Vector(("adapter", Value(()), Vector.fill(2)("test" -> "Success"))),
      ).nonEmpty
    )
  }

  private def checkEvidence(): Unit = {
    val root = file("evidence")
    val suites = for {
      protocol <- Vector("Http1", "Http2")
      role <- Vector("Server", "Client")
    } yield s"CompareEmber$protocol${role}Suite"
    val passed = new SuiteResult(TestResult.Passed, 1, 0, 0, 0, 0, 0, 0)
    val results = Vector(
      "js" -> Value(
        Tests.Output(
          TestResult.Passed,
          suites.map(suite => s"org.http4s.wire.$suite" -> passed).toMap,
          Nil,
        )
      )
    )
    val files = suites.flatMap { suite =>
      val directory = root / "js" / suite
      val id = suite + "-case"
      Vector(
        directory / "manifest.tsv" -> s"$id\tsource",
        directory / "complete" -> "",
        directory / s"$id.outcome" -> s"$id\tsource\tpass",
        directory / "munit-events.tsv" -> s"$id\tSuccess",
        directory / s"$id.wire.txt" -> "",
        directory / s"$id.json" -> s"""{"case":"$id","target":"ember","platform":"js","outcome":"pass","targetVersion":"1","runtime":"test","scalaVersion":"2.13","catalogRevision":"revision","harnessSettings":"test","source":"source","transcript":"$id.wire.txt","settings":{},"references":[{}]}""",
      )
    }.toMap
    val valid = reconcileEvidence(root, results, Evidence(files))
    require(valid.errors.isEmpty, valid.errors.mkString("\n"))
    require(valid.counts == Map("pass" -> 4))
    val suite = suites.head
    val json = root / "js" / suite / s"$suite-case.json"
    val malformed = reconcileEvidence(root, results, Evidence(files.updated(json, "{")))
    require(malformed.errors.exists(_.startsWith("Invalid JSON evidence")))
    val missing = reconcileEvidence(root, results, Evidence(files - json))
    require(missing.errors.exists(_.startsWith("Missing evidence")))
    val mismatch = reconcileEvidence(
      root,
      results,
      Evidence(files.updated(json, files(json).replace("revision", "different"))),
    )
    require(mismatch.errors.exists(_.startsWith("Catalog revisions differ")))
  }

  private def checkTrackingReferences(): Unit =
    IO.withTemporaryDirectory { root =>
      val registry = root / "docs/wire-tck/ignores.tsv"
      val record = root / "tracking.md"
      def write(reference: String): Unit =
        IO.write(
          registry,
          s"id\tplatforms\tobserved\trevision\tstatus\ttracking\ncase\tjvm\tdiagnostic\trevision\tconfirmed\t$reference\n",
        )
      write("tracking.md#record")
      IO.write(record, "## record\n")
      checkSources(root)
      def rejects(expected: String): Boolean =
        scala.util.Try(checkSources(root)).failed.toOption.exists(_.getMessage.contains(expected))
      write("tracking.md#missing")
      require(rejects("Missing tracking anchor"))
      write("missing.md#record")
      require(rejects("Missing tracking record"))
      IO.write(registry, "header\ninvalid\n")
      require(rejects("Invalid ignore row"))
    }

  private def checkEventListener(): Unit = {
    val suite = "CompareEmberHttp1ServerSuite"
    val qualifiedSuite = s"org.http4s.wire.$suite"
    val id = "h1.framing.content-length"
    def event(name: String, selected: sbt.testing.Selector): sbt.testing.Event =
      new sbt.testing.Event {
        def fullyQualifiedName(): String = name
        def selector(): sbt.testing.Selector = selected
        def status(): sbt.testing.Status = sbt.testing.Status.Success
        def duration(): Long = 0L
        def throwable(): sbt.testing.OptionalThrowable = new sbt.testing.OptionalThrowable
        def fingerprint(): sbt.testing.Fingerprint = new sbt.testing.SubclassFingerprint {
          def isModule(): Boolean = false
          def superclassName(): String = "munit.Suite"
          def requireNoArgConstructor(): Boolean = true
        }
      }
    IO.withTemporaryDirectory { root =>
      val listener = eventListener(root, "self-check", "jvm")
      val names = Vector(qualifiedSuite, s"$qualifiedSuite.$id")
      val selectors = Vector(id, s"$suite.$id", s"$qualifiedSuite.$id")
        .flatMap(name =>
          Vector(
            new sbt.testing.TestSelector(name),
            new sbt.testing.NestedTestSelector(qualifiedSuite, name),
          )
        )
      // MUnit 1.3.6 uses the fully qualified suite.case for both names.
      for {
        name <- names
        selector <- selectors
      }
        listener.testEvent(TestEvent(Vector(event(name, selector))))
      listener.testEvent(
        TestEvent(Vector(event("org.http4s.wire.CatalogSuite", new sbt.testing.TestSelector(id))))
      )
      val eventFile = root / "target/wire-tck-reports/self-check/comparison/jvm" / suite /
        "munit-events.tsv"
      require(eventFile.isFile, "Fully qualified MUnit events must reach the suite report")
      require(
        IO.readLines(eventFile).toVector == Vector.fill(names.size * selectors.size)(
          s"$id\tSuccess"
        ),
        "MUnit names must normalize to exact dotted stable case IDs",
      )
      val adapter = adapterListener(root, "self-check", "jvm")
      require(
        adapterEvents(root, "self-check", "jvm").isEmpty,
        "Filtered adapter suite must have no execution evidence",
      )
      adapter.testEvent(
        TestEvent(
          Vector(
            event(adapterSuite + ".first", new sbt.testing.TestSelector(adapterSuite + ".first"))
          )
        )
      )
      adapter.testEvent(
        TestEvent(Vector(event(adapterSuite, new sbt.testing.TestSelector("second"))))
      )
      val executed = adapterEvents(root, "self-check", "jvm")
      require(executed.size == 2 && executed.map(_._1).distinct.size == 2)
      require(harnessProblems(Vector.empty, Vector(("adapter", Value(()), executed))).isEmpty)
      adapter.testEvent(TestEvent(Vector(event(adapterSuite, new sbt.testing.SuiteSelector))))
      require(
        harnessProblems(
          Vector.empty,
          Vector(("adapter", Value(()), adapterEvents(root, "self-check", "jvm"))),
        ).nonEmpty
      )
      listener.testEvent(TestEvent(Vector(event(qualifiedSuite, new sbt.testing.SuiteSelector))))
      listener.testEvent(
        TestEvent(Vector(event(s"$qualifiedSuite.$id", new sbt.testing.TestSelector("different"))))
      )
      require(
        IO.readLines(eventFile).takeRight(2).toVector == Vector("\tSuccess", "\tSuccess"),
        "Anonymous or conflicting identities must remain unreconcilable evidence",
      )
    }
  }

  def harnessProblems(
      tests: Vector[(String, Result[Tests.Output], Set[String])],
      adapters: Vector[(String, Result[Unit], Vector[(String, String)])],
  ): Vector[String] = {
    val problems = Vector.newBuilder[String]
    tests.foreach {
      case (label, Value(output), expectedSuites) =>
        if (expectedSuites.isEmpty || output.events.keySet != expectedSuites)
          problems += s"Harness suite execution does not match discovery: $label expected=$expectedSuites actual=${output.events.keySet}"
        if (output.overall != TestResult.Passed || output.events.isEmpty)
          problems += s"Harness tests did not pass: $label"
        output.events.foreach { case (suite, result) =>
          if (
            result.result != TestResult.Passed || result.passedCount == 0 ||
            result.ignoredCount + result.pendingCount + result.skippedCount + result.canceledCount > 0
          )
            problems += s"Harness test failure or suppression: $label/$suite"
        }
      case (label, Inc(error), _) => problems += s"Harness runner failed: $label/$error"
    }
    adapters.foreach {
      case (label, Value(_), events) =>
        if (events.isEmpty) problems += s"No adapter tests executed: $label"
        if (events.exists { case (id, status) => id.isEmpty || status != "Success" })
          problems += s"Adapter test failure, suppression or anonymous execution: $label"
        if (events.map(_._1).distinct.size != events.size)
          problems += s"Duplicate adapter test execution evidence: $label"
      case (label, Inc(error), _) => problems += s"Adapter tests failed: $label/$error"
    }
    problems.result()
  }

  private def comparisonHarnessProblems(platform: String, output: Tests.Output): Vector[String] = {
    val actual = output.events.filterNot { case (suite, _) =>
      suite.split('.').last.startsWith("Compare")
    }
    val expected =
      if (platform == "jvm") Set("org.http4s.wire.NettyTargetSuite") else Set.empty[String]
    val missing =
      if (actual.keySet == expected) Vector.empty
      else
        Vector(
          s"Comparison harness suites do not match: $platform expected=$expected actual=${actual.keySet}"
        )
    missing ++ actual.toVector.collect {
      case (suite, result)
          if result.result != TestResult.Passed || result.passedCount == 0 || result.ignoredCount + result.pendingCount + result.skippedCount + result.canceledCount > 0 =>
        s"Comparison harness failure, suppression or nonexecution: $platform/$suite"
    }
  }

  final case class Reconciled(
      counts: Map[String, Int],
      cells: Map[(String, String), String],
      errors: Vector[String],
  ) {
    def summary: String =
      counts.toVector.sortBy(_._1).map { case (key, count) => s"$key=$count" }.mkString(", ")

    def text: String = s"$summary\n" + errors.mkString("\n")

    def markdown: String = {
      val columns = Vector("jvm/Ember", "jvm/Netty", "js/Ember", "native/Ember")
      val rows = cells.keys.map(_._1).toVector.distinct.sorted.map { id =>
        (Vector(id) ++ columns.map(column => cells.getOrElse(id -> column, "MISSING")))
          .mkString("| ", " | ", " |")
      }

      s"# HTTP wire comparison\n\n$summary\n\n" +
        "| Case | JVM Ember | JVM Netty | JS Ember | Native Ember |\n|---|---|---|---|---|\n" +
        rows.mkString("\n") + "\n\n" + errors.mkString("\n")
    }
  }

  /** A snapshot makes reconciliation deterministic and independent of filesystem operations. */
  final case class Evidence(files: Map[File, String]) {
    def contains(file: File): Boolean = files.contains(file)
    def apply(file: File): String = files(file)
  }

  def reconcileEvidence(
      root: File,
      results: Vector[(String, Result[Tests.Output])],
      evidence: Evidence,
      harnessErrors: Vector[String] = Vector.empty,
  ): Reconciled = {
    val problems = Vector.newBuilder[String]
    problems ++= harnessErrors
    var counts = Map.empty[String, Int].withDefaultValue(0)
    var catalogs = Map.empty[String, Set[String]]
    var revisions = Set.empty[String]
    var cells = Map.empty[(String, String), String]
    results.foreach { case (platform, result) =>
      result match {
        case Inc(error) => problems += s"$platform runner failed: $error"
        case Value(output) if output.overall == TestResult.Error =>
          problems += s"$platform test runner infrastructure error"
        case Value(output) =>
          problems ++= comparisonHarnessProblems(platform, output)
          output.events.foreach { case (suite, result) =>
            if (!suite.split('.').last.startsWith("Compare") && result.result != TestResult.Passed)
              problems += s"Harness test failure: $platform/$suite"
            if (
              result.ignoredCount + result.pendingCount + result.skippedCount + result.canceledCount > 0
            )
              problems += s"Suppressed or canceled comparison tests: $platform/$suite"
          }
      }
      val targets = if (platform == "jvm") Vector("Ember", "Netty") else Vector("Ember")
      for {
        target <- targets
        protocol <- Vector("Http1", "Http2")
        role <- Vector("Server", "Client")
      } {
        val suite = s"Compare$target$protocol${role}Suite"
        val directory = root / platform / suite
        val manifest = directory / "manifest.tsv"
        if (!evidence.contains(manifest)) problems += s"Missing manifest: $platform/$suite"
        else {
          val expected = evidence(manifest).linesIterator.toVector.filter(_.nonEmpty)
          val ids = expected.map(_.takeWhile(_ != '\t')).toSet
          val group = s"$protocol/$role"
          catalogs.get(group).foreach { previous =>
            if (previous != ids) problems += s"Catalog differs between targets: $platform/$suite"
          }
          catalogs = catalogs.updated(group, ids)
          if (expected.isEmpty || ids.size != expected.size)
            problems += s"Empty or duplicate manifest: $platform/$suite"
          result match {
            case Value(output) =>
              output.events.get(s"org.http4s.wire.$suite") match {
                case Some(events)
                    if events.passedCount + events.failureCount + events.errorCount == expected.size =>
                  ()
                case Some(events) =>
                  problems += s"MUnit results do not reconcile with manifest: $platform/$suite expected=${expected.size} passed=${events.passedCount} failed=${events.failureCount} errors=${events.errorCount}"
                case None => problems += s"Missing MUnit suite event: $platform/$suite"
              }
            case _ => ()
          }
          if (!evidence.contains(directory / "complete"))
            problems += s"Suite did not finalize: $platform/$suite"
          var outcomes = Map.empty[String, String]
          expected.foreach { entry =>
            val parts = entry.split("\t", -1)
            val file = directory / s"${parts(0)}.outcome"
            if (!evidence.contains(file)) problems += s"Unexercised case: $platform/$suite/$entry"
            else {
              val outcome = evidence(file).trim.split("\t", -1)
              if (outcome.length != 3 || outcome.take(2).mkString("\t") != entry)
                problems += s"Invalid outcome: $file"
              else {
                val status = outcome(2)
                outcomes = outcomes.updated(parts(0), status)
                cells = cells.updated(
                  parts(0) -> s"$platform/$target",
                  s"[$status]($platform/$suite/${parts(0)}.json)",
                )
                counts = counts.updated(status, counts(status) + 1)
                if (!Set("pass", "fail", "inconclusive")(status))
                  problems += s"Blocking $status: $platform/$suite/${parts(0)}"
                val json = directory / s"${parts(0)}.json"
                if (
                  !evidence.contains(json) ||
                  !evidence.contains(directory / s"${parts(0)}.wire.txt")
                )
                  problems += s"Missing evidence: $file"
                else
                  Parser.parseFromString(evidence(json)).toOption match {
                    case Some(JObject(fields)) =>
                      val strings = fields.collect { case JField(key, JString(value)) =>
                        key -> value
                      }.toMap
                      val identity = Map(
                        "case" -> parts(0),
                        "target" -> target.toLowerCase(java.util.Locale.ROOT),
                        "platform" -> platform,
                        "outcome" -> status,
                      )
                      if (
                        !identity.forall { case (key, value) => strings.get(key).contains(value) }
                      )
                        problems += s"JSON and outcome index disagree: $json"
                      if (!strings.get("transcript").contains(s"${parts(0)}.wire.txt"))
                        problems += s"Transcript reference does not match evidence: $json"
                      val required = Vector(
                        "targetVersion",
                        "runtime",
                        "scalaVersion",
                        "catalogRevision",
                        "harnessSettings",
                        "source",
                        "transcript",
                      )
                      if (!required.forall(key => strings.get(key).exists(_.nonEmpty)))
                        problems += s"Missing report metadata: $json"
                      if (
                        !fields.exists {
                          case JField("settings", JObject(values)) =>
                            values.forall {
                              case JField(key, JString(value)) => key.nonEmpty && value.nonEmpty
                              case _ => false
                            }
                          case _ => false
                        }
                      ) problems += s"Missing or invalid effective target settings: $json"
                      strings.get("catalogRevision").foreach(value => revisions += value)
                      if (
                        !fields.exists {
                          case JField("references", JArray(values)) => values.nonEmpty;
                          case _ => false
                        }
                      )
                        problems += s"Missing report references: $json"
                    case _ => problems += s"Invalid JSON evidence: $json"
                  }
              }
            }
          }
          result match {
            case Value(output) =>
              output.events.get(s"org.http4s.wire.$suite").foreach { events =>
                val eventFile = directory / "munit-events.tsv"
                val identities =
                  if (evidence.contains(eventFile))
                    evidence(eventFile).linesIterator.toVector.flatMap { line =>
                      line.split("\t", -1).toVector match {
                        case Vector(id, status) => Some(id -> status)
                        case _ => problems += s"Invalid MUnit event evidence: $eventFile"; None
                      }
                    }
                  else Vector.empty
                outcomeProblems(
                  outcomes,
                  (events.passedCount, events.failureCount, events.errorCount),
                  identities,
                )
                  .foreach(problem => problems += s"$platform/$suite: $problem")
              }
            case _ => ()
          }
        }
      }
    }
    if (revisions.size != 1)
      problems += s"Catalog revisions differ or are absent: ${revisions.mkString(", ")}"
    val errors = problems.result()
    Reconciled(counts, cells, errors)
  }

  def reconcile(
      root: File,
      results: Vector[(String, Result[Tests.Output])],
      log: Logger,
      harnessErrors: Vector[String] = Vector.empty,
  ): Unit = {
    val files = (root ** "*").get.filter(file => file.isFile && file.getName != "evidence.zip")
    // Wire transcripts are only checked for presence; don't retain their potentially large contents.
    val snapshot = Evidence(
      files
        .map(file =>
          file ->
            (if (file.getName.endsWith(".wire.txt")) "" else IO.read(file))
        )
        .toMap
    )
    val result = reconcileEvidence(root, results, snapshot, harnessErrors)
    val summary = result.summary
    val errors = result.errors
    IO.write(root / "summary.txt", result.text)
    IO.write(root / "report.md", result.markdown)
    val evidence = (root ** "*").get
      .filter(file => file.isFile && file.getName != "evidence.zip")
      .map(file => file -> IO.relativize(root, file).get)
    IO.zip(evidence, root / "evidence.zip", Some(315532800000L))
    log.info(s"Wire comparison: $summary; reports: $root")
    require(errors.isEmpty, errors.mkString("Wire comparison infrastructure failures:\n", "\n", ""))
  }
}
