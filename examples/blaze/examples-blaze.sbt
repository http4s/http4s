name := "http4s-examples-jetty"

description := "Runs the examples in http4s' blaze runner"

publishArtifact := false

fork := true

seq(Revolver.settings: _*)

(mainClass in Revolver.reStart) := Some("com.example.http4s.blaze.BlazeExample")



