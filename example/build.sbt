lazy val root = project.in(file("."))
  .settings(
    skip in publish := true
  )
  .aggregate(protobuf, client, server)

val protobuf =
  project
    .in(file("protobuf"))
    .enablePlugins(Fs2Grpc)

lazy val client =
  project
    .in(file("client"))
    .settings(
      libraryDependencies ++= List(
        "io.grpc" % "grpc-netty" % "1.11.0"
      )
    )
    .dependsOn(protobuf)

lazy val server =
  project
    .in(file("server"))
    .settings(
      libraryDependencies ++= List(
        "io.grpc" % "grpc-netty" % "1.11.0",
        "io.grpc" % "grpc-services" % "1.11.0"
      )
    )
    .dependsOn(protobuf)
