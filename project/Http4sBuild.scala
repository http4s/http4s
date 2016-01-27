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

  def nexusRepoFor(version: String): Resolver = {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot(version))
      "snapshots" at s"$nexus/content/repositories/snapshots"
    else
      "releases" at s"$nexus/service/local/staging/deploy/maven2"
  }

  def isSnapshot(version: String): Boolean = version.endsWith("-SNAPSHOT")

  def compatibleVersion(version: String) = {
    val currentVersionWithoutSnapshot = version.replaceAll("-SNAPSHOT$", "")
    val (targetMajor, targetMinor) = extractApiVersion(version)
    val targetVersion = (targetMajor, targetMinor) match {
      case (0, 11) => "0.11.1" // New line in the sand.  We fell down on the MiMa job.
      case _ => s"${targetMajor}.${targetMinor}.0"
    }
    if (targetVersion != currentVersionWithoutSnapshot)
      Some(targetVersion)
    else
      None
  }

  lazy val alpnBoot            = "org.mortbay.jetty.alpn"    % "alpn-boot"               % "8.1.4.v20150727"
  lazy val asyncHttp           = "org.asynchttpclient"       % "async-http-client"       % "2.0.0-RC7"
  lazy val blaze               = "org.http4s"               %% "blaze-http"              % "0.11.0"
  lazy val circeJawn           = "io.circe"                 %% "circe-jawn"              % "0.3.0"
  lazy val gatlingTest         = "io.gatling"                % "gatling-test-framework"  % "2.1.6"
  lazy val gatlingHighCharts   = "io.gatling.highcharts"     % "gatling-charts-highcharts" % gatlingTest.revision
  lazy val http4sWebsocket     = "org.http4s"               %% "http4s-websocket"        % "0.1.3"
  lazy val javaxServletApi     = "javax.servlet"             % "javax.servlet-api"       % "3.1.0"
  lazy val jawnArgonaut        = "org.spire-math"           %% "jawn-argonaut"           % jawnParser.revision
  lazy val jawnJson4s          = "org.spire-math"           %% "jawn-json4s"             % jawnParser.revision
  lazy val jawnParser          = "org.spire-math"           %% "jawn-parser"             % "0.8.3"
  lazy val jawnStreamz         = "org.http4s"               %% "jawn-streamz"            % "0.7.0"
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
  // Parboiled depends on an ancient Shapeless with _x.y.z compat on Scala 2.10, so we bundle a newer, compatible Shapeless.
  lazy val parboiled           = "org.parboiled"            %% "parboiled"               % "2.1.0" exclude ("com.chuusai", "shapeless_2.10.4")
  lazy val reactiveStreamsTck  = "org.reactivestreams"       % "reactive-streams-tck"    % "1.0.0"
  def scalaReflect(sv: String) = "org.scala-lang"            % "scala-reflect"           % sv
  lazy val scalameter          = "com.storm-enroute"        %% "scalameter"              % "0.7"
  lazy val scalaXml            = "org.scala-lang.modules"   %% "scala-xml"               % "1.0.5"
  lazy val scalazCore          = "org.scalaz"               %% "scalaz-core"             % "7.1.7"
  lazy val scalazScalacheckBinding = "org.scalaz"           %% "scalaz-scalacheck-binding" % scalazCore.revision
  lazy val shapeless           = "com.chuusai"              %% "shapeless"               % "2.2.5"
  lazy val specs2              = "org.specs2"               %% "specs2-core"             % "3.6.6"
  lazy val specs2MatcherExtra  = "org.specs2"               %% "specs2-matcher-extra"    % specs2.revision
  lazy val specs2Scalacheck    = "org.specs2"               %% "specs2-scalacheck"       % specs2.revision
  lazy val scalazStream        = "org.scalaz.stream"        %% "scalaz-stream"           % "0.8"
  lazy val tomcatCatalina      = "org.apache.tomcat"         % "tomcat-catalina"         % "8.0.32"
  lazy val tomcatCoyote        = "org.apache.tomcat"         % "tomcat-coyote"           % tomcatCatalina.revision
  lazy val twirlApi            = "com.typesafe.play"        %% "twirl-api"               % "1.1.1"
}
