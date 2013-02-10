import Dependencies._

name := "http4s-netty"

description := "Netty backend for http4s"

libraryDependencies ++= Seq(
  LogbackParent % "runtime",
  Netty,
  ScalaloggingSlf4j
)

