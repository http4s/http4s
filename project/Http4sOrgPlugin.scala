// To be spun off into sbt-http4s-org
package org.http4s.build

import sbt._
import sbt.Keys._

import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._
import java.lang.{Runtime => JRuntime}
import _root_.io.chrisdavenport.sbtmimaversioncheck.MimaVersionCheck
import org.scalafmt.sbt.ScalafmtPlugin

object Http4sOrgPlugin extends AutoPlugin {
  object autoImport

  import autoImport._

  override def trigger = allRequirements

  override def requires = MimaVersionCheck && ScalafmtPlugin

  override lazy val projectSettings: Seq[Setting[_]] =
    Seq(
      organization := "org.http4s",

      scalacOptions ++=
        Seq(
          "-Ybackend-parallelism",
          math.min(JRuntime.getRuntime.availableProcessors, 16).toString
        )
    )
}
