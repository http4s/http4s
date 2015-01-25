name := "http4s-jetty"

description := "Jetty backend for http4s"

libraryDependencies ++= Seq(
  metricsJetty9,
  jettyServlet
)

mimaSettings
