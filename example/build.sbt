lazy val protobuf =
  project
    .in(file("."))
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
        "io.grpc" % "grpc-netty" % "1.11.0"
      )
    )
    .dependsOn(protobuf)
