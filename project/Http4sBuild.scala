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

  lazy val sonatypeEnvCredentials = (for {
    user <- envOrNone("SONATYPE_USER")
    pass <- envOrNone("SONATYPE_PASS")
  } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)).toSeq

  def compatibleVersion(version: String) = {
    val currentVersionWithoutSnapshot = version.replaceAll("-SNAPSHOT$", "")
    val (targetMajor, targetMinor) = extractApiVersion(version)
    val targetVersion = s"${targetMajor}.${targetMinor}.0"
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

  lazy val alpnBoot            = "org.mortbay.jetty.alpn"    % "alpn-boot"               % "8.1.10.v20161026"
  lazy val argonaut            = "io.argonaut"              %% "argonaut"                % "6.2-RC2"
  lazy val asyncHttpClient     = "org.asynchttpclient"       % "async-http-client"       % "2.0.24"
  lazy val blaze               = "org.http4s"               %% "blaze-http"              % "0.12.4"
  lazy val catsKernelLaws      = "org.typelevel"            %% "cats-kernel-laws"        % catsLaws.revision
  lazy val catsLaws            = "org.typelevel"            %% "cats-laws"               % "0.9.0"
  lazy val circeGeneric        = "io.circe"                 %% "circe-generic"           % circeJawn.revision
  lazy val circeJawn           = "io.circe"                 %% "circe-jawn"              % "0.7.0"
  lazy val cryptobits          = "org.reactormonk"          %% "cryptobits"              % "1.1"
  lazy val discipline          = "org.typelevel"            %% "discipline"              % "0.7.2"
  lazy val fs2Cats             = "co.fs2"                   %% "fs2-cats"                % "0.3.0"
  lazy val fs2Io               = "co.fs2"                   %% "fs2-io"                  % "0.9.2"
  lazy val gatlingTest         = "io.gatling"                % "gatling-test-framework"  % "2.2.3"
  lazy val gatlingHighCharts   = "io.gatling.highcharts"     % "gatling-charts-highcharts" % gatlingTest.revision
  lazy val http4sWebsocket     = "org.http4s"               %% "http4s-websocket"        % "0.1.6"
  lazy val javaxServletApi     = "javax.servlet"             % "javax.servlet-api"       % "3.1.0"
  lazy val jawnJson4s          = "org.spire-math"           %% "jawn-json4s"             % jawnParser.revision
  lazy val jawnParser          = "org.spire-math"           %% "jawn-parser"             % "0.10.4"
  lazy val jawnFs2             = "org.http4s"               %% "jawn-fs2"                % "0.10.1"
  lazy val jettyServer         = "org.eclipse.jetty"         % "jetty-server"            % "9.3.14.v20161028"
  lazy val jettyServlet        = "org.eclipse.jetty"         % "jetty-servlet"           % jettyServer.revision
  lazy val json4sCore          = "org.json4s"               %% "json4s-core"             % "3.5.0"
  lazy val json4sJackson       = "org.json4s"               %% "json4s-jackson"          % json4sCore.revision
  lazy val json4sNative        = "org.json4s"               %% "json4s-native"           % json4sCore.revision
  lazy val jspApi              = "javax.servlet.jsp"         % "javax.servlet.jsp-api"   % "2.3.1" // YourKit hack
  lazy val log4s               = "org.log4s"                %% "log4s"                   % "1.3.3"
  lazy val logbackClassic      = "ch.qos.logback"            % "logback-classic"         % "1.1.7"
  lazy val macroCompat         = "org.typelevel"            %% "macro-compat"            % "1.1.1"
  lazy val metricsCore         = "io.dropwizard.metrics"     % "metrics-core"            % "3.1.2"
  lazy val metricsJson         = "io.dropwizard.metrics"     % "metrics-json"            % metricsCore.revision
  lazy val parboiled           = "org.parboiled"            %% "parboiled"               % "2.1.3"
  lazy val quasiquotes         = "org.scalamacros"          %% "quasiquotes"             % "2.1.0"
  lazy val reactiveStreamsTck  = "org.reactivestreams"       % "reactive-streams-tck"    % "1.0.0"
  def scalaCompiler(sv: String) = "org.scala-lang"           % "scala-compiler"          % sv
  def scalaReflect(sv: String) = "org.scala-lang"            % "scala-reflect"           % sv
  lazy val scalacheck          = "org.scalacheck"           %% "scalacheck"              % "1.13.4"
  lazy val scalaXml            = "org.scala-lang.modules"   %% "scala-xml"               % "1.0.5"
  lazy val scodecBits          = "org.scodec"               %% "scodec-bits"             % "1.1.0"
  lazy val specs2Core          = "org.specs2"               %% "specs2-core"             % "3.8.6"
  lazy val specs2MatcherExtra  = "org.specs2"               %% "specs2-matcher-extra"    % specs2Core.revision
  lazy val specs2Scalacheck    = "org.specs2"               %% "specs2-scalacheck"       % specs2Core.revision
  lazy val tomcatCatalina      = "org.apache.tomcat"         % "tomcat-catalina"         % "8.0.39"
  lazy val tomcatCoyote        = "org.apache.tomcat"         % "tomcat-coyote"           % tomcatCatalina.revision
  lazy val twirlApi            = "com.typesafe.play"        %% "twirl-api"               % "1.3.0"
}
