package org.http4s.build

import sbt._, Keys._

import com.typesafe.tools.mima.plugin.MimaPlugin, MimaPlugin.autoImport._
import org.http4s.build.ScalazPlugin.autoImport._
import org.http4s.build.ScalazPlugin.scalazVersionRewriters
import sbtrelease._
import sbtrelease.ReleasePlugin.autoImport._
import scala.util.Properties.envOrNone
import verizon.build.RigPlugin, RigPlugin._

object Http4sPlugin extends AutoPlugin {
  object autoImport {
    val http4sMimaVersion = settingKey[Option[String]]("Version to target for MiMa compatibility")
  }
  import autoImport._

  override def trigger = allRequirements

  override def requires = RigPlugin && MimaPlugin && ScalazPlugin

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    scalazVersionRewriter := scalazVersionRewriters.scalazStream_0_8,

    // Override rig's default of the Travis build number being the bugfix number
    releaseVersion := { ver =>
      Version(ver).map(_.withoutQualifier.string).getOrElse(versionFormatError)
    },
    scalaVersion := (sys.env.get("TRAVIS_SCALA_VERSION") orElse sys.env.get("SCALA_VERSION") getOrElse "2.12.3"),

    // Curiously missing from RigPlugin
    scalacOptions in Compile ++= Seq(
      "-Yno-adapted-args"
    ) ++ {
      // https://issues.scala-lang.org/browse/SI-8340
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 11 => Seq("-Ywarn-numeric-widen")
        case _ => Seq.empty
      }
    },

    http4sMimaVersion := {
      val VRegex = """(\d+)\.(\d+)\.(\d+)a-?.*""".r
      version.value match {
        // Oh, the bitter irony.  VersionNumber fails to parse our own version.
        case VRegex(major, minor, patch) if patch.toInt > 0 =>
          Some(scalazVersionRewriter.value(s"${major}.${minor}.0", scalazVersion.value))
        case _ =>
          None
      }
    },
    mimaFailOnProblem := http4sMimaVersion.value.isDefined,
    mimaPreviousArtifacts := (http4sMimaVersion.value map {
      organization.value % s"${moduleName.value}_${scalaBinaryVersion.value}" % _
    }).toSet
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
    libraryDependencies ++= Seq(
      Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.patch)),
      VersionNumber(scalaVersion.value).numbers match {
        case Seq(2, 10, _*) => Seq(quasiquotes)
        case _ => Seq.empty
      }
    ).flatten

  lazy val alpnBoot                         = "org.mortbay.jetty.alpn" %  "alpn-boot"                 % "8.1.11.v20170118"
  lazy val argonaut                         = "io.argonaut"            %% "argonaut"                  % "6.2"
  lazy val asyncHttpClient                  = "org.asynchttpclient"    %  "async-http-client"         % "2.0.37"
  lazy val blaze                            = "org.http4s"             %% "blaze-http"                % "0.12.9"
  lazy val circeGeneric                     = "io.circe"               %% "circe-generic"             % circeJawn.revision
  lazy val circeJawn                        = "io.circe"               %% "circe-jawn"                % "0.8.0"
  lazy val circeLiteral                     = "io.circe"               %% "circe-literal"             % circeJawn.revision
  lazy val circeParser                      = "io.circe"               %% "circe-parser"              % circeJawn.revision
  lazy val cryptobits                       = "org.reactormonk"        %% "cryptobits"                % "1.1"
  lazy val discipline                       = "org.typelevel"          %% "discipline"                % "0.8"
  lazy val gatlingTest                      = "io.gatling"             %  "gatling-test-framework"    % "2.2.5"
  lazy val gatlingHighCharts                = "io.gatling.highcharts"  %  "gatling-charts-highcharts" % gatlingTest.revision
  lazy val http4sWebsocket                  = "org.http4s"             %% "http4s-websocket"          % "0.2.0"
  lazy val javaxServletApi                  = "javax.servlet"          %  "javax.servlet-api"         % "3.1.0"
  lazy val jawnJson4s                       = "org.spire-math"         %% "jawn-json4s"               % "0.10.4"
  def jawnStreamz(scalazVersion: String)    = "org.http4s"             %% "jawn-streamz"              % "0.10.1" forScalaz scalazVersion
  lazy val jettyServer                      = "org.eclipse.jetty"      %  "jetty-server"              % "9.4.7.v20170914"
  lazy val jettyServlet                     = "org.eclipse.jetty"      %  "jetty-servlet"             % jettyServer.revision
  lazy val json4sCore                       = "org.json4s"             %% "json4s-core"               % "3.5.3"
  lazy val json4sJackson                    = "org.json4s"             %% "json4s-jackson"            % json4sCore.revision
  lazy val json4sNative                     = "org.json4s"             %% "json4s-native"             % json4sCore.revision
  lazy val jspApi                           = "javax.servlet.jsp"      %  "javax.servlet.jsp-api"     % "2.3.1" // YourKit hack
  lazy val log4s                            = "org.log4s"              %% "log4s"                     % "1.3.6"
  lazy val logbackClassic                   = "ch.qos.logback"         %  "logback-classic"           % "1.2.3"
  lazy val macroCompat                      = "org.typelevel"          %% "macro-compat"              % "1.1.1"
  lazy val metricsCore                      = "io.dropwizard.metrics"  %  "metrics-core"              % "3.2.3"
  lazy val metricsJson                      = "io.dropwizard.metrics"  %  "metrics-json"              % metricsCore.revision
  lazy val quasiquotes                      = "org.scalamacros"        %% "quasiquotes"               % "2.1.0"
  lazy val reactiveStreamsTck               = "org.reactivestreams"    %  "reactive-streams-tck"      % "1.0.0"
  lazy val scalacheck                       = "org.scalacheck"         %% "scalacheck"                % "1.13.5"
  def scalaCompiler(so: String, sv: String) = so                       %  "scala-compiler"            % sv
  def scalaReflect(so: String, sv: String)  = so                       %  "scala-reflect"             % sv
  lazy val scalaXml                         = "org.scala-lang.modules" %% "scala-xml"                 % "1.0.6"
  def scalazCore(szv: String)               = "org.scalaz"             %% "scalaz-core"               % szv
  def scalazScalacheckBinding(szv: String)  = "org.scalaz"             %% "scalaz-scalacheck-binding" % szv
  def specs2Core(szv: String)               = "org.specs2"             %% "specs2-core"               % "3.8.6" forScalaz szv
  def specs2MatcherExtra(szv: String)       = "org.specs2"             %% "specs2-matcher-extra"      % specs2Core(szv).revision
  def specs2Scalacheck(szv: String)         = "org.specs2"             %% "specs2-scalacheck"         % specs2Core(szv).revision
  def scalazStream(szv: String)             = "org.scalaz.stream"      %% "scalaz-stream"             % "0.8.6" forScalaz szv
  lazy val tomcatCatalina                   = "org.apache.tomcat"      %  "tomcat-catalina"           % "8.5.21"
  lazy val tomcatCoyote                     = "org.apache.tomcat"      %  "tomcat-coyote"             % tomcatCatalina.revision
  lazy val twirlApi                         = "com.typesafe.play"      %% "twirl-api"                 % "1.3.7"
}
