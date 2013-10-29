import Http4sDependencies._

name := "http4s-servlet"

description := "Servlet backend for http4s"

libraryDependencies ++= Seq(
  javaxServletApi % "provided",
  jettyServer % "test",
  jettyServlet % "test"
)