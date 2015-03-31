name := "http4s-examples-blaze"

description := "Runs the examples in http4s' blaze runner"

publishArtifact := false

fork := true

libraryDependencies ++= Seq(
  "io.dropwizard.metrics" % "metrics-json" % "3.1.0"
)

seq(Revolver.settings: _*)




