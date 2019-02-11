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
      publishLocal := {},
      publish := {},
      publishArtifact in Test := false,
      publishArtifact in Compile := false,
      publishArtifact in (Test, packageBin) := false,
      publishArtifact in (Test, packageDoc) := false,
      publishArtifact in (Test, packageSrc) := false,
      publishArtifact in (Compile, packageBin) := false,
      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in (Compile, packageSrc) := false,
      publishArtifact := false
    ) ++ Seq(
      mimaPreviousArtifacts := Set.empty,
      publishLocalSigned := {},
      publishSigned := {},
      unusedCompileDependenciesTest := {},
    )
}
