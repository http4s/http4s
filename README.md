# fs2-grpc
gRPC implementation for FS2/cats-effect

## SBT configuration

`project/plugins.sbt`:
```scala
addSbtPlugin("org.lyranthe.fs2-grpc" % "sbt-java-gen" % "0.1.0-SNAPSHOT")
```

`build.sbt`:
```scala
PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value,
  fs2CodeGenerator -> (sourceManaged in Compile).value
)

scalacOptions += "-Ypartial-unification"
```
