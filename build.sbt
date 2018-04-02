inThisBuild(
  List(
    organization := "org.lyranthe.fs2-grpc",
    git.useGitDescribe := true
  ))

lazy val root = project.in(file("."))
  .enablePlugins(GitVersioning)
  .settings(
    sonatypeProfileName := "org.lyranthe",
    skip in publish := true,
    pomExtra in Global := {
      <url>https://github.com/fiadliel/http4s-timer</url>
        <licenses>
          <license>
            <name>MIT</name>
              <url>https://github.com/fiadliel/fs2-grpc/blob/master/LICENSE</url>
          </license>
        </licenses>
        <developers>
          <developer>
            <id>fiadliel</id>
            <name>Gary Coady</name>
            <url>https://www.lyranthe.org/</url>
          </developer>
        </developers>
    }
  )
  .aggregate(`sbt-java-gen`, `java-runtime`)

lazy val `sbt-java-gen` = project
  .enablePlugins(GitVersioning)
  .settings(
    publishTo := sonatypePublishTo.value,
    sbtPlugin := true,
    crossSbtVersions := List(sbtVersion.value, "0.13.17"),
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.18"),
    libraryDependencies ++= List(
      "io.grpc"              % "grpc-core"       % "1.11.0",
      "com.thesamet.scalapb" %% "compilerplugin" % "0.7.1"
    )
  )

lazy val `java-runtime` = project
  .enablePlugins(GitVersioning)
  .settings(
    scalaVersion := "2.12.5",
    crossScalaVersions := List(scalaVersion.value, "2.11.12"),
    publishTo := sonatypePublishTo.value,
    libraryDependencies ++= List(
      "co.fs2"        %% "fs2-core"         % "0.10.3",
      "io.grpc"       % "grpc-core"         % "1.11.0",
      "io.monix"      %% "minitest"         % "2.1.1" % "test",
      "org.typelevel" %% "cats-effect-laws" % "0.10" % "test"
    ),
    testFrameworks += new TestFramework("minitest.runner.Framework"),
    addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.6" cross CrossVersion.binary)
  )
