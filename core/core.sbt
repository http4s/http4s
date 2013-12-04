import Http4sDependencies._

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies <+= scalaVersion(scalaReflect)

libraryDependencies ++= Seq(
  akkaActor,
  jodaConvert, // Without this, get bad constant pool tag errors loading joda-time classes.
  jodaTime,
  parboiledScala,
  playIteratees % "test",
  rl,
  slf4jApi,
//  scalazStream,
  scalaloggingSlf4j,
  typesafeConfig
)

seq(buildInfoSettings:_*)

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage <<= organization

