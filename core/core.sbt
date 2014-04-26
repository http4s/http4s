import Http4sDependencies._

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies <+= scalaVersion(scalaReflect)

libraryDependencies ++= Seq(
  base64,
  jodaConvert, // Without this, get bad constant pool tag errors loading joda-time classes.
  jodaTime,
  parboiled,
  rl,
  scalazStream,
  scalaloggingSlf4j,
  Http4sDependencies.config
)

seq(buildInfoSettings:_*)

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage <<= organization

