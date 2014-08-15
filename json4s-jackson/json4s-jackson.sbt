import Http4sKeys._
import Http4sDependencies._

name := "http4s-json4s-jackson"

description := "json4s-jackson support for http4s"

libraryDependencies ++= Seq(
  json4sJackson
)
