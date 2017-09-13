package org.http4s.build

import sbt._, Keys._

import com.typesafe.tools.mima.plugin.MimaPlugin, MimaPlugin.autoImport._
import scoverage.ScoverageSbtPlugin, ScoverageSbtPlugin.autoImport._
import verizon.build.DisablePublishingPlugin

object PrivateProjectPlugin extends AutoPlugin {
  override def trigger = noTrigger

  override def requires = Http4sPlugin && MimaPlugin && ScoverageSbtPlugin

  override lazy val projectSettings: Seq[Setting[_]] =
    DisablePublishingPlugin.projectSettings ++ Seq(
      coverageExcludedPackages := ".*",
      mimaPreviousArtifacts := Set.empty
    )
}
