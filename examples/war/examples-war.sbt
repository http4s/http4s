name := "http4s-examples-war"

description := "Builds the examples as a .war file"

publishArtifact := false

libraryDependencies ++= Seq(
  javaxServletApi % "provided",
  logbackClassic % "runtime"
)

jetty()
