package org.http4s.build

import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.SbtPgp.autoImport._
import com.typesafe.sbt.git.JGit
import com.typesafe.tools.mima.plugin.MimaPlugin, MimaPlugin.autoImport._
import org.eclipse.jgit.lib.Repository
import sbt._
import sbt.Keys._
import sbtrelease._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import scoverage.ScoverageKeys.{coverageEnabled,coverageHighlighting}
import scala.util.Properties.envOrNone
import verizon.build.RigPlugin
import verizon.build.RigPlugin._
import verizon.build.RigPlugin.autoImport._
import verizon.build.common._

object Http4sPlugin extends AutoPlugin {
  object autoImport {
    val http4sMimaVersion = settingKey[Option[String]]("Version to target for MiMa compatibility")
    val http4sPrimary = settingKey[Boolean]("Is this the primary build?")
    val http4sPublish = settingKey[Boolean]("Is this a publishing build?")
    val http4sMasterBranch = settingKey[Boolean]("Is this the master branch?")
    val http4sApiVersion = taskKey[(Int, Int)]("API version of http4s")
    val http4sJvmTarget = taskKey[String]("JVM target")
    val http4sBuildData = taskKey[Unit]("Export build metadata for Hugo")
  }
  import autoImport._

  override def trigger = allRequirements

  override def requires = RigPlugin && MimaPlugin

  override lazy val buildSettings = Seq(
    // Many steps only run on one build. We distinguish the primary build from
    // secondary builds by the Travis build number.
    http4sPrimary := sys.env.get("TRAVIS_JOB_NUMBER").fold(true)(_.endsWith(".1")),
    // Publishing to gh-pages and sonatype only done from select branches and
    // never from pull requests.
    http4sPublish := {
      sys.env.get("TRAVIS") == Some("true") &&
        sys.env.get("TRAVIS_PULL_REQUEST") == Some("false") &&
        sys.env.get("TRAVIS_REPO_SLUG") == Some("http4s/http4s") &&
        (sys.env.get("TRAVIS_BRANCH") match {
           case Some("master") => true
           case Some(branch) if branch.startsWith("release-") => true
           case _ => false
         })
    },
    http4sMasterBranch := sys.env.get("TRAVIS_BRANCH") == Some("master"),
    http4sApiVersion in ThisBuild := (version in ThisBuild).map {
      case VersionNumber(Seq(major, minor, _*), _, _) => (major.toInt, minor.toInt)
    }.value,
    coverageEnabled := isTravisBuild.value && http4sPrimary.value,
    coverageHighlighting := true,
    git.remoteRepo := "git@github.com:http4s/http4s.git"
  ) ++ signingSettings

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    scalaVersion := (sys.env.get("TRAVIS_SCALA_VERSION") orElse sys.env.get("SCALA_VERSION") getOrElse "2.12.3"),

