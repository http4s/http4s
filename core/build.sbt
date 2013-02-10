import Dependencies._

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies ++= Seq(
  Rl,
  Shapeless,
  SprayHttp,
  PlayIteratees,
  Junit % "test",
  Specs2 % "test"
)

