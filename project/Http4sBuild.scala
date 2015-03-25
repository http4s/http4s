import sbt._
import Keys._

import scala.util.Properties.envOrNone
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys._

object Http4sBuild extends Build {
  lazy val mimaSettings = mimaDefaultSettings ++ {
    Seq(
      failOnProblem := compatibleVersion(version.value).isDefined,
      previousArtifact := compatibleVersion(version.value) map {
        organization.value % s"${moduleName.value}_${scalaBinaryVersion.value}" % _
      }
    )
  }

  def extractApiVersion(version: String) = {
    val VersionExtractor = """(\d+)\.(\d+)\..*""".r
    version match {
      case VersionExtractor(major, minor) => (major.toInt, minor.toInt)
    }
  }

  lazy val travisCredentials = (envOrNone("SONATYPE_USER"), envOrNone("SONATYPE_PASS")) match {
    case (Some(user), Some(pass)) =>
      Some(Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass))
    case _ =>
      None
  }

  def nexusRepoFor(version: String): Resolver = {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot(version))
      "snapshots" at nexus + "content/repositories/snapshots"
    else
      "releases" at nexus + "service/local/staging/deploy/maven2"
  }

  def isSnapshot(version: String): Boolean = version.endsWith("-SNAPSHOT")

  def compatibleVersion(version: String) = {
    val currentVersionWithoutSnapshot = version.replaceAll("-SNAPSHOT$", "")
    val (targetMajor, targetMinor) = extractApiVersion(version)
    val targetVersion = s"${targetMajor}.${targetMinor}.0"
    if (targetVersion != currentVersionWithoutSnapshot)
      Some(targetVersion)
    else
      None
  }

  val apiVersion = TaskKey[(Int, Int)]("api-version", "Defines the API compatibility version for the project.")

  lazy val argonaut            = "io.argonaut"              %% "argonaut"                % "6.1-M5"
  lazy val argonautSupport     = "org.spire-math"           %% "argonaut-support"        % jawnParser.revision
  lazy val base64              = "net.iharder"               % "base64"                  % "2.3.8"
  lazy val blaze               = "org.http4s"               %% "blaze-http"              % "0.5.0"
  lazy val http4sWebsocket     = "org.http4s"               %% "http4s-websocket"        % "0.1.1"
  lazy val javaxServletApi     = "javax.servlet"             % "javax.servlet-api"       % "3.1.0"
  lazy val jawnParser          = "org.spire-math"           %% "jawn-parser"             % "0.7.2"
  lazy val jawnStreamz         = "org.http4s"               %% "jawn-streamz"            % "0.3.1"
  lazy val jettyServer         = "org.eclipse.jetty"         % "jetty-server"            % "9.2.6.v20141205"
  lazy val jettyServlet        = "org.eclipse.jetty"         % "jetty-servlet"           % jettyServer.revision
  lazy val json4sCore          = "org.json4s"               %% "json4s-core"             % "3.2.11"
  lazy val json4sJackson       = "org.json4s"               %% "json4s-jackson"          % json4sCore.revision
  lazy val json4sNative        = "org.json4s"               %% "json4s-native"           % json4sCore.revision
  lazy val json4sSupport       = "org.spire-math"           %% "json4s-support"          % jawnParser.revision
  lazy val jspApi              = "javax.servlet.jsp"         % "javax.servlet.jsp-api"   % "2.3.1" // YourKit hack
  lazy val log4s               = "org.log4s"                %% "log4s"                   % "1.1.3"
  lazy val logbackClassic      = "ch.qos.logback"            % "logback-classic"         % "1.1.2"
  lazy val metricsCore         = "io.dropwizard.metrics"     % "metrics-core"            % "3.1.0"
  lazy val metricsJetty9       = "io.dropwizard.metrics"     % "metrics-jetty9"          % metricsCore.revision
  lazy val metricsServlet      = "io.dropwizard.metrics"     % "metrics-servlet"         % metricsCore.revision
  lazy val metricsServlets     = "io.dropwizard.metrics"     % "metrics-servlets"        % metricsCore.revision
  lazy val parboiled           = "org.parboiled"            %% "parboiled"               % "2.1.0"
  def scalaReflect(sv: String) = "org.scala-lang"            % "scala-reflect"           % sv
  lazy val scalameter          = "com.storm-enroute"        %% "scalameter"              % "0.6"
  lazy val scalaXml            = "org.scala-lang.modules"   %% "scala-xml"               % "1.0.3"
  lazy val scalazScalacheckBinding = "org.scalaz"           %% "scalaz-scalacheck-binding" % "7.1.0"
  lazy val scalazSpecs2        = "org.typelevel"            %% "scalaz-specs2"           % "0.3.0"
  lazy val scalazStream        = "org.scalaz.stream"        %% "scalaz-stream"           % "0.7a"
  lazy val scodecBits          = "org.typelevel"            %% "scodec-bits"             % "1.0.4"
  lazy val tomcatCatalina      = "org.apache.tomcat"         % "tomcat-catalina"         % "8.0.18"
  lazy val tomcatCoyote        = "org.apache.tomcat"         % "tomcat-coyote"           % tomcatCatalina.revision
  lazy val twirlApi            = "com.typesafe.play"        %% "twirl-api"               % "1.0.4"
}
