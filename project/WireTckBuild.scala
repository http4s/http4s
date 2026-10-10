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
    (sources :+ (root / "build.sbt") :+ (root / "project" / "WireTckBuild.scala"))
      .sortBy(_.getPath).foreach { source =>
        digest.update(IO.relativize(root, source).get.getBytes("UTF-8"))
        digest.update(IO.readBytes(source))
      }
    head + ":" + digest.digest().map(b => f"${b & 0xff}%02x").mkString
  }

  def checkSources(root: File): Unit = {
    val files = Vector("wire-testkit", "wire-tck", "wire-tck-compare")
      .flatMap(module => (root / module ** "*.scala").get)
      .filterNot(_.getPath.contains("/target/"))
      .filterNot(_.getName == "WireSuite.scala")
    val forbidden = "\\.(ignore|pending|flaky|only)\\b|@(?:munit\\.)?IgnoreSuite|\\bmunitIgnore\\b|\\bassume\\s*\\(".r
    val violations = files.filter(f => forbidden.findFirstIn(IO.read(f)).nonEmpty)
    require(violations.isEmpty, s"Local test suppression is forbidden: ${violations.mkString(", ")}")
    val registry = IO.read(root / "wire-testkit/shared/src/main/scala/org/http4s/wire/IgnoreRegistry.scala")
    val tracking = "tracking = \"([^\"]+)\"".r.findAllMatchIn(registry).map(_.group(1)).toVector
    tracking.foreach { reference =>
      val parts = reference.split("#", 2)
      require(parts.length == 2 && (root / parts(0)).isFile, s"Missing tracking record: $reference")
      val headings = IO.readLines(root / parts(0)).filter(_.startsWith("## ")).map(_.drop(3))
      require(headings.contains(parts(1)), s"Missing tracking anchor: $reference")
    }
  }

  def reconcile(root: File, results: Vector[(String, Result[Tests.Output])], log: Logger): Unit = {
    val problems = Vector.newBuilder[String]
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
          output.events.foreach { case (suite, result) =>
            if (!suite.split('.').last.startsWith("Compare") && result.result != TestResult.Passed)
              problems += s"Harness test failure: $platform/$suite"
            if (result.ignoredCount + result.pendingCount + result.skippedCount + result.canceledCount > 0)
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
        if (!manifest.isFile) problems += s"Missing manifest: $platform/$suite"
        else {
          val expected = IO.readLines(manifest).filter(_.nonEmpty)
          val ids = expected.map(_.takeWhile(_ != '\t')).toSet
          val group = s"$protocol/$role"
          catalogs.get(group).foreach { previous =>
            if (previous != ids) problems += s"Catalog differs between targets: $platform/$suite"
          }
          catalogs = catalogs.updated(group, ids)
          if (expected.isEmpty || expected.distinct.size != expected.size)
            problems += s"Empty or duplicate manifest: $platform/$suite"
          result match {
            case Value(output) =>
              output.events.get(s"org.http4s.wire.$suite") match {
                case Some(events) if events.passedCount + events.failureCount + events.errorCount == expected.size => ()
                case _ => problems += s"MUnit results do not reconcile with manifest: $platform/$suite"
              }
            case _ => ()
          }
          if (!(directory / "complete").isFile) problems += s"Suite did not finalize: $platform/$suite"
          expected.foreach { entry =>
            val parts = entry.split("\t", -1)
            val file = directory / s"${parts(0)}.outcome"
            if (!file.isFile) problems += s"Unexercised case: $platform/$suite/$entry"
            else {
              val outcome = IO.read(file).trim.split("\t", -1)
              if (outcome.length != 3 || outcome.take(2).mkString("\t") != entry)
                problems += s"Invalid outcome: $file"
              else {
                val status = outcome(2)
                cells = cells.updated(parts(0) -> s"$platform/$target",
                  s"[$status]($platform/$suite/${parts(0)}.json)")
                counts = counts.updated(status, counts(status) + 1)
                if (!Set("pass", "fail", "inconclusive")(status))
                  problems += s"Blocking $status: $platform/$suite/${parts(0)}"
                val json = directory / s"${parts(0)}.json"
                if (!json.isFile ||
                    !(directory / s"${parts(0)}.wire.txt").isFile)
                  problems += s"Missing evidence: $file"
                else Parser.parseFromFile(json).toOption match {
                  case Some(JObject(fields)) =>
                    val strings = fields.collect { case JField(key, JString(value)) => key -> value }.toMap
                    val identity = Map("case" -> parts(0), "target" -> target.toLowerCase(java.util.Locale.ROOT),
                      "platform" -> platform, "outcome" -> status)
                    if (!identity.forall { case (key, value) => strings.get(key).contains(value) })
                      problems += s"JSON and outcome index disagree: $json"
                    val required = Vector("targetVersion", "runtime", "scalaVersion", "catalogRevision", "settings", "source", "transcript")
                    if (!required.forall(key => strings.get(key).exists(_.nonEmpty)))
                      problems += s"Missing report metadata: $json"
                    strings.get("catalogRevision").foreach(value => revisions += value)
                    if (!fields.exists { case JField("references", JArray(values)) => values.nonEmpty; case _ => false })
                      problems += s"Missing report references: $json"
                  case _ => problems += s"Invalid JSON evidence: $json"
                }
              }
            }
          }
        }
      }
    }
    if (revisions.size != 1) problems += s"Catalog revisions differ or are absent: ${revisions.mkString(", ")}"
    val errors = problems.result()
    val summary = counts.toVector.sortBy(_._1).map { case (key, count) => s"$key=$count" }.mkString(", ")
    IO.write(root / "summary.txt", s"$summary\n" + errors.mkString("\n"))
    val columns = Vector("jvm/Ember", "jvm/Netty", "js/Ember", "native/Ember")
    val rows = cells.keys.map(_._1).toVector.distinct.sorted.map { id =>
      (Vector(id) ++ columns.map(column => cells.getOrElse(id -> column, "MISSING"))).mkString("| ", " | ", " |")
    }
    IO.write(root / "report.md", s"# HTTP wire comparison\n\n$summary\n\n" +
      "| Case | JVM Ember | JVM Netty | JS Ember | Native Ember |\n|---|---|---|---|---|\n" +
      rows.mkString("\n") + "\n\n" + errors.mkString("\n"))
    log.info(s"Wire comparison: $summary; reports: $root")
    require(errors.isEmpty, errors.mkString("Wire comparison infrastructure failures:\n", "\n", ""))
  }
}
