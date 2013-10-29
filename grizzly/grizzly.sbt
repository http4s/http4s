import Http4sDependencies._

name := "http4s-grizzly"

description := "Glassfish Grizzly backend for http4s"

libraryDependencies ++= Seq(
  grizzlyHttpServer
)