import Dependencies._

name := "http4s-grizzly-example"

description := "Glassfish Grizzly backend for http4s"

libraryDependencies ++= Seq(
  GrizzlyHttpServer
)