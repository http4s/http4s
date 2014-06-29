import Http4sKeys._
import Http4sDependencies._

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies <+= scalaVersion(scalaReflect)

libraryDependencies ++= Seq(
  base64,
  parboiled,
  rl,
  scalazStream,
  scalaloggingSlf4j,
  Http4sDependencies.config
)

seq(buildInfoSettings:_*)

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, apiVersion)

buildInfoPackage <<= organization

