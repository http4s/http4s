import Dependencies._

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies ++= SprayHttp ++ Seq(
  Rl,
  Shapeless,
  PlayIteratees,
  Junit % "test",
  Specs2 % "test"
)

