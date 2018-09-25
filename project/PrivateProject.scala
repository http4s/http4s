package org.http4s.build

import com.typesafe.sbt.pgp.PgpKeys.{publishLocalSigned, publishSigned}
import com.typesafe.tools.mima.plugin.MimaPlugin
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._
import explicitdeps.ExplicitDepsPlugin.autoImport._
import sbt._
import scoverage.ScoverageSbtPlugin
import scoverage.ScoverageSbtPlugin.autoImport._
import verizon.build.DisablePublishingPlugin

object PrivateProjectPlugin extends AutoPlugin {
  override def trigger = noTrigger

  override def requires = Http4sPlugin && MimaPlugin && ScoverageSbtPlugin

  override lazy val projectSettings: Seq[Setting[_]] =
    DisablePublishingPlugin.projectSettings ++ Seq(
      coverageExcludedPackages := ".*",
      mimaPreviousArtifacts := Set.empty,
      publishLocalSigned := {},
      publishSigned := {},
      unusedCompileDependenciesTest := {},
    )
}
