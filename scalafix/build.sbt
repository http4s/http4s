lazy val V = _root_.scalafix.sbt.BuildInfo
lazy val scalafixVersion = V.scalafixVersion
lazy val outputVersion = "0.22.7"
inThisBuild(
  List(
    organization := "org.http4s",
    version := outputVersion,
    homepage := Some(url("https://github.com/http4s/http4s")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    scmInfo := Some(ScmInfo(url("https://github.com/http4s/http4s"), "git@github.com:http4s/http4s.git")),
    developers := List(
        Developer(
          "amarrella",
          "Alessandro Marrella",
          "hello@alessandromarrella.com",
          url("https://alessandromarrella.com")
        ),
        Developer(
          "satorg",
          "Sergey Torgashov",
          "satorg@gmail.com",
          url("https://github.com/satorg")
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
  libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % scalafixVersion,
)
  .settings(scalafixSettings)

lazy val input = project.settings(
    libraryDependencies ++= List(
      "http4s-blaze-client",
      "http4s-blaze-server",
      "http4s-client",
      "http4s-core",
      "http4s-dsl",
      "http4s-tomcat",
      "http4s-jetty",
      "http4s-jetty-client",
      "http4s-okhttp-client",
      "http4s-async-http-client"
    ).map("org.http4s" %% _ % "0.21.18"),
  skip in publish := true
)
  .settings(scalafixSettings)

lazy val output = project.settings(
  skip in publish := true,
  skip in compile := true,
  libraryDependencies ++= Seq(
    "org.http4s" %% "http4s-blaze-client" % outputVersion,
    "org.http4s" %% "http4s-blaze-server" % outputVersion,
    "org.http4s" %% "http4s-dsl" % outputVersion
  )
)
  .settings(scalafixSettings)

lazy val tests = project
  .settings(
    skip in publish := true,
    libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % scalafixVersion % Test cross CrossVersion.full,
    Compile / compile :=
      (Compile / compile).dependsOn(input / Compile / compile).value,
    scalafixTestkitOutputSourceDirectories :=
      (output / Compile / sourceDirectories).value,
    scalafixTestkitInputSourceDirectories :=
      (input / Compile / sourceDirectories).value,
    scalafixTestkitInputClasspath :=
      (input / Compile / fullClasspath).value,
  )
  .settings(scalafixSettings)
  .dependsOn(rules)
  .enablePlugins(ScalafixTestkitPlugin)

lazy val scalafixSettings: Seq[Setting[_]] = Seq(
  developers ++= List(
    Developer(
      "amarrella",
      "Alessandro Marrella",
      "hello@alessandromarrella.com",
      url("https://alessandromarrella.com")
    ),
    Developer(
      "satorg",
      "Sergey Torgashov",
      "satorg@gmail.com",
      url("https://github.com/satorg")
    ),
  ),
  addCompilerPlugin(scalafixSemanticdb),
  scalacOptions += "-Yrangepos",
  startYear := Some(2018)
)

addCommandAlias("ci", ";clean ;test")
