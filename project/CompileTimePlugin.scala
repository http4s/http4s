// To be spun off into sbt-http4s-org
package org.http4s.build

import sbt._
import sbt.Keys._

object CompileTimePlugin extends AutoPlugin {
  object autoImport

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] =
    Seq(
      ivyConfigurations += CompileTime,
      unmanagedClasspath in Compile ++= update.value.select(configurationFilter("CompileTime"))
    )

  val CompileTime = config("CompileTime").hide
}
