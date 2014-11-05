import sbt._

import scala.util.Properties.envOrNone

object Http4sBuild {
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
}

object Http4sKeys {
  val apiVersion = TaskKey[(Int, Int)]("api-version", "Defines the API compatibility version for the project.")
}

object Http4sDependencies {
  lazy val argonaut            = "io.argonaut"              %% "argonaut"                % "6.1-M4"
  lazy val base64              = "net.iharder"               % "base64"                  % "2.3.8"
  lazy val blaze               = "org.http4s"               %% "blaze-http"              % "0.2.0"
  lazy val config              = "com.typesafe"              % "config"                  % "1.0.0"
  lazy val javaxServletApi     = "javax.servlet"             % "javax.servlet-api"       % "3.1.0"
  lazy val jettyServer         = "org.eclipse.jetty"         % "jetty-server"            % "9.2.3.v20140905"
  lazy val jettyServlet        = "org.eclipse.jetty"         % "jetty-servlet"           % jettyServer.revision
  lazy val json4sCore          = "org.json4s"               %% "json4s-core"             % "3.2.10"
  lazy val json4sJackson       = "org.json4s"               %% "json4s-jackson"          % json4sCore.revision
  lazy val json4sNative        = "org.json4s"               %% "json4s-native"           % json4sCore.revision
  lazy val jspApi              = "javax.servlet.jsp"         % "javax.servlet.jsp-api"   % "2.3.1" // YourKit hack
  lazy val junit               = "junit"                     % "junit"                   % "4.11"
  lazy val log4s               = "org.log4s"                %% "log4s"                   % "1.1.2"
  lazy val logbackClassic      = "ch.qos.logback"            % "logback-classic"         % "1.0.9"
  lazy val parboiled           = "org.parboiled"            %% "parboiled"               % "2.0.0"
  lazy val rl                  = "org.scalatra.rl"          %% "rl"                      % "0.4.10"
  def scalaReflect(sv: String) = "org.scala-lang"            % "scala-reflect"           % sv
  lazy val scalameter          = "com.github.axel22"        %% "scalameter"              % "0.5-M2"
  lazy val scalazScalacheckBinding = "org.scalaz"           %% "scalaz-scalacheck-binding" % "7.1.0"
  lazy val scalazSpecs2        = "org.typelevel"            %% "scalaz-specs2"           % "0.3.0"
  lazy val scalazStream        = "org.scalaz.stream"        %% "scalaz-stream"           % "0.5a"
  lazy val scodecBits          = "org.typelevel"            %% "scodec-bits"             % "1.0.4"
  lazy val tomcatCatalina      = "org.apache.tomcat"         % "tomcat-catalina"         % "8.0.14"
  lazy val tomcatCoyote        = "org.apache.tomcat"         % "tomcat-coyote"           % tomcatCatalina.revision
}
