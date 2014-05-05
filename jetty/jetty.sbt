import Http4sDependencies._

name := "http4s-jetty"

description := "Jetty backend for http4s"

libraryDependencies ++= Seq(
  jettyServlet
)