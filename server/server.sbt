name := "http4s-server"

description := "Server bindings for http4s"

libraryDependencies ++= Seq(
  metricsCore,
  "com.typesafe.akka" %% "akka-actor" % "2.3.9"
)

mimaSettings
