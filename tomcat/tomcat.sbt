name := "http4s-tomcat"

description := "Tomcat backend for http4s"

libraryDependencies ++= Seq(
  tomcatCatalina,
  tomcatCoyote
)

mimaSettings
