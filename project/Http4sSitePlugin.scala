package org.http4s.sbt

import sbt._, Keys._

import laika.ast._
import laika.rewrite._
import laika.ast.Path.Root
import laika.ast._
import laika.bundle.ExtensionBundle
import laika.config.{ConfigBuilder, LaikaKeys}
import laika.helium.Helium
import laika.helium.config.{Favicon, HeliumIcon, IconLink, ImageLink, ReleaseInfo, Teaser, TextLink}
import laika.rewrite.link.LinkConfig
import laika.rewrite.nav.CoverImage
import laika.rewrite.{Version, Versions}
import mdoc.MdocPlugin.autoImport._
import org.typelevel.sbt.TypelevelSitePlugin.autoImport._

object Http4sSitePlugin extends AutoPlugin {

  override def requires = Http4sPlugin && Http4sOrgSitePlugin

  override def projectSettings = Seq(
    mdocVariables ++= tlSiteApiUrl.value.map("API_URL" -> _.toString).toMap,
    mdocVariables ++= Map(
      "CIRCE_VERSION" -> Http4sPlugin.V.circe,
      "CRYPTOBITS_VERSION" -> Http4sPlugin.V.cryptobits,
    ),
    mdocVariables ++= {
      val latest = Http4sPlugin.latestPerMinorVersion(baseDirectory.value)
      latest.map { case ((major, minor), v) =>
        s"VERSION_${major}_${minor}" -> v.toString
      }
    },
    tlSiteHeliumConfig ~= {
      _.site.versions(versions.config)
    },
    tlSiteHeliumConfig := {
      val latest = Http4sPlugin.latestPerMinorVersion(baseDirectory.value)
      if (true || version.value.startsWith("1."))
        landingPage.configure(
          tlSiteHeliumConfig.value,
          latest((0, 23)).toString,
          latest((1, 0)).toString,
        )
      else tlSiteHeliumConfig.value
    },
  )

  object landingPage {
    def configure(helium: Helium, stableVersion: String, milestoneVersion: String): Helium =
      helium.site.landingPage(
        logo = Some(Image.internal(Root / "images" / "http4s-logo-text-light.svg")),
        title = None,
        subtitle = Some("Typeful, functional, streaming HTTP for Scala"),
        latestReleases = Seq(
          ReleaseInfo("Latest Stable Release", stableVersion),
          ReleaseInfo("Latest Milestone Release", milestoneVersion),
        ),
        license = Some("Apache 2.0"),
        documentationLinks = projectLinks,
        projectLinks = Nil, // TODO
        teasers = landingPage.teasers,
      )

    val teasers: Seq[Teaser] = Seq(
      Teaser(
        "Typeful",
        "http4s servers and clients share an immutable model of requests and responses. Standard headers are modeled as semantic types, and entity codecs are done by typeclass.",
      ),
      Teaser(
        "Functional",
        "The pure functional side of Scala is favored to promote composability and easy reasoning about your code. I/O is managed through cats-effect.",
      ),
      Teaser(
        "Streaming",
        "http4s is built on FS2, a streaming library that provides for processing and emitting large payloads in constant space and implementing websockets.",
      ),
    )

    val projectLinks: Seq[TextLink] = Seq(
      TextLink.internal(Root / "versions.md", "Versions"),
      TextLink.internal(Root / "changelog.md", "Changelog"),
      TextLink.internal(Root / "getting-help.md", "Getting Help"),
      TextLink.internal(Root / "contributing.md", "Contributing"),
      TextLink.internal(Root / "adopters.md", "Adopters"),
      TextLink.internal(Root / "code-of-conduct.md", "Code of Conduct"),
      TextLink.internal(Root / "further-reading.md", "Further Reading"),
    )

  }

  object versions {
    def config: Versions = Versions(
      currentVersion = current,
      olderVersions = all.dropWhile(_ != current).drop(1),
      newerVersions = all.takeWhile(_ != current),
      renderUnversioned = true || current == v1_0,
    )

    private def version(version: String, label: String): Version =
      Version(version, "v" + version, "/documentation/quickstart.html", Some(label))

    val v1_0: Version = version("1.0", "Dev")
    val v0_23: Version = version("0.23", "Stable")
    val v0_22: Version = version("0.22", "Stable")
    val v0_21: Version = Version("0.21", "v0.21", "/index.html", Some("EOL"))
    val choose: Version = Version(
      "Help me choose...",
      "",
      "/versions.html",
    ) // Pretend it's a "version" to get it into the menu

    val all: Seq[Version] = Seq(v1_0, v0_23, v0_22, v0_21, choose)

    val current: Version = v0_22
  }

}
