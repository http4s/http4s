import sbt._
import Keys._

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
  ) aggregate(core)

  lazy val core = Project(
    "core",
    file("core"),
    settings = http4sSettings
  )
}
