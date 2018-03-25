import sbt._
import Keys._

import scala.util.Properties.envOrNone

object Http4sBuild {

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
    val VersionExtractor = """(\d+)\.(\d+)\.(\d+).*""".r
    currentVersion match {
      case VersionExtractor(major, minor, patch) if minor.toInt == 15 =>
        s"${major.toInt}.${minor.toInt}.${patch.toInt - 1}a"              // scalaz-7.2 for 0.15.x
      case VersionExtractor(major, minor, patch) if patch.toInt > 0 =>
        s"${major.toInt}.${minor.toInt}.${patch.toInt - 1}"
      case _ =>
        currentVersion
    }
  }

  lazy val sonatypeEnvCredentials = (for {
    user <- envOrNone("SONATYPE_USERNAME")
    pass <- envOrNone("SONATYPE_PASSWORD")
  } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)).toSeq

  def compatibleVersion(version: String, scalazVersion: String) = {
    val currentVersionWithoutSnapshot = version.replaceAll("-SNAPSHOT$", "")
    val (targetMajor, targetMinor) = extractApiVersion(version)
    val targetVersion = scalazCrossBuild(s"${targetMajor}.${targetMinor}.0", scalazVersion)
    if (targetVersion != currentVersionWithoutSnapshot)
      Some(targetVersion)
    else
      None
  }

  val macroParadiseSetting =
    libraryDependencies ++= Seq(
      Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)),
      VersionNumber(scalaVersion.value).numbers match {
        case Seq(2, 10, _*) => Seq(quasiquotes)
        case _ => Seq.empty
      }
    ).flatten

  def scalazCrossBuild(version: String, scalazVersion: String) =
    VersionNumber(scalazVersion).numbers match {
      case Seq(7, 1, _*) =>
        version
      case Seq(7, 2, _*) =>
        if (version.endsWith("-SNAPSHOT"))
          version.replaceFirst("-SNAPSHOT$", "a-SNAPSHOT")
        else
          s"${version}a"
    }
  def specs2Version(scalazVersion: String) =
    VersionNumber(scalazVersion).numbers match {
      case Seq(7, 1, _*) => "3.8.6-scalaz-7.1"
      case Seq(7, 2, _*) => "3.8.6" // held back so we don't have to optionally pull in specs2-scalaz
    }

  lazy val alpnBoot            = "org.mortbay.jetty.alpn"    % "alpn-boot"               % "8.1.11.v20170118"
  lazy val argonaut            = "io.argonaut"              %% "argonaut"                % "6.2"
  lazy val asyncHttpClient     = "org.asynchttpclient"       % "async-http-client"       % "2.0.32"
  lazy val blaze               = "org.http4s"               %% "blaze-http"              % "0.12.6"
  lazy val circeGeneric        = "io.circe"                 %% "circe-generic"           % circeJawn.revision
  lazy val circeJawn           = "io.circe"                 %% "circe-jawn"              % "0.6.1"
  lazy val circeLiteral        = "io.circe"                 %% "circe-literal"           % circeJawn.revision
  lazy val cryptobits          = "org.reactormonk"          %% "cryptobits"              % "1.1"
  lazy val discipline          = "org.typelevel"            %% "discipline"              % "0.7.3"
  lazy val gatlingTest         = "io.gatling"                % "gatling-test-framework"  % "2.2.3"
  lazy val gatlingHighCharts   = "io.gatling.highcharts"     % "gatling-charts-highcharts" % gatlingTest.revision
  lazy val http4sWebsocket     = "org.http4s"               %% "http4s-websocket"        % "0.1.6"
  lazy val javaxServletApi     = "javax.servlet"             % "javax.servlet-api"       % "3.1.0"
  lazy val jawnJson4s          = "org.spire-math"           %% "jawn-json4s"             % "0.10.4"
  def jawnStreamz(scalazVersion: String) = "org.http4s"     %% "jawn-streamz"            % scalazCrossBuild("0.10.1", scalazVersion)
  lazy val jettyServer         = "org.eclipse.jetty"         % "jetty-server"            % "9.3.19.v20170502"
  lazy val jettyServlet        = "org.eclipse.jetty"         % "jetty-servlet"           % jettyServer.revision
  lazy val json4sCore          = "org.json4s"               %% "json4s-core"             % "3.5.2"
  lazy val json4sJackson       = "org.json4s"               %% "json4s-jackson"          % json4sCore.revision
  lazy val json4sNative        = "org.json4s"               %% "json4s-native"           % json4sCore.revision
  lazy val jspApi              = "javax.servlet.jsp"         % "javax.servlet.jsp-api"   % "2.3.1" // YourKit hack
  lazy val log4s               = "org.log4s"                %% "log4s"                   % "1.3.4"
  lazy val logbackClassic      = "ch.qos.logback"            % "logback-classic"         % "1.1.11"
  lazy val macroCompat         = "org.typelevel"            %% "macro-compat"            % "1.1.1"
  lazy val metricsCore         = "io.dropwizard.metrics"     % "metrics-core"            % "3.1.4"
  lazy val metricsJson         = "io.dropwizard.metrics"     % "metrics-json"            % metricsCore.revision
  lazy val parboiled           = "org.parboiled"            %% "parboiled"               % "2.1.3"
  lazy val quasiquotes         = "org.scalamacros"          %% "quasiquotes"             % "2.1.0"
  lazy val reactiveStreamsTck  = "org.reactivestreams"       % "reactive-streams-tck"    % "1.0.0"
  lazy val scalacheck          = "org.scalacheck"           %% "scalacheck"              % "1.13.5"
  def scalaCompiler(sv: String) = "org.scala-lang"           % "scala-compiler"          % sv
  def scalaReflect(sv: String) = "org.scala-lang"            % "scala-reflect"           % sv
  lazy val scalaXml            = "org.scala-lang.modules"   %% "scala-xml"               % "1.0.6"
  def scalazCore(version: String)               = "org.scalaz"           %% "scalaz-core"               % version
  def scalazScalacheckBinding(version: String)  = "org.scalaz"           %% "scalaz-scalacheck-binding" % version
  val simpleclientCommon       = "io.prometheus"             % "simpleclient_common"     % "0.0.19"
  def specs2Core(scalazVersion: String)         = "org.specs2"           %% "specs2-core"               % specs2Version(scalazVersion)
  def specs2MatcherExtra(scalazVersion: String) = "org.specs2"           %% "specs2-matcher-extra"      % specs2Core(scalazVersion).revision
  def specs2Scalacheck(scalazVersion: String)   = "org.specs2"           %% "specs2-scalacheck"         % specs2Core(scalazVersion).revision
  def scalazStream(scalazVersion: String)       = "org.scalaz.stream"    %% "scalaz-stream"             % scalazCrossBuild("0.8.6", scalazVersion)
  lazy val tomcatCatalina      = "org.apache.tomcat"         % "tomcat-catalina"         % "8.0.44"
  lazy val tomcatCoyote        = "org.apache.tomcat"         % "tomcat-coyote"           % tomcatCatalina.revision
  lazy val twirlApi            = "com.typesafe.play"        %% "twirl-api"               % "1.3.0"
}
