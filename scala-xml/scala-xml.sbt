name := "http4s-scala-xml"

description := "scala-xml support for http4s"

// Lifted from Spire build: https://github.com/non/spire/blob/master/project/Build.scala
libraryDependencies := {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, scalaMajor)) if scalaMajor >= 11 =>
      libraryDependencies.value ++ Seq(
        scalaXml
      )
    case Some((2, 10)) =>
      libraryDependencies.value
  }
}

mimaSettings
