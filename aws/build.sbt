name := "AWS Tools"

description := "AWS Tools"


libraryDependencies ++= Seq(
  `http4s-core`,
  `http4s-dsl`,
  `http4s-client`,
  `http4s-json-native`,
  "org.scodec" %% "scodec-scalaz" % "1.0.0"
)
