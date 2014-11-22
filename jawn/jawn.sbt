import Http4sKeys._
import Http4sDependencies._

name := "http4s-jawn"

description := "Jawn JSON parsing for http4s"

libraryDependencies ++= Seq(
  jawnStreamz
)