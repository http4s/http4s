inThisBuild(
  List(
    organization := "org.lyranthe.fs2-grpc",
    git.useGitDescribe := true,
    scmInfo := Some(ScmInfo(url("https://github.com/fiadliel/fs2-grpc"), "git@github.com:fiadliel/fs2-grpc.git"))
  ))

lazy val root = project.in(file("."))
  .enablePlugins(GitVersioning, BuildInfoPlugin)
  .settings(
    sonatypeProfileName := "org.lyranthe",
    skip in publish := true,
    pomExtra in Global := {
      <url>https://github.com/fiadliel/fs2-grpc</url>
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
  .enablePlugins(GitVersioning, BuildInfoPlugin)
  .settings(
    publishTo := sonatypePublishTo.value,
    sbtPlugin := true,
    crossSbtVersions := List(sbtVersion.value, "0.13.17"),
    buildInfoPackage := "org.lyranthe.fs2_grpc.buildinfo",
    addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.18"),
    libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % scalapb.compiler.Version.scalapbVersion
  )

lazy val `java-runtime` = project
  .enablePlugins(GitVersioning)
  .settings(
    scalaVersion := "2.12.7",
    crossScalaVersions := List(scalaVersion.value, "2.11.12"),
    publishTo := sonatypePublishTo.value,
    libraryDependencies ++= List(
      "co.fs2"        %% "fs2-core"         % "1.0.0",
      "org.typelevel" %% "cats-effect"      % "1.0.0",
      "org.typelevel" %% "cats-effect-laws" % "1.0.0" % "test",
      "io.grpc"       % "grpc-core"         % scalapb.compiler.Version.grpcJavaVersion,
      "io.grpc"       % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion % "test",
      "io.monix"      %% "minitest"         % "2.2.1" % "test"
    ),
    mimaPreviousArtifacts := Set(organization.value %% name.value % "0.3.0"),
    testFrameworks += new TestFramework("minitest.runner.Framework"),
    addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.8" cross CrossVersion.binary)
  )
