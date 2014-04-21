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
  slf4jApi,
  scalazStream,
  scalaBinaryVersion.value match {
    case "2.10" => scalaloggingSlf4j_2_10
    case "2.11" => scalaloggingSlf4j_2_11
  },
  typesafeConfig
)

seq(buildInfoSettings:_*)

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage <<= organization

