import Dependencies._

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies <+= scalaVersion(ScalaReflect)

libraryDependencies ++= Seq(
  Rl,
  Scalaz,
  Shapeless,
  SprayHttp,
  PlayIteratees,
  Junit % "test",
  Specs2 % "test"
)

