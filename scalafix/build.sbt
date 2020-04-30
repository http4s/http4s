lazy val V = _root_.scalafix.sbt.BuildInfo

val inputVersion = "0.19.0"
val outputVersion = "0.21.4"

inThisBuild(
  List(
    organization := "org.http4s",
    version := outputVersion,
    isSnapshot := {
      if (outputVersion == "0.20.0") false
      else isSnapshot.value
    },
    homepage := Some(url("https://github.com/http4s/http4s")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    scmInfo := Some(ScmInfo(url("https://github.com/http4s/http4s"), "git@github.com:http4s/http4s.git")),
    developers := List(
      Developer(
        "amarrella",
        "Alessandro Marrella",
        "hello@alessandromarrella.com",
        url("https://alessandromarrella.com")
      )
    ),
    scalaVersion := V.scala212,
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions ++= List(
      "-Yrangepos"
    ),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    }
  )
)

skip in publish := true

lazy val rules = project.settings(
  moduleName := "http4s-scalafix",
  libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % V.scalafixVersion
)

def http4sDependencies(version: String) = Seq(
  "org.http4s" %% "http4s-blaze-client" % version,
  "org.http4s" %% "http4s-blaze-server" % version,
  "org.http4s" %% "http4s-dsl" % version
)

lazy val input = project.settings(
  skip in publish := true,
  libraryDependencies ++= http4sDependencies(inputVersion)
)

lazy val output = project.settings(
  skip in publish := true,
  skip in compile := true,
  libraryDependencies ++= http4sDependencies(outputVersion)
)

lazy val tests = project
  .settings(
    skip in publish := true,
    libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % V.scalafixVersion % Test cross CrossVersion.full,
    Compile / compile :=
      (Compile / compile).dependsOn(input / Compile / compile).value,
    scalafixTestkitOutputSourceDirectories :=
      (output / Compile / sourceDirectories).value,
    scalafixTestkitInputSourceDirectories :=
      (input / Compile / sourceDirectories).value,
    scalafixTestkitInputClasspath :=
      (input / Compile / fullClasspath).value,
  )
  .dependsOn(rules)
  .enablePlugins(ScalafixTestkitPlugin)

addCommandAlias("ci", ";clean ;test")
