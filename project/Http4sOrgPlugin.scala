package org.http4s.build

import sbt._
import sbt.Keys._

object Http4sOrgPlugin extends AutoPlugin {
  object autoImport

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] =
    Seq(
      organization := "org.http4s"
    )

  override def trigger = allRequirements
}
