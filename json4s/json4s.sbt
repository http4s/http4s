name := "http4s-json4s"

description := "Abstract json4s support for http4s"

libraryDependencies ++= Seq(
  jawnJson4s,
  json4sCore
)

mimaSettings
