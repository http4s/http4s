import Http4sKeys._
import Http4sDependencies._

name := "http4s-argonaut"

description := "argonaut support for http4s"

libraryDependencies ++= Seq(
  argonaut,
  argonautSupport
)
