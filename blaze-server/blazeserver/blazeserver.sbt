import Http4sDependencies._

name := "http4s-blazeserver"

description := "blaze server backend for http4s"

fork := true

libraryDependencies ++= Seq(
  blaze
)

