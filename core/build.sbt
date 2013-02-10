import Dependencies._

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies ++= Seq(
  Rl,
  PlayIteratees,
  Junit % "test",
  Specs2 % "test"
)

libraryDependencies ++= SprayHttp