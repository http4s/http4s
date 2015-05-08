scalaVersion := "2.11.6"

val http4sVersion = "0.7.0"

libraryDependencies ++= Seq(
  "org.scalaz.stream" %% "scalaz-stream" % "0.7a",
  "org.http4s" %% "http4s-core" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-json4s-native" % http4sVersion,
  "org.http4s" %% "http4s-client" % http4sVersion,
  "org.scodec" %% "scodec-scalaz" % "1.0.0"
)
