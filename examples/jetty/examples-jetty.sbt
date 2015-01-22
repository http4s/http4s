name := "http4s-examples-jetty"

description := "Runs the examples in http4s' Jetty runner"

publishArtifact := false

fork := true

libraryDependencies ++= Seq(
  metricsServlets
)

seq(Revolver.settings: _*)

(mainClass in Revolver.reStart) := Some("com.example.http4s.jetty.JettyExample")

