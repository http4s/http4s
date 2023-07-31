package org.http4s.sbt

import sbt._, Keys._

import cats.effect._
import laika.ast._
import laika.rewrite._
import laika.ast.Path.Root
import laika.ast._
import laika.bundle.ExtensionBundle
import laika.config.{ConfigBuilder, LaikaKeys}
import laika.helium.Helium
import laika.helium.config.{Favicon, HeliumIcon, IconLink, ImageLink, ReleaseInfo, Teaser, TextLink}
import laika.io.model.InputTree
import laika.theme.ThemeProvider
import laika.theme.ThemeBuilder
import laika.theme.Theme
import laika.rewrite.link.LinkConfig
import laika.rewrite.nav.CoverImage
import laika.rewrite.{Version, Versions}
import laika.sbt.LaikaPlugin.autoImport._
import mdoc.MdocPlugin.autoImport._
import org.typelevel.sbt.gha.GitHubActionsPlugin.autoImport
import org.typelevel.sbt.TypelevelSitePlugin.autoImport._

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
    tlSiteHeliumConfig := {
      tlSiteHeliumConfig.value.site.versions(versions.config(isCi.value))
    },
    tlSiteHeliumConfig := {
      val latest = Http4sPlugin.latestPerMinorVersion(baseDirectory.value)
      // helpful to render landing page when previewing locally
      if (version.value.startsWith("1.") || !isCi.value)
        landingPage.configure(
          tlSiteHeliumConfig.value,
          latest((0, 23)).toString,
          latest((1, 0)).toString,
        )
      else tlSiteHeliumConfig.value
    },
    laikaTheme := laikaTheme.value.extend(redirects.theme),
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
    def config(isCi: Boolean): Versions = Versions(
      currentVersion = current,
      olderVersions = all.dropWhile(_ != current).drop(1),
      newerVersions = all.takeWhile(_ != current),
      // helpful to render unversioned pages when previewing locally
      renderUnversioned = current == v1_0 || !isCi,
    )

    private def version(version: String, label: String): Version =
      Version(version, "v" + version, "/docs/quickstart.html", Some(label))

    val v1_0: Version = version("1", "Dev")
    val v0_23: Version = version("0.23", "Stable")
    val v0_22: Version = version("0.22", "EOL")
    val v0_21: Version = Version("0.21", "v0.21", "/index.html", Some("EOL"))
    val choose: Version = Version(
      "Help me choose...",
      "",
      "/versions.html",
    ) // Pretend it's a "version" to get it into the menu

    val all: Seq[Version] = Seq(v1_0, v0_23, v0_22, v0_21, choose)

    val current: Version = v0_23
  }

  object redirects {

    def theme = new ThemeProvider {
      def build[F[_]: Sync]: Resource[F, Theme[F]] =
        ThemeBuilder[F]("Http4s Redirects")
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
