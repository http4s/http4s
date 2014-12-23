import com.typesafe.tools.mima.plugin.MimaKeys

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
        compilerPlugin("org.scalamacros" % "paradise" % "2.0.0" cross CrossVersion.full),
        "org.scalamacros" %% "quasiquotes" % "2.0.0" cross CrossVersion.binary)
  }
}

seq(buildInfoSettings:_*)

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, apiVersion)

buildInfoPackage <<= organization

mimaSettings

// This was a private type.
MimaKeys.binaryIssueFilters ++= {
  import com.typesafe.tools.mima.core._
  import com.typesafe.tools.mima.core.ProblemFilters._
  Seq(
    exclude[MissingTypesProblem]("org.http4s.parser.QueryParser"),
    exclude[MissingMethodProblem]("org.http4s.parser.QueryParser.input"),
    exclude[MissingMethodProblem]("org.http4s.parser.QueryParser.org$http4s$parser$QueryParser$$decodeParam"),
    exclude[MissingMethodProblem]("org.http4s.parser.QueryParser.QChar"),
    exclude[MissingMethodProblem]("org.http4s.parser.QueryParser.SubDelims"),
    exclude[MissingMethodProblem]("org.http4s.parser.QueryParser.Unreserved"),
    exclude[MissingMethodProblem]("org.http4s.parser.QueryParser.charset"),
    exclude[MissingMethodProblem]("org.http4s.parser.QueryParser.QueryString"),
    exclude[MissingMethodProblem]("org.http4s.parser.QueryParser.Pchar"),
    exclude[MissingMethodProblem]("org.http4s.parser.QueryParser.QueryParameter"),
    exclude[IncompatibleMethTypeProblem]("org.http4s.parser.QueryParser.this")
  )
}
