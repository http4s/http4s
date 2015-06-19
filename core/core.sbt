import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaKeys.binaryIssueFilters

name := "http4s-core"

description := "Core http4s framework"

libraryDependencies <+= scalaVersion(scalaReflect(_) % "provided")

libraryDependencies ++= Seq(
  base64,
  http4sWebsocket,
  log4s,
  parboiled,
  scalazStream,
  scodecBits
)

// Lifted from Spire build: https://github.com/non/spire/blob/master/project/Build.scala
libraryDependencies := {
  CrossVersion.partialVersion(scalaVersion.value) match {
    // if scala 2.11+ is used, quasiquotes are merged into scala-reflect
    case Some((2, scalaMajor)) if scalaMajor >= 11 =>
      libraryDependencies.value
    // in Scala 2.10, quasiquotes are provided by macro paradise
    case Some((2, 10)) =>
      libraryDependencies.value ++ Seq(
        compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full),
        "org.scalamacros" %% "quasiquotes" % "2.0.1" cross CrossVersion.binary)
  }
}

seq(buildInfoSettings:_*)

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, apiVersion)

buildInfoPackage <<= organization

mimaSettings

binaryIssueFilters ++= Seq(
  ProblemFilters.exclude[MissingMethodProblem]("org.http4s.util.Writer.quote$default$2"),
  ProblemFilters.exclude[MissingMethodProblem]("org.http4s.util.Writer.<<#"),
  ProblemFilters.exclude[MissingMethodProblem]("org.http4s.util.Writer.quote"),
  ProblemFilters.exclude[MissingMethodProblem]("org.http4s.util.Writer.quote$default$3")
)


