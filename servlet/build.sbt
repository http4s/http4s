import Dependencies._

name := "http4s-servlet"

description := "Servlet backend for http4s"

libraryDependencies ++= Seq(
//  AtmosphereRuntime,
  JettyServer % "test",
  JettyServlet % "test",
  ServletApi % "provided"
)