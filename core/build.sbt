import Dependencies._

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies ++= Seq(
  Junit % "test",
  PlayIteratees,
  Specs2 % "test"
)