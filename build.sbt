
lazy val aws = project         // @stephenjudkins

name := "http4s-contrib"

version in ThisBuild := "0.1.0-SNAPSHOT"

description := "http4s contributions"

organization in ThisBuild := "org.http4s"

homepage in ThisBuild := Some(url("https://github.com/http4s/contrib"))

startYear in ThisBuild := Some(2015)

scalaVersion in ThisBuild := "2.11.6"

crossScalaVersions in ThisBuild := Seq(
  "2.10.5",
  "2.11.6"
)

val JvmTarget = "1.7"

scalacOptions in ThisBuild ++= Seq(
  "-deprecation",
  "-feature",
  "-language:implicitConversions",
  "-language:higherKinds",
  s"-target:jvm-${JvmTarget}",
  "-unchecked",
  "-Xlint"
)

javacOptions in ThisBuild ++= Seq(
  "-source", JvmTarget,
  "-target", JvmTarget,
  "-Xlint:deprecation",
  "-Xlint:unchecked"
)
