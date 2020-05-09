// To be spun off into sbt-http4s-org
package org.http4s.build

import sbt._
import sbt.Keys._

import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._
import de.heikoseeberger.sbtheader.{AutomateHeaderPlugin, LicenseDetection, LicenseStyle}
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import java.lang.{Runtime => JRuntime}
import _root_.io.chrisdavenport.sbtmimaversioncheck.MimaVersionCheck
import org.scalafmt.sbt.ScalafmtPlugin

object Http4sOrgPlugin extends AutoPlugin {
  object autoImport

  import autoImport._

  override def trigger = allRequirements

  override def requires = MimaVersionCheck && ScalafmtPlugin

  override lazy val projectSettings: Seq[Setting[_]] =
    organizationSettings ++
    scalaSettings ++
    javaSettings ++
    headerSettings

  val organizationSettings: Seq[Setting[_]] =
    Seq(
      organization := "org.http4s",
      organizationName := "http4s.org"
    )

  val scalaSettings: Seq[Setting[_]] =
    Seq(
      scalacOptions ++=
        Seq(
          "-Ybackend-parallelism",
          math.min(JRuntime.getRuntime.availableProcessors, 16).toString
        )
    )

  val javaSettings: Seq[Setting[_]] =
    Seq(
      javacOptions ++=
        Seq(
          "-Xlint:all",
        )
    )

  val headerSettings: Seq[Setting[_]] =
    Seq(
      headerLicenseStyle := LicenseStyle.SpdxSyntax,
      headerLicense := {
        val current = java.time.Year.now().getValue
        val copyrightYear = startYear.value.fold(current.toString)(start => s"$start-$current")
        LicenseDetection(
          licenses.value.toList,
          organizationName.value,
          Some(copyrightYear),
          headerLicenseStyle.value
        )
      }
    )
}
