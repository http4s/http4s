package org.http4s.build

import com.jsuereth.sbtpgp.PgpKeys.publishSigned
import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin
import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport._
import com.timushev.sbt.updates.UpdatesPlugin.autoImport._ // autoImport vs. UpdateKeys necessary here for implicit
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.git.JGit
import com.typesafe.tools.mima.core.{DirectMissingMethodProblem, ProblemFilters}
import com.typesafe.tools.mima.plugin.MimaPlugin
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._
import java.lang.{Runtime => JRuntime}
import sbt.Keys._
import sbt._
import sbtrelease.Version

object Http4sPlugin extends AutoPlugin {
  object autoImport {
    val isCi = settingKey[Boolean]("true if this build is running as either a PR or a release build within Travis CI")
    val http4sMimaVersion = settingKey[Option[String]]("Version to target for MiMa compatibility")
    val http4sApiVersion = taskKey[(Int, Int)]("API version of http4s")
    val http4sJvmTarget = taskKey[String]("JVM target")
    val http4sBuildData = taskKey[Unit]("Export build metadata for Hugo")
  }
  import autoImport._

  override def trigger = allRequirements

  override def requires = MimaPlugin && ScalafmtCorePlugin

  override lazy val buildSettings = Seq(
    // Many steps only run on one build. We distinguish the primary build from
    // secondary builds by the Travis build number.
    isCi := sys.env.get("CI").isDefined,
    
    ThisBuild / http4sApiVersion := (ThisBuild / version).map {
      case VersionNumber(Seq(major, minor, _*), _, _) => (major.toInt, minor.toInt)
    }.value,
    git.remoteRepo := "git@github.com:http4s/http4s.git"
  )

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    // Rig will take care of this on production builds.  We haven't fully
    // implemented that machinery yet, so we're going to live without this
    // one for now.
    scalacOptions -= "-Xcheckinit",

