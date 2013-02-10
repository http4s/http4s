import Dependencies._

name := "http4s-servlet"

description := "Servlet backend for http4s"

libraryDependencies ++= Seq(
  JettyServer % "test",
  JettyServlet % "test",
  ServletApi % "provided"
)