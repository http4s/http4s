import Http4sDependencies._

name := "http4s-cooldsl"

description := "self documenting DSL for http4s"

fork := true

libraryDependencies ++= Seq(
  "org.json4s" %% "json4s-jackson" % "3.2.7",
  "org.json4s" %% "json4s-ext"     % "3.2.7"
)

