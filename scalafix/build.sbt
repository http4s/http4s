lazy val V = _root_.scalafix.sbt.BuildInfo
lazy val outputVersion = "0.20.0"
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

lazy val input = project.settings(
  skip in publish := true,
  libraryDependencies ++= Seq(
    "org.http4s" %% "http4s-blaze-client" % "0.18.21",
    "org.http4s" %% "http4s-blaze-server" % "0.18.21",
    "org.http4s" %% "http4s-dsl" % "0.18.21"
  )
)

lazy val output = project.settings(
  skip in publish := true,
  skip in compile := true,
  libraryDependencies ++= Seq(
    "org.http4s" %% "http4s-blaze-client" % outputVersion,
    "org.http4s" %% "http4s-blaze-server" % outputVersion,
    "org.http4s" %% "http4s-dsl" % outputVersion
  )
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
