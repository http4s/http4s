import Http4sKeys._
import Http4sDependencies._

name := "http4s-json4s-native"

description := "json4s-native support for http4s"

libraryDependencies ++= Seq(
  json4sNative
)
