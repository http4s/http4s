import Dependencies._

name := "http4s-netty"

description := "Netty backend for http4s"

libraryDependencies ++= Seq(
  Netty,
  ScalaloggingSlf4j
)

