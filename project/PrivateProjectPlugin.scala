// To be spun off into sbt-http4s-org
package org.http4s.build

import com.typesafe.tools.mima.plugin.MimaPlugin
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport.mimaPreviousArtifacts
import sbt._
import sbt.Keys._

object PrivateProjectPlugin extends AutoPlugin {
  override def trigger = noTrigger

  override def requires = Http4sPlugin && MimaPlugin

  override lazy val projectSettings: Seq[Setting[_]] =
    Seq(
      publish / skip := true,
      mimaPreviousArtifacts := Set.empty,
    )
}
