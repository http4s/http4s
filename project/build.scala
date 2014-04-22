import sbt._

object Http4sDependencies {
  lazy val base64              = "net.iharder"               % "base64"                  % "2.3.8"
  lazy val blaze               = "org.http4s"               %% "blaze"                   % "0.2.0-SNAPSHOT"
  lazy val typesafeConfig      = "com.typesafe"              % "config"                  % "1.0.0"
  lazy val javaxServletApi     = "javax.servlet"             % "javax.servlet-api"       % "3.0.1"
  lazy val jettyServer         = "org.eclipse.jetty"         % "jetty-server"            % "9.1.0.v20131115"
  lazy val jettyServlet        = "org.eclipse.jetty"         % "jetty-servlet"           % jettyServer.revision
  lazy val jettyWebSocket      = "org.eclipse.jetty"         % "jetty-websocket"         % jettyServer.revision
  lazy val jodaConvert         = "org.joda"                  % "joda-convert"            % "1.5"
  lazy val jodaTime            = "joda-time"                 % "joda-time"               % "2.3"
  lazy val jspApi              = "javax.servlet.jsp"         % "javax.servlet.jsp-api"   % "2.3.1" // YourKit hack
  lazy val junit               = "junit"                     % "junit"                   % "4.11"
  lazy val logbackClassic      = "ch.qos.logback"            % "logback-classic"         % "1.0.9"
  lazy val npn_api             = "org.eclipse.jetty.npn"     % "npn-api"                 % npn_version
  lazy val npn_boot            = "org.mortbay.jetty.npn"     % "npn-boot"                % npn_version
  lazy val parboiled           = "org.parboiled"            %% "parboiled"               % "2.0-M1"
  lazy val rl                  = "org.scalatra.rl"          %% "rl"                      % "0.4.10"
  lazy val scalaloggingSlf4j_2_10 = "com.typesafe"          %% "scalalogging-slf4j"      % "1.1.0"
  lazy val scalaloggingSlf4j_2_11 = "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.0.3"
  lazy val scalameter          = "com.github.axel22"        %% "scalameter"              % "0.4-M2"
  lazy val scalatest           = "org.scalatest"            %% "scalatest"               % "2.1.3"
  lazy val scalazStream        = "org.scalaz.stream"        %% "scalaz-stream"           % "0.4.1"
  lazy val slf4jApi            = "org.slf4j"                 % "slf4j-api"               % "1.7.2"

  def scalaReflect(sv: String) = "org.scala-lang"            % "scala-reflect"           % sv

  val npn_version = "8.1.2.v20120308"
}
