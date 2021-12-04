package org.http4s.sbt

import laika.ast.LengthUnit._
import laika.ast.Path.Root
import laika.ast._
import laika.bundle.ExtensionBundle
import laika.config.{ConfigBuilder, LaikaKeys}
import laika.helium.Helium
import laika.helium.config.{Favicon, HeliumIcon, IconLink, ImageLink, ReleaseInfo, Teaser, TextLink}
import laika.rewrite.link.LinkConfig
import laika.rewrite.nav.CoverImage
import laika.rewrite.{Version, Versions}
import laika.sbt.LaikaConfig
import laika.theme.ThemeProvider
import laika.theme.config.Color
import org.http4s.sbt.Http4sPlugin.autoImport.isCi
import org.http4s.sbt.Http4sPlugin.{circeJawn, cryptobits, docExampleVersion, latestPerMinorVersion}
import sbt.Def.{Initialize, setting}
import sbt.Keys.{baseDirectory, version}
import sbt.librarymanagement.VersionNumber

/** Shared configuration for the `docs` and `website` projects.
  *
  * This class represents relatively trivial configuration code talking to well-documented public APIs of the Laika toolkit.
  * It is the only Laika-related build artifact that is intended to live in this repo indefinitely (the others are
  * temporary workarounds for features not yet part of Laika Core).
  *
  * The configuration code is fairly verbose as Laika prefers code over HOCON for global config.
  * It covers the following areas:
  *
  * - Version configuration for Laika's auto-generated version switcher dropdown.
  * - Link definitions for the landing page and the top navigation bar.
  * - Style adjustments to override the default Helium color theme for the orange/grey theme used by http4s.
  * - Generators for variable substitutions that are used to inject version numbers in markup pages.
  * - The text for the three teaser boxes on the landing page.
  *
  * Helium theme settings are documented here:
  * https://planet42.github.io/Laika/0.18/03-preparing-content/03-theme-settings.html
  */
object SiteConfig {

