import Dependencies._

name := "http4s-netty"

description := "Netty backend for http4s"

libraryDependencies ++= Seq(
  "org.codehaus.groovy" % "groovy" % "2.1.0" % "runtime",
  LogbackParent % "runtime",
  Netty,
  ScalaloggingSlf4j
)

