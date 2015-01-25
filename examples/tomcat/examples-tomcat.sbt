name := "http4s-examples-tomcat"

description := "Runs the examples in http4s' Tomcat runner"

publishArtifact := false

fork := true

libraryDependencies ++= Seq(
  metricsServlets
)

seq(Revolver.settings: _*)

(mainClass in Revolver.reStart) := Some("com.example.http4s.tomcat.TomcatExample")


