import Http4sDependencies._

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies <+= scalaVersion(scalaReflect)

libraryDependencies ++= Seq(
  akkaActor,
  rl,
  slf4jApi,
  scalazCore,
  scalaloggingSlf4j,
  parboiledScala,
  playIteratees,
  typesafeConfig
)

seq(buildInfoSettings:_*)

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage <<= organization

