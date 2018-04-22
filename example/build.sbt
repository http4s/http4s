lazy val root = project.in(file("."))
  .settings(
    skip in publish := true
  )
  .aggregate(protobuf, client, server)

val protobuf =
  project
    .in(file("protobuf"))
    .settings(
      PB.targets in Compile := List(
        scalapb.gen() -> (sourceManaged in Compile).value,
        fs2CodeGenerator -> (sourceManaged in Compile).value
      )
    )

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
