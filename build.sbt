/* basic project info */
name := "http4s"

organization := "org.http4s"

version := "0.1.0-SNAPSHOT"

description := "Common HTTP framework for Scala"

homepage := Some(url("https://github.com/http4s/http4s"))

startYear := Some(2013)

licenses := Seq(
  ("BSD 2-clause", url("https://raw.github.com/http4s/http4s/develop/LICENSE"))
)

scmInfo := Some(
  ScmInfo(
    url("https://github.com/http4s/http4s"),
    "scm:git:https://github.com/http4s/http4s.git",
    Some("scm:git:git@github.com:http4s/http4s.git")
  )
)

/* scala versions and options */
scalaVersion := "2.10.0"

offline := false

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked"
)

javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

/* dependencies */
libraryDependencies ++= Seq (
  "play" % "play-iteratees_2.10" % "2.1-RC1",
  "io.spray" % "spray-http" % "1.1-M7",
  "org.specs2" %% "specs2" % "1.13" % "test"
)

/* you may need these repos */
resolvers ++= Seq(
  Resolver.typesafeRepo("releases"),
  "spray repo" at "http://repo.spray.io"
)

/* testing */
testOptions += Tests.Argument(TestFrameworks.Specs2, "console", "junitxml")

/* sbt behavior */
logLevel in compile := Level.Warn

traceLevel := 5

/* publishing */
publishMavenStyle := true

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) Some(
    "snapshots" at nexus + "content/repositories/snapshots"
  )
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

mappings in (Compile, packageBin) ~= { (ms: Seq[(File, String)]) =>
  ms filter { case (file, toPath) =>
      toPath != "application.conf"
  }
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <developers>
    <developer>
      <id>rossabaker</id>
      <name>Ross A. Baker</name>
      <email>baker@alumni.indiana.edu</email>
      <!-- <url></url> -->
    </developer>
    <developer>
      <id>casualjim</id>
      <name>Ivan Porto Carrero</name>
      <email>ivan@flanders.co.nz</email>
      <url>http://flanders.co.nz</url>
    </developer>
  </developers>
)
