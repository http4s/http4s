import sbt._

object Dependencies {
  private val jettyVersion = "8.1.8.v20121106"
  val JettyServer = "org.eclipse.jetty" % "jetty-server" % "8.1.8.v20121106"
  val JettyServlet = "org.eclipse.jetty" % "jetty-servlet" % "8.1.8.v20121106"

  val Junit = "junit" % "junit" % "4.11"

  val PlayIteratees = "play" %% "play-iteratees" % "2.1.0"

  val ServletApi = "javax.servlet" % "javax.servlet-api" % "3.0.1"

  val Specs2 = "org.specs2" %% "specs2" % "1.13"
}
