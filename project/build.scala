import sbt._
import Keys._

import spray.revolver.RevolverPlugin._

object build extends Build {

  val gc = TaskKey[Unit]("gc", "runs garbage collector")
  val gcTask = gc := {
    println("requesting garbage collection")
    System gc()
  }

  val http4sSettings = Defaults.defaultSettings ++ Seq(gcTask)

  lazy val project = Project (
    "project",
    file("."),
    settings = http4sSettings
  ) aggregate(core, servlet, netty, grizzly)

  lazy val core = Project(
    "core",
    file("core"),
    settings = http4sSettings
  )

  lazy val servlet = Project(
    "servlet",
    file("servlet"),
    settings = http4sSettings
  ) dependsOn(core % "compile;test->test")

  lazy val netty = Project(
    "netty",
    file("netty"),
    settings = http4sSettings
  ) dependsOn(core % "compile;test->test")

  lazy val grizzly = Project(
    "grizzly",
    file("grizzly"),
    settings = http4sSettings
  ) dependsOn(core % "compile;test->test")
  
  lazy val grizzly_example = Project(
    "grizzly-example",
    file("grizzly-example"),
    settings = http4sSettings ++ Revolver.settings
  ) dependsOn(grizzly, core % "compile->test")
}
