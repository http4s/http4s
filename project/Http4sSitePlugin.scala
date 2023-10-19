package org.http4s.sbt

import sbt._
import Keys._
import cats.effect._
import laika.ast._
import laika.ast.Path.Root
import laika.config.{Version, Versions}
import laika.helium.Helium
import laika.helium.config.{IconLink, LinkGroup, ReleaseInfo, Teaser, TextLink, VersionMenu}
import laika.io.model.InputTree
import laika.theme.ThemeProvider
import laika.theme.ThemeBuilder
import laika.theme.Theme
import mdoc.MdocPlugin.autoImport._
import org.typelevel.sbt.TypelevelSitePlugin.autoImport._
import org.typelevel.sbt.site.GenericSiteSettings
import Http4sPlugin.autoImport._

object Http4sSitePlugin extends AutoPlugin {

  override def requires = Http4sPlugin && Http4sOrgSitePlugin

  override def projectSettings = Seq(
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
    tlSiteHelium := {
      val base = tlSiteHelium.value
        .extendWith(redirects.theme)
        .site
        .internalCSS(Root / "styles")
        .site
        .versions(versions.config(isCi.value))
        .site
        .topNavigationBar(versionMenu = versions.menu("Documentation"))

      val latest = Http4sPlugin.latestPerMinorVersion(baseDirectory.value)
      // helpful to render landing page when previewing locally
      if (version.value.startsWith("1.") || !isCi.value)
        landingPage.configure(
          base,
          latest((0, 23)).toString,
          latest((1, 0)).toString,
          GenericSiteSettings.githubLink.value.toList ++ Seq(
            Http4sOrgSitePlugin.chatLink
          ),
        )
      else base
    },
  )

  object landingPage {
    def configure(
        helium: Helium,
        stableVersion: String,
        milestoneVersion: String,
        links: Seq[IconLink],
    ): Helium =
      helium.site.landingPage(
        logo = Some(Image.internal(Root / "images" / "http4s-logo-text-light.svg")),
        title = None,
        subtitle = Some("Typeful, functional, streaming HTTP for Scala"),
        latestReleases = Seq(
          ReleaseInfo("Latest Stable Release", stableVersion),
          ReleaseInfo("Latest Milestone Release", milestoneVersion),
        ),
        license = Some("Apache 2.0"),
        titleLinks = Seq(
          versions.menu("Getting Started"),
          LinkGroup.create(links.head, links.tail: _*),
        ),
        documentationLinks = projectLinks,
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
      Teaser(
        "Cross-platform",
        "http4s cross-builds for Scala.js and Scala Native. Share code and deploy to browsers, Node.js, native executable binaries, and the JVM.",
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
    def config(isCi: Boolean): Versions = Versions
      .forCurrentVersion(current)
      .withOlderVersions(all.dropWhile(_ != current).drop(1) *)
      .withNewerVersions(all.takeWhile(_ != current) *)
      .withRenderUnversioned(current == v1_0 || !isCi)
    // helpful to render unversioned pages when previewing locally

    private def version(version: String, label: String): Version =
      Version(version, "v" + version).withFallbackLink("/docs/quickstart.html").withLabel(label)

    val v1_0: Version = version("1", "Dev")
    val v0_23: Version = version("0.23", "Stable")
    val v0_22: Version = version("0.22", "EOL")
    val v0_21: Version = Version("0.21", "v0.21").withFallbackLink("/index.html").withLabel("EOL")

    val all: Seq[Version] = Seq(v1_0, v0_23, v0_22, v0_21)

    val current: Version = v1_0

    def menu(unversionedLabel: String): VersionMenu = VersionMenu.create(
      unversionedLabel = unversionedLabel,
      additionalLinks = Seq(TextLink.internal(Root / "versions.md", "Help me choose...")),
    )
  }

  object redirects {

    def theme = new ThemeProvider {
      def build[F[_]: Async]: Resource[F, Theme[F]] =
        ThemeBuilder[F]("http4s Redirects")
          .addInputs(
            // add redirect htmls to the virtual file tree
            // for simplicity, we treat these as unversioned pages
            // such that they are completely managed by the primary branch
            redirects.foldLeft(InputTree[F]) { case (tree, (from, to)) =>
              tree.addString(html(to), from)
            }
          )
          .build
    }

    def html(to: Path) =
      s"""|<!DOCTYPE html>
          |<meta charset="utf-8">
          |<meta http-equiv="refresh" content="0; URL=$to">
          |<link rel="canonical" href="$to">
          |""".stripMargin

    val redirects = {
      val unversioned =
        List(
          "adopters",
          "changelog",
          "code-of-conduct",
          "contributing",
          "further-reading",
          "getting-help",
          "versions",
        ).map { page =>
          Root / page / "index.html" -> Root / s"$page.html"
        }

      import versions._
      val versioned = List("v0.22" -> "v0.22", "v0.23" -> "v0.23", "v1.0" -> "v1").flatMap {
        case (fromV, toV) =>
          List(
            "auth",
            "client",
            "cors",
            "csrf",
            "deployment",
            "dsl",
            "entity",
            "error-handling",
            "gzip",
            "hsts",
            "integrations",
            "json",
            "methods",
            "middleware",
            "service",
            "static",
            "streaming",
            "testing",
            "upgrading",
            "uri",
          ).map { page =>
            Root / fromV / page / "index.html" -> Root / toV / "docs" / s"$page.html"
          } ++ List(Root / fromV / "index.html" -> Root / toV / "docs" / s"quickstart.html")
      }

      versioned ++ unversioned
    }
  }

}
