package org.http4s.build

import com.timushev.sbt.updates.UpdatesPlugin.autoImport._ // autoImport vs. UpdateKeys necessary here for implicit
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.git.JGit
import com.typesafe.tools.mima.core.{DirectMissingMethodProblem, ProblemFilters}
import com.typesafe.tools.mima.plugin.MimaPlugin
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._
import explicitdeps.ExplicitDepsPlugin.autoImport.unusedCompileDependenciesFilter
import java.lang.{Runtime => JRuntime}
import org.scalafmt.sbt.ScalafmtPlugin
import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
import sbt.Keys._
import sbt._

object Http4sPlugin extends AutoPlugin {
  object autoImport {
    val isCi = settingKey[Boolean]("true if this build is running on CI")
    val http4sMimaVersion = settingKey[Option[String]]("Version to target for MiMa compatibility")
    val http4sApiVersion = taskKey[(Int, Int)]("API version of http4s")
    val http4sJvmTarget = taskKey[String]("JVM target")
    val http4sBuildData = taskKey[Unit]("Export build metadata for Hugo")
  }
  import autoImport._

  override def trigger = allRequirements

  override def requires = MimaPlugin && ScalafmtPlugin

  val scala_213 = "2.13.1"
  val scala_212 = "2.12.10"

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
    scalaVersion := scala_213,
    crossScalaVersions := Seq(scala_213, scala_212),

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
    mimaFailOnNoPrevious := false,
    mimaPreviousArtifacts := (http4sMimaVersion.value.map {
      organization.value % s"${moduleName.value}_${scalaBinaryVersion.value}" % _
    }).toSet,
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.BlazeClientBuilder.this"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("org.http4s.client.blaze.Http1Support.this")
    ),

    addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),

    http4sBuildData := {
      val dest = target.value / "hugo-data" / "build.toml"
      val (major, minor) = http4sApiVersion.value

      val releases = latestPerMinorVersion(baseDirectory.value)
        .map { case ((major, minor), v) => s""""$major.$minor" = "${v.toString}""""}
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
    unusedCompileDependenciesFilter -= moduleFilter(
      organization = "org.scala-lang",
      name = "scala-reflect",
      revision = "2.12.*",
    ), // false positive on 2.12.10
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

  def latestPerMinorVersion(file: File): Map[(Long, Long), VersionNumber] = {
    def majorMinor(v: VersionNumber) = v match {
      case VersionNumber(Seq(major, minor, _), _, _) =>
        Some((major, minor))
      case _ =>
        None
    }

    // M before RC before final
    def patchSortKey(v: VersionNumber) = v match {
      case VersionNumber(Seq(_, _, patch), Seq(q), _) if q startsWith "M" =>
        (patch, 0L, q.drop(1).toLong)
      case VersionNumber(Seq(_, _, patch), Seq(q), _) if q startsWith "RC" =>
        (patch, 1L, q.drop(2).toLong)
      case VersionNumber(Seq(_, _, patch), Seq(), _) => (patch, 2L, 0L)
      case _ => (-1L, -1L, -1L)
    }

    JGit(file).tags.collect {
      case ref if ref.getName.startsWith("refs/tags/v") =>
        VersionNumber(ref.getName.substring("refs/tags/v".size))
    }.foldLeft(Map.empty[(Long, Long), VersionNumber]) {
      case (m, v) =>
        majorMinor(v) match {
          case Some(key) =>
            val max = m.get(key).fold(v) { v0 => Ordering[(Long, Long, Long)].on(patchSortKey).max(v, v0) }
            m.updated(key, max)
          case None => m
        }
    }
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
  lazy val argonaut                         = "io.argonaut"            %% "argonaut"                  % "6.2.5"
  lazy val asyncHttpClient                  = "org.asynchttpclient"    %  "async-http-client"         % "2.11.0"
  lazy val blaze                            = "org.http4s"             %% "blaze-http"                % "0.14.11"
  lazy val boopickle                        = "io.suzaku"              %% "boopickle"                 % "1.3.1"
  lazy val cats                             = "org.typelevel"          %% "cats-core"                 % "2.1.1"
  lazy val catsEffect                       = "org.typelevel"          %% "cats-effect"               % "2.1.2"
  lazy val catsEffectLaws                   = "org.typelevel"          %% "cats-effect-laws"          % catsEffect.revision
  lazy val catsEffectTestingSpecs2          = "com.codecommit"         %% "cats-effect-testing-specs2" % "0.4.0"
  lazy val catsKernelLaws                   = "org.typelevel"          %% "cats-kernel-laws"          % cats.revision
  lazy val catsLaws                         = "org.typelevel"          %% "cats-laws"                 % cats.revision
  lazy val circeGeneric                     = "io.circe"               %% "circe-generic"             % "0.13.0"
  lazy val circeJawn                        = "io.circe"               %% "circe-jawn"                % circeGeneric.revision
  lazy val circeLiteral                     = "io.circe"               %% "circe-literal"             % circeGeneric.revision
  lazy val circeParser                      = "io.circe"               %% "circe-parser"              % circeGeneric.revision
  lazy val circeTesting                     = "io.circe"               %% "circe-testing"             % circeGeneric.revision
  lazy val cryptobits                       = "org.reactormonk"        %% "cryptobits"                % "1.3"
  lazy val dropwizardMetricsCore            = "io.dropwizard.metrics"  %  "metrics-core"              % "4.1.5"
  lazy val dropwizardMetricsJson            = "io.dropwizard.metrics"  %  "metrics-json"              % dropwizardMetricsCore.revision
  lazy val disciplineSpecs2                 = "org.typelevel"          %% "discipline-specs2"         % "1.0.0"
  lazy val fs2Io                            = "co.fs2"                 %% "fs2-io"                    % "2.2.2"
  lazy val fs2ReactiveStreams               = "co.fs2"                 %% "fs2-reactive-streams"      % fs2Io.revision
  lazy val javaxServletApi                  = "javax.servlet"          %  "javax.servlet-api"         % "3.1.0"
  lazy val jawnFs2                          = "org.http4s"             %% "jawn-fs2"                  % "1.0.0"
  lazy val jawnJson4s                       = "org.typelevel"          %% "jawn-json4s"               % "1.0.0"
  lazy val jawnPlay                         = "org.typelevel"          %% "jawn-play"                 % "1.0.0"
  lazy val jettyClient                      = "org.eclipse.jetty"      %  "jetty-client"              % "9.4.27.v20200227"
  lazy val jettyRunner                      = "org.eclipse.jetty"      %  "jetty-runner"              % jettyServer.revision
  lazy val jettyServer                      = "org.eclipse.jetty"      %  "jetty-server"              % "9.4.27.v20200227"
  lazy val jettyServlet                     = "org.eclipse.jetty"      %  "jetty-servlet"             % jettyServer.revision
  lazy val json4sCore                       = "org.json4s"             %% "json4s-core"               % "3.6.7"
  lazy val json4sJackson                    = "org.json4s"             %% "json4s-jackson"            % json4sCore.revision
  lazy val json4sNative                     = "org.json4s"             %% "json4s-native"             % json4sCore.revision
  lazy val jspApi                           = "javax.servlet.jsp"      %  "javax.servlet.jsp-api"     % "2.3.3" // YourKit hack
  lazy val keypool                          = "io.chrisdavenport"      %% "keypool"                   % "0.2.0"
  lazy val log4catsCore                     = "io.chrisdavenport"      %% "log4cats-core"             % "1.0.1"
  lazy val log4catsSlf4j                    = "io.chrisdavenport"      %% "log4cats-slf4j"            % log4catsCore.revision
  lazy val log4catsTesting                  = "io.chrisdavenport"      %% "log4cats-testing"          % log4catsCore.revision
  lazy val log4s                            = "org.log4s"              %% "log4s"                     % "1.8.2"
  lazy val logbackClassic                   = "ch.qos.logback"         %  "logback-classic"           % "1.2.3"
  lazy val mockito                          = "org.mockito"            %  "mockito-core"              % "3.3.3"
  lazy val okhttp                           = "com.squareup.okhttp3"   %  "okhttp"                    % "4.3.1"
  lazy val playJson                         = "com.typesafe.play"      %% "play-json"                 % "2.8.1"
  lazy val prometheusClient                 = "io.prometheus"          %  "simpleclient"              % "0.8.1"
  lazy val prometheusCommon                 = "io.prometheus"          %  "simpleclient_common"       % prometheusClient.revision
  lazy val prometheusHotspot                = "io.prometheus"          %  "simpleclient_hotspot"      % prometheusClient.revision
  lazy val parboiled                        = "org.http4s"             %% "parboiled"                 % "2.0.1"
  lazy val quasiquotes                      = "org.scalamacros"        %% "quasiquotes"               % "2.1.0"
  lazy val scalacheck                       = "org.scalacheck"         %% "scalacheck"                % "1.14.3"
  def scalaReflect(sv: String)              = "org.scala-lang"         %  "scala-reflect"             % sv
  lazy val scalatagsApi                     = "com.lihaoyi"            %% "scalatags"                 % "0.8.6"
  lazy val scalaXml                         = "org.scala-lang.modules" %% "scala-xml"                 % "1.2.0"
  lazy val specs2Cats                       = "org.specs2"             %% "specs2-cats"               % specs2Core.revision
  lazy val specs2Core                       = "org.specs2"             %% "specs2-core"               % "4.9.2"
  lazy val specs2Matcher                    = "org.specs2"             %% "specs2-matcher"            % specs2Core.revision
  lazy val specs2MatcherExtra               = "org.specs2"             %% "specs2-matcher-extra"      % specs2Core.revision
  lazy val specs2Scalacheck                 = "org.specs2"             %% "specs2-scalacheck"         % specs2Core.revision
  lazy val tomcatCatalina                   = "org.apache.tomcat"      %  "tomcat-catalina"           % "9.0.31"
  lazy val tomcatCoyote                     = "org.apache.tomcat"      %  "tomcat-coyote"             % tomcatCatalina.revision
  lazy val twirlApi                         = "com.typesafe.play"      %% "twirl-api"                 % "1.4.2"
  lazy val vault                            = "io.chrisdavenport"      %% "vault"                     % "2.0.0"
}