    // Getting some spurious unreachable code warnings in 2.13.0-M5
    scalacOptions -= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) =>
          "-Xfatal-warnings"
        case _ =>
          "I DON'T EXIST I'M WORKING AROUND NOT BEING ABLE TO CALL scalaVersion.value FROM ~="
      }
    },

    // https://github.com/tkawachi/sbt-doctest/issues/102
    Test / compile / scalacOptions -= "-Ywarn-unused:params",

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
          Some(s"$major.$minor.${patch.toInt - 1}")
        case _ =>
          None
      }
    },
    mimaFailOnProblem := http4sMimaVersion.value.isDefined,
    mimaPreviousArtifacts := (http4sMimaVersion.value.map {
      case "0.20.5" => "0.20.4" // cursed release
      case v => v
    }.map {
      organization.value % s"${moduleName.value}_${scalaBinaryVersion.value}" % _
    }).toSet,
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.BlazeClientBuilder.this"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.Http1Support.this")
    ),

    libraryDependencies += compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.0"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),

    scalafmtVersion := "1.5.1",
    Test / scalafmt := {
      (Compile / scalafmt).value
      (Test / scalafmt).value
      ()
    },
    Test / scalafmt / test := {
      (Compile / scalafmt / test).value
      (Test / scalafmt / test).value
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
           |$releases
         """.stripMargin

      IO.write(dest, buildData)
    },

    dependencyUpdatesFilter -= moduleFilter(organization = "javax.servlet"), // servlet-4.0 is not yet supported by jetty-9 or tomcat-9, so don't accidentally depend on its new features
    dependencyUpdatesFilter -= moduleFilter(organization = "org.scalacheck"), // scalacheck-1.14 is incompatible with cats-laws-1.1
    dependencyUpdatesFilter -= moduleFilter(organization = "org.specs2"), // specs2-4.2 is incompatible with scalacheck-1.13
    dependencyUpdatesFilter -= moduleFilter(organization = "org.typelevel", name = "discipline"), // discipline-0.10 is incompatible with scalacheck-1.13
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

  object V { // Dependency versions
    private def priorTo2_13(scalaVersion: String): Boolean =
      CrossVersion.partialVersion(scalaVersion) match {
        case Some((2, minor)) if minor < 13 => true
        case _                              => false
      }

    // We pull multiple modules from several projects. This is a convenient
    // reference of all the projects we depend on, and hopefully will reduce
    // error-prone merge conflicts in the dependencies below.
    val alpn = "8.1.13.v20181017"
    val argonaut = "6.2.5"
    val asyncHttpClient = "2.10.5"
    val blaze = "0.14.12"
    val boopickle = "1.3.1"
    val cats = "1.6.1"
    val catsEffect = "1.4.0"
    val circe = "0.11.2"
    val cryptobits = "1.2"
    def discipline(scalaVersion: String): String =
      if (priorTo2_13(scalaVersion)) "0.9.0" else "0.11.0"
    val dropwizardMetrics = "4.1.6"
    def fs2(scalaVersion: String): String =
      if (priorTo2_13(scalaVersion)) "1.0.5" else "1.0.4"
    val jawn = "0.14.3"
    val jawnFs2 = "0.14.2"
    val jetty = "9.4.28.v20200408"
    val json4s = "3.6.7"
    val jsp = "2.3.3"
    val logback = "1.2.3"
    val log4s = "1.8.2"
    val mockito = "2.28.2"
    val okhttp = "3.14.7"
    val parboiledHttp4s = "1.0.1"
    val playJson = "2.7.4"
    val prometheusClient = "0.8.1"
    val quasiquotes = "2.1.0"
    def scalacheck(scalaVersion: String): String =
      if (priorTo2_13(scalaVersion)) "1.13.5" else "1.14.0"
    val scalatags = "0.6.8"
    val scalaXml = "1.1.1"
    val servlet = "3.1.0"
    def specs2(scalaVersion: String): String =
      if (priorTo2_13(scalaVersion)) "4.1.0" else "4.4.1"
    val tomcat = "9.0.34"
    val treehugger = "0.4.4"
    val twirl = "1.4.2"
    val vault = "1.0.0"
  }

  lazy val alpnBoot                         = "org.mortbay.jetty.alpn" %  "alpn-boot"                 % V.alpn
  lazy val argonaut                         = "io.argonaut"            %% "argonaut"                  % V.argonaut
  lazy val asyncHttpClient                  = "org.asynchttpclient"    %  "async-http-client"         % V.asyncHttpClient
  lazy val blaze                            = "org.http4s"             %% "blaze-http"                % V.blaze
  lazy val boopickle                        = "io.suzaku"              %% "boopickle"                 % V.boopickle
  lazy val cats                             = "org.typelevel"          %% "cats-core"                 % V.cats
  lazy val catsEffect                       = "org.typelevel"          %% "cats-effect"               % V.catsEffect
  lazy val catsEffectLaws                   = "org.typelevel"          %% "cats-effect-laws"          % V.catsEffect
  lazy val catsKernelLaws                   = "org.typelevel"          %% "cats-kernel-laws"          % V.cats
  lazy val catsLaws                         = "org.typelevel"          %% "cats-laws"                 % V.cats
  lazy val circeGeneric                     = "io.circe"               %% "circe-generic"             % V.circe
  lazy val circeJawn                        = "io.circe"               %% "circe-jawn"                % V.circe
  lazy val circeLiteral                     = "io.circe"               %% "circe-literal"             % V.circe
  lazy val circeParser                      = "io.circe"               %% "circe-parser"              % V.circe
  lazy val circeTesting                     = "io.circe"               %% "circe-testing"             % V.circe
  lazy val cryptobits                       = "org.reactormonk"        %% "cryptobits"                % V.cryptobits
  lazy val dropwizardMetricsCore            = "io.dropwizard.metrics"  %  "metrics-core"              % V.dropwizardMetrics
  lazy val dropwizardMetricsJson            = "io.dropwizard.metrics"  %  "metrics-json"              % V.dropwizardMetrics
  def discipline(sv: String)                = "org.typelevel"          %% "discipline"                % V.discipline(sv)
  def fs2Io(sv: String)                     = "co.fs2"                 %% "fs2-io"                    % V.fs2(sv)
  def fs2ReactiveStreams(sv: String)        = "co.fs2"                 %% "fs2-reactive-streams"      % V.fs2(sv)
  lazy val javaxServletApi                  = "javax.servlet"          %  "javax.servlet-api"         % V.servlet
  lazy val jawnFs2                          = "org.http4s"             %% "jawn-fs2"                  % V.jawnFs2
  lazy val jawnJson4s                       = "org.typelevel"          %% "jawn-json4s"               % V.jawn
  lazy val jawnPlay                         = "org.typelevel"          %% "jawn-play"                 % V.jawn
  lazy val jettyClient                      = "org.eclipse.jetty"      %  "jetty-client"              % V.jetty
  lazy val jettyHttp2Server                 = "org.eclipse.jetty.http2" %  "http2-server"             % V.jetty
  lazy val jettyRunner                      = "org.eclipse.jetty"      %  "jetty-runner"              % V.jetty
  lazy val jettyServer                      = "org.eclipse.jetty"      %  "jetty-server"              % V.jetty
  lazy val jettyServlet                     = "org.eclipse.jetty"      %  "jetty-servlet"             % V.jetty
  lazy val json4sCore                       = "org.json4s"             %% "json4s-core"               % V.json4s
  lazy val json4sJackson                    = "org.json4s"             %% "json4s-jackson"            % V.json4s
  lazy val json4sNative                     = "org.json4s"             %% "json4s-native"             % V.json4s
  lazy val jspApi                           = "javax.servlet.jsp"      %  "javax.servlet.jsp-api"     % V.jsp // YourKit hack
  lazy val log4s                            = "org.log4s"              %% "log4s"                     % V.log4s
  lazy val logbackClassic                   = "ch.qos.logback"         %  "logback-classic"           % V.logback
  lazy val mockito                          = "org.mockito"            %  "mockito-core"              % V.mockito
  lazy val okhttp                           = "com.squareup.okhttp3"   %  "okhttp"                    % V.okhttp
  lazy val playJson                         = "com.typesafe.play"      %% "play-json"                 % V.playJson
  lazy val prometheusClient                 = "io.prometheus"          %  "simpleclient"              % V.prometheusClient
  lazy val prometheusCommon                 = "io.prometheus"          %  "simpleclient_common"       % V.prometheusClient
  lazy val prometheusHotspot                = "io.prometheus"          %  "simpleclient_hotspot"      % V.prometheusClient
  lazy val parboiled                        = "org.http4s"             %% "parboiled"                 % V.parboiledHttp4s
  lazy val quasiquotes                      = "org.scalamacros"        %% "quasiquotes"               % V.quasiquotes
  def scalacheck(sv:String)                 = "org.scalacheck"         %% "scalacheck"                % V.scalacheck(sv)
  def scalaCompiler(so: String, sv: String) = so             %  "scala-compiler"            % sv
  def scalaReflect(so: String, sv: String)  = so             %  "scala-reflect"             % sv
  lazy val scalatagsApi                     = "com.lihaoyi"            %% "scalatags"                 % V.scalatags
  lazy val scalaXml                         = "org.scala-lang.modules" %% "scala-xml"                 % V.scalaXml
  def specs2Core(sv: String)                = "org.specs2"             %% "specs2-core"               % V.specs2(sv)
  def specs2Matcher(sv: String)             = "org.specs2"             %% "specs2-matcher"            % V.specs2(sv)
  def specs2MatcherExtra(sv: String)        = "org.specs2"             %% "specs2-matcher-extra"      % V.specs2(sv)
  def specs2Scalacheck(sv: String)          = "org.specs2"             %% "specs2-scalacheck"         % V.specs2(sv)
  lazy val tomcatCatalina                   = "org.apache.tomcat"      %  "tomcat-catalina"           % V.tomcat
  lazy val tomcatCoyote                     = "org.apache.tomcat"      %  "tomcat-coyote"             % V.tomcat
  lazy val treeHugger                       = "com.eed3si9n"           %% "treehugger"                % V.treehugger
  lazy val twirlApi                         = "com.typesafe.play"      %% "twirl-api"                 % V.twirl
  lazy val vault                            = "io.chrisdavenport"      %% "vault"                     % V.vault
}
