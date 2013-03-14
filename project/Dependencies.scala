import sbt._

object Dependencies {
  val AkkaActor = "com.typesafe.akka" %% "akka-actor" % "2.1.0"

  val GrizzlyHttpServer = "org.glassfish.grizzly" % "grizzly-http-server" % "2.2.19"

  private val jettyVersion = "8.1.8.v20121106"
  val JettyServer = "org.eclipse.jetty" % "jetty-server" % jettyVersion
  val JettyServlet = "org.eclipse.jetty" % "jetty-servlet" % jettyVersion
  val JettyWebSocket = "org.eclipse.jetty" % "jetty-websocket" % jettyVersion

  val Junit = "junit" % "junit" % "4.11"

  val LogbackClassic = "ch.qos.logback" % "logback-classic" % "1.0.9" % "runtime"

  val Netty = "io.netty" % "netty" % "3.6.3.Final"

  val ParboiledScala = "org.parboiled" %%  "parboiled-scala" % "1.1.4"

  val PlayIteratees = "play" %% "play-iteratees" % "2.1.0"

  val Rl = "org.scalatra.rl" %% "rl" % "0.4.2"

  val ScalaloggingSlf4j = "com.typesafe" %% "scalalogging-slf4j" % "1.0.1"

  val ServletApi = "javax.servlet" % "javax.servlet-api" % "3.0.1"

  val Slf4j = "org.slf4j" % "slf4j-api" % "1.7.2"

  val Specs2 = "org.specs2" %% "specs2" % "1.13"

  val Shapeless = "com.chuusai" %% "shapeless" % "1.2.3"

  val ScalaReflect = (sv: String) => "org.scala-lang" % "scala-reflect" % sv

  val ScalaStm = "org.scala-stm" %% "scala-stm" % "0.7"

  val ScalazCore = "org.scalaz" %% "scalaz-core" % "7.0.0-M7"

  val TypesafeConfig = "com.typesafe" % "config" % "1.0.0"

  val AtmosphereRuntime = "org.atmosphere" % "atmosphere-runtime" % "1.0.11"
}