    scalacOptions in Compile ++= Seq(
      "-Yno-adapted-args", // Curiously missing from RigPlugin
      "-Ypartial-unification" // Needed on 2.11 for Either, good idea in general
    ) ++ {
      // https://issues.scala-lang.org/browse/SI-8340
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 11 => Seq("-Ywarn-numeric-widen")
        case _ => Seq.empty
      }
    },

    http4sMimaVersion := {
      version.value match {
        case VersionNumber(Seq(major, minor, patch), _, _) if patch.toInt > 0 =>
          Some(s"${major}.${minor}.${patch.toInt - 1}")
        case _ =>
          None
      }
    },
    mimaFailOnProblem := http4sMimaVersion.value.isDefined,
    mimaPreviousArtifacts := (http4sMimaVersion.value map {
      organization.value % s"${moduleName.value}_${scalaBinaryVersion.value}" % _
    }).toSet,


    http4sBuildData := {
      val dest = target.value / "hugo-data" / "build.toml"
      val (major, minor) = http4sApiVersion.value

      val releases = latestPerMinorVersion(baseDirectory.value)
        .map { case ((major, minor), v) => s""""$major.$minor" = "${v.string}""""}
        .mkString("\n")

      // Would be more elegant if `[versions.http4s]` was nested, but then
      // the index lookups in `shortcodes/version.html` get complicated.
      val buildData: String =
        s"""
           |[versions]
           |"http4s.api" = "$major.$minor"
           |"http4s.current" = "${version.value}"
           |"http4s.doc" = "${docExampleVersion(version.value)}"
           |circe = "${circeJawn.revision}"
           |cryptobits = "${cryptobits.revision}"
           |"argonaut-shapeless_6.2" = "1.2.0-M6"
           |
           |[releases]
           |${releases}
         """.stripMargin

      IO.write(dest, buildData)
    }
  ) ++ releaseSettings

  val releaseSettings = Seq(
    // Reset a couple sbt-release defaults that rig changed
    releaseVersion := { ver => Version(ver).map(_.withoutQualifier.string).getOrElse(versionFormatError) },
    releaseTagName := s"v${if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value}",

    releaseProcess := {
      implicit class StepSyntax(val step: ReleaseStep) {
        def when(cond: Boolean) =
          if (cond) step else ReleaseStep(identity)
      }

      implicit class StateFCommand(val step: State => State) {
        def when(cond: Boolean) =
          StepSyntax(step).when(cond)
      }

      val release = !isSnapshot.value
      val publish = http4sPublish.value
      val primary = http4sPrimary.value
      val master = http4sMasterBranch.value

      Seq(
        // For debugging purposes
        releaseStepCommand("show core/version"),
        releaseStepCommand("show core/isSnapshot"),

        checkSnapshotDependencies.when(release),
        inquireVersions.when(release),
        setReleaseVersion.when(release),
        tagRelease.when(primary && release),
        runTestWithCoverage,
        releaseStepCommand("mimaReportBinaryIssues"),
        releaseStepCommand("docs/makeSite").when(primary),
        releaseStepCommand("website/makeSite").when(primary),
        openSonatypeRepo.when(publish && release),
        publishArtifacsWithoutInstrumentation.when(publish),
        releaseAndClose.when(publish && release),
        releaseStepCommand("docs/ghpagesPushSite").when(publish && primary),
        releaseStepCommand("website/ghpagesPushSite").when(publish && primary && master),
        setNextVersion.when(publish && primary && release),
        commitNextVersion.when(publish && primary && release),
        pushChanges.when(publish && primary && release),
        // We need a superfluous final step to ensure exit code
        // propagation from failed steps above.
        //
        // https://github.com/sbt/sbt-release/issues/95
        releaseStepCommand("show core/version")
      )
    }
  )

  val signingSettings = Seq(
    useGpg := false,
    usePgpKeyHex("42FAD8A85B13261D"),
    pgpPublicRing := baseDirectory.value / "project" / ".gnupg" / "pubring.gpg",
    pgpSecretRing := baseDirectory.value / "project" / ".gnupg" / "secring.gpg",
    pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray)
  )

  def extractApiVersion(version: String) = {
    val VersionExtractor = """(\d+)\.(\d+)\..*""".r
    version match {
      case VersionExtractor(major, minor) => (major.toInt, minor.toInt)
    }
  }

  def extractDocsPrefix(version: String) =
    extractApiVersion(version).productIterator.mkString("/v", ".", "")

  /**
   * @return the version we want to document, for example in tuts,
   * given the version being built.
   *
   * For snapshots after a stable release, return the previous stable
   * release.  For snapshots of 0.16.0 and 0.17.0, return the latest
   * milestone.  Otherwise, just return the current version.  Favors
   * scalaz-7.2 "a" versions for 0.15.x and 0.16.x.
   */
  def docExampleVersion(currentVersion: String) = {
    val MilestoneVersionExtractor = """(0).(16|17).(0)a?-SNAPSHOT""".r
    val latestMilestone = "M1"
    val VersionExtractor = """(\d+)\.(\d+)\.(\d+).*""".r
    currentVersion match {
      case MilestoneVersionExtractor(major, minor, patch) if minor.toInt == 16 =>
        s"${major.toInt}.${minor.toInt}.${patch.toInt}a-$latestMilestone" // scalaz-7.2 for 0.16.x
      case MilestoneVersionExtractor(major, minor, patch) =>
        s"${major.toInt}.${minor.toInt}.${patch.toInt}-$latestMilestone"
      case VersionExtractor(major, minor, patch) if minor.toInt == 15 =>
        s"${major.toInt}.${minor.toInt}.${patch.toInt - 1}a"              // scalaz-7.2 for 0.15.x
      case VersionExtractor(major, minor, patch) if patch.toInt > 0 =>
        s"${major.toInt}.${minor.toInt}.${patch.toInt - 1}"
      case _ =>
        currentVersion
    }
  }

  val macroParadiseSetting =
    libraryDependencies += compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.patch)

  def latestPerMinorVersion(file: File): Map[(Int, Int), Version] =
    JGit(file).tags.collect {
      case ref if ref.getName.startsWith("refs/tags/v") =>
        Version(ref.getName.substring("refs/tags/v".size))
    }.foldLeft(Map.empty[(Int, Int), Version]) {
      case (m, Some(v)) =>
        def toMinor(v: Version) = (v.major, v.subversions.headOption.getOrElse(0))
        def patch(v: Version) = v.subversions.drop(1).headOption.getOrElse(0)
        // M before RC before final
        def milestone(v: Version) = v.qualifier match {
          case Some(q) if q.startsWith("-M") => (0, q.substring(2).toInt)
          case Some(q) if q.startsWith("-RC") => (1, q.substring(3).toInt)
          case None => (2, 0)
        }
        val versionOrdering: Ordering[Version] =
          Ordering[(Int, (Int, Int))].on(v => (patch(v), milestone(v)))
        val key = toMinor(v)
        val max = m.get(key).fold(v) { v0 => versionOrdering.max(v, v0) }
        m.updated(key, max)
      case (m, None) => m
    }

  lazy val alpnBoot                         = "org.mortbay.jetty.alpn" %  "alpn-boot"                 % "8.1.11.v20170118"
  lazy val argonaut                         = "io.argonaut"            %% "argonaut"                  % "6.2"
  lazy val asyncHttpClient                  = "org.asynchttpclient"    %  "async-http-client"         % "2.0.37"
  lazy val blaze                            = "org.http4s"             %% "blaze-http"                % "0.12.9"
  lazy val catsKernelLaws                   = "org.typelevel"          %% "cats-kernel-laws"          % catsLaws.revision
  lazy val catsLaws                         = "org.typelevel"          %% "cats-laws"                 % "0.9.0"
  lazy val circeGeneric                     = "io.circe"               %% "circe-generic"             % circeJawn.revision
  lazy val circeJawn                        = "io.circe"               %% "circe-jawn"                % "0.8.0"
  lazy val circeLiteral                     = "io.circe"               %% "circe-literal"             % circeJawn.revision
  lazy val circeParser                      = "io.circe"               %% "circe-parser"              % circeJawn.revision
  lazy val cryptobits                       = "org.reactormonk"        %% "cryptobits"                % "1.1"
  lazy val discipline                       = "org.typelevel"          %% "discipline"                % "0.8"
  lazy val fs2Cats                          = "co.fs2"                 %% "fs2-cats"                  % "0.3.0"
  lazy val fs2Io                            = "co.fs2"                 %% "fs2-io"                    % "0.9.7"
  lazy val fs2ReactiveStreams               = "com.github.zainab-ali"  %% "fs2-reactive-streams"      % "0.1.1"
  lazy val gatlingTest                      = "io.gatling"             %  "gatling-test-framework"    % "2.2.5"
  lazy val gatlingHighCharts                = "io.gatling.highcharts"  %  "gatling-charts-highcharts" % gatlingTest.revision
  lazy val http4sWebsocket                  = "org.http4s"             %% "http4s-websocket"          % "0.2.0"
  lazy val javaxServletApi                  = "javax.servlet"          %  "javax.servlet-api"         % "3.1.0"
  lazy val jawnJson4s                       = "org.spire-math"         %% "jawn-json4s"               % "0.10.4"
  lazy val jawnFs2                          = "org.http4s"             %% "jawn-fs2"                  % "0.10.1"
  lazy val jettyServer                      = "org.eclipse.jetty"      %  "jetty-server"              % "9.4.7.v20170914"
  lazy val jettyServlet                     = "org.eclipse.jetty"      %  "jetty-servlet"             % jettyServer.revision
  lazy val json4sCore                       = "org.json4s"             %% "json4s-core"               % "3.5.3"
  lazy val json4sJackson                    = "org.json4s"             %% "json4s-jackson"            % json4sCore.revision
  lazy val json4sNative                     = "org.json4s"             %% "json4s-native"             % json4sCore.revision
  lazy val jspApi                           = "javax.servlet.jsp"      %  "javax.servlet.jsp-api"     % "2.3.1" // YourKit hack
  lazy val log4s                            = "org.log4s"              %% "log4s"                     % "1.3.6"
  lazy val logbackClassic                   = "ch.qos.logback"         %  "logback-classic"           % "1.2.3"
  lazy val macroCompat                      = "org.typelevel"          %% "macro-compat"              % "1.1.1"
  lazy val metricsCore                      = "io.dropwizard.metrics"  %  "metrics-core"              % "3.2.5"
  lazy val metricsJson                      = "io.dropwizard.metrics"  %  "metrics-json"              % metricsCore.revision
  lazy val parboiled                        = "org.http4s"             %% "parboiled"                 % "1.0.0"
  lazy val quasiquotes                      = "org.scalamacros"        %% "quasiquotes"               % "2.1.0"
  lazy val scalacheck                       = "org.scalacheck"         %% "scalacheck"                % "1.13.5"
  def scalaCompiler(so: String, sv: String) = so                       %  "scala-compiler"            % sv
  def scalaReflect(so: String, sv: String)  = so                       %  "scala-reflect"             % sv
  lazy val scalaXml                         = "org.scala-lang.modules" %% "scala-xml"                 % "1.0.6"
  lazy val scodecBits                       = "org.scodec"             %% "scodec-bits"               % "1.1.5"
  lazy val specs2Core                       = "org.specs2"             %% "specs2-core"               % "3.9.4"
  lazy val specs2MatcherExtra               = "org.specs2"             %% "specs2-matcher-extra"      % specs2Core.revision
  lazy val specs2Scalacheck                 = "org.specs2"             %% "specs2-scalacheck"         % specs2Core.revision
  lazy val tomcatCatalina                   = "org.apache.tomcat"      %  "tomcat-catalina"           % "8.5.23"
  lazy val tomcatCoyote                     = "org.apache.tomcat"      %  "tomcat-coyote"             % tomcatCatalina.revision
  lazy val twirlApi                         = "com.typesafe.play"      %% "twirl-api"                 % "1.3.12"
}
