import Dependencies._

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies <+= scalaVersion(ScalaReflect)

libraryDependencies ++= Seq(
  LogbackParent % "provider",
  "org.slf4j" % "slf4j-api" % "1.7.2",
  Rl,
  Shapeless,
  ScalaStm,
  PlayIteratees,
  Parboiled,
  Junit % "test",
  Specs2 % "test"
)

