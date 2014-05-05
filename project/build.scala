import sbt._

object Http4sDependencies {
  lazy val base64              = "net.iharder"               % "base64"                  % "2.3.8"
  lazy val blaze               = "org.http4s"               %% "blaze-http"              % "0.2.0-SNAPSHOT"
  lazy val config              = "com.typesafe"              % "config"                  % "1.0.0"
  lazy val javaxServletApi     = "javax.servlet"             % "javax.servlet-api"       % "3.0.1"
  lazy val jettyServer         = "org.eclipse.jetty"         % "jetty-server"            % "9.1.4.v20140401"
  lazy val jettyServlet        = "org.eclipse.jetty"         % "jetty-servlet"           % jettyServer.revision
  lazy val jettyWebSocket      = "org.eclipse.jetty"         % "jetty-websocket"         % jettyServer.revision
  lazy val jodaConvert         = "org.joda"                  % "joda-convert"            % "1.5"
  lazy val jodaTime            = "joda-time"                 % "joda-time"               % "2.3"
  lazy val jspApi              = "javax.servlet.jsp"         % "javax.servlet.jsp-api"   % "2.3.1" // YourKit hack
  lazy val junit               = "junit"                     % "junit"                   % "4.11"
  lazy val logbackClassic      = "ch.qos.logback"            % "logback-classic"         % "1.0.9"
  lazy val parboiled           = "org.parboiled"            %% "parboiled"               % "2.0.0-RC1"
  lazy val rl                  = "org.scalatra.rl"          %% "rl"                      % "0.4.10"
  lazy val scalaloggingSlf4j   = "com.typesafe.scala-logging" %% "scala-logging-slf4j"   % "2.1.2"
  def scalaReflect(sv: String) = "org.scala-lang"            % "scala-reflect"           % sv
  lazy val scalameter          = "com.github.axel22"        %% "scalameter"              % "0.5-M2"
  lazy val scalatest           = "org.scalatest"            %% "scalatest"               % "2.1.3"
  lazy val scalazStream        = "org.scalaz.stream"        %% "scalaz-stream"           % "0.4.1"
  lazy val specs2              = "org.specs2"               %% "specs2"                  % "2.3.11"
}
