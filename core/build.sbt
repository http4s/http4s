import Dependencies._

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies <+= scalaVersion(ScalaReflect)

libraryDependencies ++= Seq(
  Junit % "test",
  Rl,
  Slf4j,
  ScalaStm,
  ScalazStream,
  ScalaloggingSlf4j,
  Shapeless,
  Specs2 % "test",
  ParboiledScala,
  PlayIteratees,
  TypesafeConfig
)

seq(buildInfoSettings:_*)

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage <<= organization

