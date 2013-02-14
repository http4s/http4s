import Dependencies._

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies <+= scalaVersion(ScalaReflect)

libraryDependencies ++= Seq(
  Scalaz,
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

seq(buildInfoSettings:_*)

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage <<= organization

