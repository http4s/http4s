import Http4sDependencies._

name := "http4s-client"

description := "Client bindings for http4s"

// Test dependencies
libraryDependencies ++= Seq(
  jettyServlet
).map(_ % "test")