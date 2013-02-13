import Dependencies._

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies <+= scalaVersion(ScalaReflect)

libraryDependencies ++= Seq(
  Scalaz,
  LogbackParent,
  ScalaloggingSlf4j,
  Slf4j,
  Rl,
  Shapeless,
  ScalaStm,
  PlayIteratees,
  Parboiled,
  Junit % "test",
  Specs2 % "test"
)

