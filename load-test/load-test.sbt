import io.gatling.sbt.GatlingPlugin

name := "http4s-load-test"

description := "Load test of http4s server"

enablePlugins(GatlingPlugin)

libraryDependencies ++= Seq(
  gatlingHighCharts, gatlingTest
)
