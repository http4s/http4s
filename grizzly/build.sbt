import Dependencies._

name := "http4s-grizzly"

description := "Glassfish Grizzly backend for http4s"

libraryDependencies ++= Seq(
  GrizzlyHttpServer
)