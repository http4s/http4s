import sbt._

object Dependencies {

  val Rl = "org.scalatra.rl" %% "rl" % "0.4.2"

  val GrizzlyHttpServer = "org.glassfish.grizzly" % "grizzly-http-server" % "2.2.19"

  private val jettyVersion = "8.1.8.v20121106"
  val JettyServer = "org.eclipse.jetty" % "jetty-server" % jettyVersion
  val JettyServlet = "org.eclipse.jetty" % "jetty-servlet" % jettyVersion
  val JettyWebSocket = "org.eclipse.jetty" % "jetty-websocket" % jettyVersion

  val Junit = "junit" % "junit" % "4.11"

  val LogbackParent = "ch.qos.logback" % "logback-classic" % "1.0.9"

  val Netty = "org.jboss.netty" % "netty" % "3.2.9.Final"

  val PlayIteratees = "play" %% "play-iteratees" % "2.1.0"

  val ScalaloggingSlf4j = "com.typesafe" %% "scalalogging-slf4j" % "1.0.1"

  val ServletApi = "javax.servlet" % "javax.servlet-api" % "3.0.1"

  val Specs2 = "org.specs2" %% "specs2" % "1.13"

  val Shapeless = "com.chuusai" %% "shapeless" % "1.2.3"

  val ScalaStm = "org.scala-stm" %% "scala-stm" % "0.7"

  val ScalaReflect = (sv: String) => "org.scala-lang" % "scala-reflect" % sv

  val Parboiled = "org.parboiled" %%  "parboiled-scala" % "1.1.4"
}
