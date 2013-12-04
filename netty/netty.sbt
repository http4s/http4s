import Http4sDependencies._

name := "http4s-netty4"

description := "Netty4 backend for http4s"

fork := true

libraryDependencies ++= Seq(
  netty4,
  npn_boot,
  npn_api
)