import sbt._

object Http4sDependencies {
  lazy val akkaActor           = "com.typesafe.akka"        %% "akka-actor"              % "2.1.0"
  lazy val atmosphereRuntime   = "org.atmosphere"            % "atmosphere-runtime"      % "1.0.11"
  lazy val typesafeConfig      = "com.typesafe"              % "config"                  % "1.0.0"
  lazy val grizzlyHttpServer   = "org.glassfish.grizzly"     % "grizzly-http-server"     % "2.2.19"
  lazy val javaxServletApi     = "javax.servlet"             % "javax.servlet-api"       % "3.0.1"
  lazy val jettyServer         = "org.eclipse.jetty"         % "jetty-server"            % "8.1.8.v20121106"
  lazy val jettyServlet        = "org.eclipse.jetty"         % "jetty-servlet"           % jettyServer.revision
  lazy val jettyWebSocket      = "org.eclipse.jetty"         % "jetty-websocket"         % jettyServer.revision
  lazy val junit               = "junit"                     % "junit"                   % "4.11"
  lazy val logbackClassic      = "ch.qos.logback"            % "logback-classic"         % "1.0.9"
  lazy val netty4              = "io.netty"                  % "netty-all"               % "4.0.10.Final"
  lazy val parboiledScala      = "org.parboiled"            %% "parboiled-scala"         % "1.1.4"
  lazy val playIteratees       = "com.typesafe.play"        %% "play-iteratees"          % "2.2.0"
  lazy val rl                  = "org.scalatra.rl"          %% "rl"                      % "0.4.2"
  lazy val scalaloggingSlf4j   = "com.typesafe"             %% "scalalogging-slf4j"      % "1.0.1"
  lazy val scalazCore          = "org.scalaz"               %% "scalaz-core"             % "7.0.0-M7"
  lazy val slf4jApi            = "org.slf4j"                 % "slf4j-api"               % "1.7.2"
  lazy val specs2              = "org.specs2"               %% "specs2"                  % "1.13"
  lazy val shapeless           = "com.chuusai"              %% "shapeless"               % "1.2.3"

  def scalaReflect(sv: String) = "org.scala-lang"            % "scala-reflect"           % sv
}