  object landingPage {

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
      TextLink.internal(Root / "versions" / "README.md", "Versions"),
      TextLink.internal(Root / "changelog" / "README.md", "Changelog"),
      TextLink.internal(Root / "getting-help" / "README.md", "Getting Help"),
      TextLink.internal(Root / "contributing" / "README.md", "Contributing"),
      TextLink.internal(Root / "adopters" / "README.md", "Adopters"),
      TextLink.internal(Root / "code-of-conduct" / "README.md", "Code of Conduct"),
      TextLink.internal(Root / "further-reading" / "README.md", "Further Reading"),
      // TODO: the internal reference is not resolving for now
      TextLink.external(epub.downloadDocURL, "Download (EPUB)"),
    )
  }

  object versions {

    private def version(version: String, label: String): Version =
      Version(version, "v" + version, "/index.html", Some(label))

    val v1_0: Version = version("1.0", "Dev")
    val v0_23: Version = version("0.23", "Stable")
    val v0_22: Version = version("0.22", "Stable")
    val v0_21: Version = version("0.21", "EOL")
    val choose: Version = Version(
      "Help me choose...",
      "versions",
      "/index.html",
    ) // Pretend it's a "version" to get it into the menu

    val all: Seq[Version] = Seq(v1_0, v0_23, v0_22, v0_21, choose)

    val current: Version = v0_22

    def config(current: Version): Versions = Versions(
      currentVersion = current,
      olderVersions = all.dropWhile(_ != current).drop(1),
      newerVersions = all.takeWhile(_ != current),
    )

    val paths: Seq[Path] = all.map { v =>
      Root / v.pathSegment
    } ++ all.map { v =>
      Root / v.pathSegment / "index.html"
    }
  }

  object epub {
    val downloadPageDesc: Option[String] = Some(
      "The e-book contains the same documentation as the website."
    )
    val downloadDocURL =
      s"https://http4s.org/${versions.current.pathSegment}/downloads/"
    val epubMetadataDesc: Option[String] = Some("A minimal, idiomatic Scala interface for HTTP.")
  }

  // This kind of variable generator used to live in Http4sPlugin, but it's not used by anything other than Laika.
  lazy val variables: Initialize[Map[String, String]] = setting {
    val (major, minor) =
      version.value match { // cannot use the existing http4sApiVersion as it is somehow defined as a task, not a setting
        case VersionNumber(Seq(major, minor, _*), _, _) => (major.toInt, minor.toInt)
      }
    val latestInSeries = latestPerMinorVersion(baseDirectory.value)
      .map { case ((major, minor), v) => s"version.http4s.latest.$major.$minor" -> v.toString }
    Map(
      "version.http4s.api" -> s"$major.$minor",
      "version.http4s.current" -> version.value,
      "version.http4s.doc" -> docExampleVersion(version.value),
      "version.circe" -> circeJawn.revision,
      "version.cryptobits" -> cryptobits.revision,
    ) ++ latestInSeries
  }

  val homeURL: Initialize[String] = setting {
    if (isCi.value) "https://http4s.org"
    else
      s"http://127.0.0.1:4242" // port hardcoded for now as laikaPreviewConfig is accidentally declared as a task
  }

  val extensions: Seq[ExtensionBundle] = Seq(
    laika.markdown.github.GitHubFlavor,
    laika.parse.code.SyntaxHighlighting,
  )

  def config(versioned: Boolean): sbt.Def.Initialize[LaikaConfig] = sbt.Def.setting {
    val config = variables.value.foldLeft(ConfigBuilder.empty) { case (builder, (key, value)) =>
      builder.withValue(key, value)
    }

    LaikaConfig(
      configBuilder = config
        .withValue(LaikaKeys.versioned, versioned)
        .withValue(LinkConfig(excludeFromValidation = Seq(Root / "api")))
        .withValue(LaikaKeys.artifactBaseName, s"http4s-${versions.current.displayValue}")
    )
  }

  def theme(
      currentVersion: Version,
      variables: Map[String, String],
      homeURL: String,
      includeLandingPage: Boolean,
  ): ThemeProvider = {

    val apiLink =
      if (includeLandingPage) None
      else
        Some(
          IconLink
            .internal(Root / "api" / "index.html", HeliumIcon.api, options = Styles("svg-link"))
        )

    val baseTheme =
      if (includeLandingPage)
        Helium.defaults.site
          .landingPage(
            logo = Some(Image.internal(Root / "images" / "http4s-logo-text-light-2.svg")),
            title = None,
            subtitle = Some("Typeful, functional, streaming HTTP for Scala"),
            latestReleases = Seq(
              ReleaseInfo(
                "Latest Stable Release",
                variables(s"version.http4s.latest.${versions.all(1).displayValue}")),
              ReleaseInfo(
                "Latest Milestone Release",
                variables(s"version.http4s.latest.${versions.all.head.displayValue}"))
            ),
            license = Some("Apache 2.0"),
            documentationLinks = landingPage.projectLinks,
            projectLinks = Nil, // TODO
            teasers = landingPage.teasers
          )
          .site
          .favIcons(
            Favicon.internal(Root / "images" / "http4s-favicon.svg", "32x32").copy(sizes = None),
            Favicon.internal(Root / "images" / "http4s-favicon.png", "32x32")
          )
      else Helium.defaults

    val fullTheme = baseTheme.all
      .metadata(
        language = Some("en"),
        title = Some("http4s"),
      )
      .site
      .markupEditLinks(
        text = "Edit this page",
        baseURL = "https://github.com/http4s/http4s/edit/main/docs/src/main/mdoc",
      )
      .site
      .layout(
        contentWidth = px(860),
        navigationWidth = px(275),
        topBarHeight = px(35),
        defaultBlockSpacing = px(10),
        defaultLineHeight = 1.5,
        anchorPlacement = laika.helium.config.AnchorPlacement.Right,
      )
      .site
      .themeColors(
        primary = Color.hex("5B7980"),
        secondary = Color.hex("cc6600"),
        primaryMedium = Color.hex("a7d4de"),
        primaryLight = Color.hex("e9f1f2"),
        text = Color.hex("5f5f5f"),
        background = Color.hex("ffffff"),
        bgGradient =
          (Color.hex("334044"), Color.hex("5B7980")), // only used for landing page background
      )
      .site
      .darkMode
      .disabled
      .site
      .topNavigationBar(
        // TODO temporary hard-code of homeURL
        homeLink = ImageLink.external(
          "https://http4s.org",
          Image.internal(Root / "images" / "http4s-logo-text-dark-2.svg"),
        ),
        navLinks = apiLink.toSeq ++ Seq(
          IconLink.external(
            "https://github.com/http4s/http4s",
            HeliumIcon.github,
            options = Styles("svg-link"),
          ),
          IconLink.external("https://discord.gg/XF3CXcMzqD", HeliumIcon.chat),
          IconLink.external("https://twitter.com/http4s", HeliumIcon.twitter),
          // TODO: the internal reference is not resolving for now
          IconLink.external(epub.downloadDocURL, HeliumIcon.download),
        ),
      )
      .site
      .versions(versions.config(currentVersion))
      .site
      .downloadPage(
        title = "Documentation Downloads",
        description = epub.downloadPageDesc,
        includeEPUB = true,
        includePDF = false,
      )
      .epub
      .metadata(
        title = Some("http4s"),
        description = epub.epubMetadataDesc,
        version = Some(versions.current.displayValue),
        language = Some("en"),
      )
      .epub
      .coverImages(CoverImage(Root / "images" / "http4s-logo-text-dark-2.svg"))
      .epub
      .tableOfContent("Table of Content", 3)
      .build

    HeliumExtensions.applyTo(fullTheme, variables, versions.paths)
  }
}
