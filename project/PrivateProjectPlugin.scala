package org.http4s.build

import com.typesafe.sbt.pgp.PgpKeys.{publishLocalSigned, publishSigned}
import com.typesafe.tools.mima.plugin.MimaPlugin
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._
import explicitdeps.ExplicitDepsPlugin.autoImport._
import sbt._
import Keys._

object PrivateProjectPlugin extends AutoPlugin {
  override def trigger = noTrigger

  override def requires = Http4sPlugin && MimaPlugin

  override lazy val projectSettings: Seq[Setting[_]] =
    Seq(
      publish / skip := true,
      publish := (()),
      publishSigned := (()),
      publishLocal := (()),
      publishLocalSigned := (()),
      publishArtifact := false,
      publishTo := None,
      Test / publishArtifact := false,
      Test / packageBin / publishArtifact := false,
      Test / packageDoc / publishArtifact := false,
      Test / packageSrc / publishArtifact := false,
      Compile / publishArtifact := false,
      Compile / packageBin / publishArtifact := false,
      Compile / packageDoc / publishArtifact := false,
      Compile / packageSrc / publishArtifact := false,
      mimaPreviousArtifacts := Set.empty,
      unusedCompileDependenciesTest := (()),
    )
}
