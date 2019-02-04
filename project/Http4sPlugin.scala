package org.http4s.build

import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin
import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport._
import com.timushev.sbt.updates.UpdatesPlugin.autoImport._ // autoImport vs. UpdateKeys necessary here for implicit
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.SbtPgp.autoImport._
import com.typesafe.sbt.git.JGit
import com.typesafe.sbt.pgp.PgpKeys.publishSigned
import com.typesafe.tools.mima.plugin.MimaPlugin
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._
import java.lang.{Runtime => JRuntime}
import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease._
import scoverage.ScoverageKeys.{coverageEnabled, coverageHighlighting}
import verizon.build.RigPlugin
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

  override def requires = RigPlugin && MimaPlugin && ScalafmtCorePlugin

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
        sys.env.get("TRAVIS_JDK_VERSION") == Some("oraclejdk8") &&
        (sys.env.get("TRAVIS_BRANCH") match {
           case Some("master") => true
           case Some(branch) if branch.startsWith("release-") => true
           case _ => false
         })
    },
    coverageEnabled := false,
    http4sMasterBranch := sys.env.get("TRAVIS_BRANCH") == Some("master"),
    http4sApiVersion in ThisBuild := (version in ThisBuild).map {
      case VersionNumber(Seq(major, minor, _*), _, _) => (major.toInt, minor.toInt)
    }.value,
    git.remoteRepo := "git@github.com:http4s/http4s.git"
  ) ++ signingSettings

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    scalaVersion := (sys.env.get("TRAVIS_SCALA_VERSION") orElse sys.env.get("SCALA_VERSION") getOrElse "2.12.7"),

    // Rig will take care of this on production builds.  We haven't fully
    // implemented that machinery yet, so we're going to live without this
    // one for now.
    scalacOptions -= "-Xcheckinit",

    // https://github.com/tkawachi/sbt-doctest/issues/102
    scalacOptions in (Test, compile) -= "-Ywarn-unused:params",

    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, minor)) if minor >= 12 =>
          Seq("-Ybackend-parallelism", math.min(JRuntime.getRuntime.availableProcessors, 16).toString)
        case _ =>
          Seq.empty
      },
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

    addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.9" cross CrossVersion.binary),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"),

    scalafmtVersion := "1.5.1",
    scalafmt in Test := {
      (scalafmt in Compile).value
      (scalafmt in Test).value
      ()
    },
    test in (Test, scalafmt) := {
      (test in (Compile, scalafmt)).value
      (test in (Test, scalafmt)).value
      ()
    },

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
    },

    dependencyUpdatesFilter -= moduleFilter(organization = "javax.servlet"), // servlet-4.0 is not yet supported by jetty-9 or tomcat-9, so don't accidentally depend on its new features
    dependencyUpdatesFilter -= moduleFilter(organization = "org.scalacheck"), // scalacheck-1.14 is incompatible with cats-laws-1.1
    dependencyUpdatesFilter -= moduleFilter(organization = "org.specs2"), // specs2-4.2 is incompatible with scalacheck-1.13
    dependencyUpdatesFilter -= moduleFilter(organization = "org.typelevel", name = "discipline"), // discipline-0.10 is incompatible with scalacheck-1.13
    dependencyUpdatesFilter -= moduleFilter(organization = "org.typelevel", name = "cats-effect"),
    dependencyUpdatesFilter -= moduleFilter(organization = "org.typelevel", name = "cats-effect-laws"),
  ) ++ releaseSettings

  val releaseSettings = Seq(
    // Reset a couple sbt-release defaults that rig changed
    releaseVersion := { ver =>
      Version(ver).map(v =>
        v.copy(qualifier = v.qualifier.map(_.replaceAllLiterally("-SNAPSHOT", "")))
          .string
      ).getOrElse(versionFormatError)
    },
    releaseTagName := s"v${if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value}",
    releasePublishArtifactsAction := Def.taskDyn {
      if (isSnapshot.value) publish
      else publishSigned
    }.value,
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
      val publishable = http4sPublish.value
      val primary = http4sPrimary.value
      val master = http4sMasterBranch.value

      Seq(
        checkSnapshotDependencies.when(release),
        inquireVersions.when(release),
        setReleaseVersion.when(release),
        tagRelease.when(primary && release),
        runClean,
        runTest,
        releaseStepCommand("mimaReportBinaryIssues"),
        releaseStepCommand("unusedCompileDependenciesTest"),
        releaseStepCommand("test:scalafmt::test").when(primary),
        releaseStepCommand("docs/makeSite").when(primary),
        releaseStepCommand("website/makeSite").when(primary),
        openSonatypeRepo.when(publishable && release),
        publishToSonatypeWithoutInstrumentation.when(publishable),
        releaseAndClose.when(publishable && release),
        releaseStepCommand("docs/ghpagesPushSite").when(publishable && primary),
        releaseStepCommand("website/ghpagesPushSite").when(publishable && primary && master),
        setNextVersion.when(publishable && primary && release),
        commitNextVersion.when(publishable && primary && release),
        pushChanges.when(publishable && primary && release),
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
    pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray),
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
    * milestone.  Otherwise, just return the current version.
    */
  def docExampleVersion(currentVersion: String) = {
    val MilestoneVersionExtractor = """(0).(16|17).(0)a?-SNAPSHOT""".r
    val latestMilestone = "M1"
    val VersionExtractor = """(\d+)\.(\d+)\.(\d+).*""".r
    currentVersion match {
      case MilestoneVersionExtractor(major, minor, patch) =>
        s"${major.toInt}.${minor.toInt}.${patch.toInt}-$latestMilestone"
      case VersionExtractor(major, minor, patch) if patch.toInt > 0 =>
        s"${major.toInt}.${minor.toInt}.${patch.toInt - 1}"
      case _ =>
        currentVersion
    }
  }

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

  def addAlpnPath(attList: Keys.Classpath): Seq[String] = {
    for {
      file <- attList.map(_.data)
      path = file.getAbsolutePath if path.contains("jetty") && path.contains("alpn-boot")
    } yield {
      println(s"Adding Alpn classes to boot classpath: $path")
      "-Xbootclasspath/p:" + path
    }
  }

  lazy val alpnBoot                         = "org.mortbay.jetty.alpn" %  "alpn-boot"                 % "8.1.13.v20181017"
  lazy val argonaut                         = "io.argonaut"            %% "argonaut"                  % "6.2.2"
  lazy val asyncHttpClient                  = "org.asynchttpclient"    %  "async-http-client"         % "2.7.0"
  lazy val blaze                            = "org.http4s"             %% "blaze-http"                % "0.14.0-M12"
  lazy val boopickle                        = "io.suzaku"              %% "boopickle"                 % "1.3.0"
  lazy val cats                             = "org.typelevel"          %% "cats-core"                 % "1.6.0"
  lazy val catsEffect                       = "org.typelevel"          %% "cats-effect"               % "1.1.0"
  lazy val catsEffectLaws                   = "org.typelevel"          %% "cats-effect-laws"          % catsEffect.revision
  lazy val catsKernelLaws                   = "org.typelevel"          %% "cats-kernel-laws"          % cats.revision
  lazy val catsLaws                         = "org.typelevel"          %% "cats-laws"                 % cats.revision
  lazy val circeGeneric                     = "io.circe"               %% "circe-generic"             % circeJawn.revision
  lazy val circeJawn                        = "io.circe"               %% "circe-jawn"                % "0.11.1"
  lazy val circeLiteral                     = "io.circe"               %% "circe-literal"             % circeJawn.revision
  lazy val circeParser                      = "io.circe"               %% "circe-parser"              % circeJawn.revision
  lazy val circeTesting                     = "io.circe"               %% "circe-testing"             % circeJawn.revision
  lazy val cryptobits                       = "org.reactormonk"        %% "cryptobits"                % "1.2"
  lazy val dropwizardMetricsCore            = "io.dropwizard.metrics"  %  "metrics-core"              % "4.0.5"
  lazy val dropwizardMetricsJson            = "io.dropwizard.metrics"  %  "metrics-json"              % dropwizardMetricsCore.revision
  lazy val discipline                       = "org.typelevel"          %% "discipline"                % "0.9.0"
  lazy val fs2Io                            = "co.fs2"                 %% "fs2-io"                    % "1.0.3"
  lazy val fs2ReactiveStreams               = "co.fs2"                 %% "fs2-reactive-streams"      % fs2Io.revision
  lazy val javaxServletApi                  = "javax.servlet"          %  "javax.servlet-api"         % "3.1.0"
  lazy val jawnFs2                          = "org.http4s"             %% "jawn-fs2"                  % "0.14.1"
  lazy val jawnJson4s                       = "org.typelevel"          %% "jawn-json4s"               % "0.14.1"
  lazy val jawnPlay                         = "org.typelevel"          %% "jawn-play"                 % "0.14.1"
  lazy val jettyClient                      = "org.eclipse.jetty"      %  "jetty-client"              % "9.4.14.v20181114"
  lazy val jettyRunner                      = "org.eclipse.jetty"      %  "jetty-runner"              % jettyServer.revision
  lazy val jettyServer                      = "org.eclipse.jetty"      %  "jetty-server"              % "9.4.14.v20181114"
  lazy val jettyServlet                     = "org.eclipse.jetty"      %  "jetty-servlet"             % jettyServer.revision
  lazy val json4sCore                       = "org.json4s"             %% "json4s-core"               % "3.6.4"
  lazy val json4sJackson                    = "org.json4s"             %% "json4s-jackson"            % json4sCore.revision
  lazy val json4sNative                     = "org.json4s"             %% "json4s-native"             % json4sCore.revision
  lazy val jspApi                           = "javax.servlet.jsp"      %  "javax.servlet.jsp-api"     % "2.3.3" // YourKit hack
  lazy val log4s                            = "org.log4s"              %% "log4s"                     % "1.6.1"
  lazy val logbackClassic                   = "ch.qos.logback"         %  "logback-classic"           % "1.2.3"
  lazy val mockito                          = "org.mockito"            %  "mockito-core"              % "2.24.0"
  lazy val okhttp                           = "com.squareup.okhttp3"   %  "okhttp"                    % "3.12.1"
  lazy val playJson                         = "com.typesafe.play"      %% "play-json"                 % "2.7.1"
  lazy val prometheusClient                 = "io.prometheus"          %  "simpleclient"              % "0.6.0"
  lazy val prometheusCommon                 = "io.prometheus"          %  "simpleclient_common"       % prometheusClient.revision
  lazy val prometheusHotspot                = "io.prometheus"          %  "simpleclient_hotspot"      % prometheusClient.revision
  lazy val parboiled                        = "org.http4s"             %% "parboiled"                 % "1.0.0"
  lazy val quasiquotes                      = "org.scalamacros"        %% "quasiquotes"               % "2.1.0"
  lazy val scalacheck                       = "org.scalacheck"         %% "scalacheck"                % "1.13.5"
  def scalaCompiler(so: String, sv: String) = so             %  "scala-compiler"            % sv
  def scalaReflect(so: String, sv: String)  = so             %  "scala-reflect"             % sv
  lazy val scalatagsApi                     = "com.lihaoyi"            %% "scalatags"                 % "0.6.7"
  lazy val scalaXml                         = "org.scala-lang.modules" %% "scala-xml"                 % "1.1.1"
  lazy val specs2Core                       = "org.specs2"             %% "specs2-core"               % "4.1.0"
  lazy val specs2Matcher                    = "org.specs2"             %% "specs2-matcher"            % specs2Core.revision
  lazy val specs2MatcherExtra               = "org.specs2"             %% "specs2-matcher-extra"      % specs2Core.revision
  lazy val specs2Scalacheck                 = "org.specs2"             %% "specs2-scalacheck"         % specs2Core.revision
  lazy val treeHugger                       = "com.eed3si9n"           %% "treehugger"                % "0.4.3"
  lazy val tomcatCatalina                   = "org.apache.tomcat"      %  "tomcat-catalina"           % "9.0.14"
  lazy val tomcatCoyote                     = "org.apache.tomcat"      %  "tomcat-coyote"             % tomcatCatalina.revision
  lazy val twirlApi                         = "com.typesafe.play"      %% "twirl-api"                 % "1.4.0"
}
