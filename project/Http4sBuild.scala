import sbt._
import Keys._

import scala.util.Properties.envOrNone

object Http4sBuild extends Build {
  // keys
  val apiVersion = TaskKey[(Int, Int)]("api-version", "Defines the API compatibility version for the project.")
  val jvmTarget = TaskKey[String]("jvm-target-version", "Defines the target JVM version for object files.")

  def extractApiVersion(version: String) = {
    val VersionExtractor = """(\d+)\.(\d+)\..*""".r
    version match {
      case VersionExtractor(major, minor) => (major.toInt, minor.toInt)
    }
  }

  lazy val sonatypeEnvCredentials = (for {
    user <- envOrNone("SONATYPE_USER")
    pass <- envOrNone("SONATYPE_PASS")
  } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)).toSeq

  def compatibleVersion(version: String, scalazVersion: String) = {
    val currentVersionWithoutSnapshot = version.replaceAll("-SNAPSHOT$", "")
    val (targetMajor, targetMinor) = extractApiVersion(version)
    val targetVersion = s"${targetMajor}.${targetMinor}.0${scalazCrossBuildSuffix(scalazVersion)}"
    if (targetVersion != currentVersionWithoutSnapshot)
      Some(targetVersion)
    else
      None
  }

  val macroParadiseSetting =
    libraryDependencies <++= scalaVersion (
      VersionNumber(_).numbers match {
        case Seq(2, 10, _*) => Seq(
          compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
          "org.scalamacros" %% "quasiquotes" % "2.1.0" cross CrossVersion.binary
        )
        case _ => Seq.empty
      })

  val scalazVersion = settingKey[String]("The version of Scalaz used for building.")
  def scalazStreamVersion(scalazVersion: String) =
    "0.8.2" + scalazCrossBuildSuffix(scalazVersion)
  def scalazCrossBuildSuffix(scalazVersion: String) =
    VersionNumber(scalazVersion).numbers match {
      case Seq(7, 1, _*) => ""
      case Seq(7, 2, _*) => "a"
    }
  def specs2Version(scalazVersion: String) =
    VersionNumber(scalazVersion).numbers match {
      case Seq(7, 1, _*) => "3.7.2-scalaz-7.1.7"
      case Seq(7, 2, _*) => "3.7.2"
    }
  def jawnStreamzVersion(scalazVersion: String) =
    "0.9.0" + scalazCrossBuildSuffix(scalazVersion)

  lazy val alpnBoot            = "org.mortbay.jetty.alpn"    % "alpn-boot"               % "8.1.7.v20160121"
  lazy val argonaut            = "io.argonaut"              %% "argonaut"                % "6.2-M1"
  lazy val asyncHttpClient     = "org.asynchttpclient"       % "async-http-client"       % "2.0.2"
  lazy val blaze               = "org.http4s"               %% "blaze-http"              % "0.12.0"
  lazy val circeGeneric        = "io.circe"                 %% "circe-generic"           % circeJawn.revision
  lazy val circeJawn           = "io.circe"                 %% "circe-jawn"              % "0.4.0"
  lazy val discipline          = "org.typelevel"            %% "discipline"              % "0.4"
  lazy val gatlingTest         = "io.gatling"                % "gatling-test-framework"  % "2.1.6"
  lazy val gatlingHighCharts   = "io.gatling.highcharts"     % "gatling-charts-highcharts" % gatlingTest.revision
  lazy val http4sWebsocket     = "org.http4s"               %% "http4s-websocket"        % "0.1.3"
  lazy val javaxServletApi     = "javax.servlet"             % "javax.servlet-api"       % "3.1.0"
  lazy val jawnJson4s          = "org.spire-math"           %% "jawn-json4s"             % jawnParser.revision
  lazy val jawnParser          = "org.spire-math"           %% "jawn-parser"             % "0.8.4"
  def jawnStreamz(scalazVersion: String) = "org.http4s"     %% "jawn-streamz"            % jawnStreamzVersion(scalazVersion)
  lazy val jettyServer         = "org.eclipse.jetty"         % "jetty-server"            % "9.3.7.v20160115"
  lazy val jettyServlet        = "org.eclipse.jetty"         % "jetty-servlet"           % jettyServer.revision
  lazy val json4sCore          = "org.json4s"               %% "json4s-core"             % "3.3.0"
  lazy val json4sJackson       = "org.json4s"               %% "json4s-jackson"          % json4sCore.revision
  lazy val json4sNative        = "org.json4s"               %% "json4s-native"           % json4sCore.revision
  lazy val jspApi              = "javax.servlet.jsp"         % "javax.servlet.jsp-api"   % "2.3.1" // YourKit hack
  lazy val log4s               = "org.log4s"                %% "log4s"                   % "1.1.5"
  lazy val logbackClassic      = "ch.qos.logback"            % "logback-classic"         % "1.1.3"
  lazy val metricsCore         = "io.dropwizard.metrics"     % "metrics-core"            % "3.1.2"
  lazy val metricsJetty9       = "io.dropwizard.metrics"     % "metrics-jetty9"          % metricsCore.revision
  lazy val metricsServlet      = "io.dropwizard.metrics"     % "metrics-servlet"         % metricsCore.revision
  lazy val metricsServlets     = "io.dropwizard.metrics"     % "metrics-servlets"        % metricsCore.revision
  lazy val metricsJson         = "io.dropwizard.metrics"     % "metrics-json"            % metricsCore.revision
  lazy val parboiled           = "org.parboiled"            %% "parboiled"               % "2.1.1"
  lazy val reactiveStreamsTck  = "org.reactivestreams"       % "reactive-streams-tck"    % "1.0.0"
  def scalaReflect(sv: String) = "org.scala-lang"            % "scala-reflect"           % sv
  lazy val scalaXml            = "org.scala-lang.modules"   %% "scala-xml"               % "1.0.5"
  def scalazCore(version: String)               = "org.scalaz"           %% "scalaz-core"               % version
  def scalazScalacheckBinding(version: String)  = "org.scalaz"           %% "scalaz-scalacheck-binding" % version
  def specs2Core(scalazVersion: String)         = "org.specs2"           %% "specs2-core"               % specs2Version(scalazVersion)
  def specs2MatcherExtra(scalazVersion: String) = "org.specs2"           %% "specs2-matcher-extra"      % specs2Core(scalazVersion).revision
  def specs2Scalacheck(scalazVersion: String)   = "org.specs2"           %% "specs2-scalacheck"         % specs2Core(scalazVersion).revision
  def scalazStream(scalazVersion: String)       = "org.scalaz.stream"    %% "scalaz-stream"             % scalazStreamVersion(scalazVersion)
  lazy val tomcatCatalina      = "org.apache.tomcat"         % "tomcat-catalina"         % "8.0.32"
  lazy val tomcatCoyote        = "org.apache.tomcat"         % "tomcat-coyote"           % tomcatCatalina.revision
  lazy val twirlApi            = "com.typesafe.play"        %% "twirl-api"               % "1.1.1"
}
