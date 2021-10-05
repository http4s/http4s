package org.http4s.sbt

import laika.ast.LengthUnit._
import laika.ast._
import laika.bundle.ExtensionBundle
import laika.config.{ConfigBuilder, Key, LaikaKeys}
import laika.helium.Helium
import laika.helium.config.{HeliumIcon, IconLink}
import laika.rewrite.{Version, Versions}
import laika.sbt.LaikaConfig
import laika.theme.ThemeProvider
import laika.theme.config.Color
import org.http4s.sbt.Http4sPlugin.{circeJawn, cryptobits, docExampleVersion, latestPerMinorVersion}
import sbt.Keys.{baseDirectory, version}
import sbt.librarymanagement.VersionNumber

object SiteConfig {

  object versions {

    private def version(version: String, label: String): Version =
      Version(version, "v" + version, "/index.html", Some(label))

    val v1_0: Version  = version("1.0", "Dev")
    val v0_23: Version = version("0.23", "Stable")
    val v0_22: Version = version("0.22", "Stable")
    val v0_21: Version = version("0.21", "EOL")

    private val all = Seq(v1_0, v0_23, v0_22, v0_21)

    def config (current: Version): Versions = Versions(
      currentVersion = current,
      olderVersions = all.dropWhile(_ != current).drop(1),
      newerVersions = all.takeWhile(_ != current)
    )
  }

  // This could move back to the Http4sPlugin that currently writes these values to a TOML file for Hugo
  lazy val variables: sbt.Def.Initialize[Map[String, String]] = sbt.Def.setting {
    val (major, minor) = version.value match { // cannot use the existing http4sApiVersion as it is somehow defined as a task, not a setting
      case VersionNumber(Seq(major, minor, _*), _, _) => (major.toInt, minor.toInt)
    }
    val latestInSeries = latestPerMinorVersion(baseDirectory.value)
      .map { case ((major, minor), v) => s"version.http4s.latest.$major.$minor" -> v.toString }
    Map(
      "version.http4s.api"     -> s"$major.$minor",
      "version.http4s.current" -> version.value,
      "version.http4s.doc"     -> docExampleVersion(version.value),
      "version.circe"          -> circeJawn.value.revision,
      "version.cryptobits"     -> cryptobits.revision
    ) ++ latestInSeries
  }

  val extensions: Seq[ExtensionBundle] = Seq(
    laika.markdown.github.GitHubFlavor,
    laika.parse.code.SyntaxHighlighting
  )

  def config (versioned: Boolean): sbt.Def.Initialize[LaikaConfig] = sbt.Def.setting {
    LaikaConfig(
      configBuilder = ConfigBuilder.empty
        .withValue(Key.root, variables.value)
        .withValue(LaikaKeys.versioned, versioned)
    )
  }

  def theme (currentVersion: Version, variables: Map[String, String]): ThemeProvider = HeliumExtensions.applyTo(Helium.defaults
    .site.markupEditLinks(
      text = "Edit this page",
      baseURL = "https://github.com/http4s/http4s/tree/main/docs/jvm/src/main/mdoc")
    .site.layout(
      contentWidth        = px(860),
      navigationWidth     = px(275),
      topBarHeight        = px(35),
      defaultBlockSpacing = px(10),
      defaultLineHeight   = 1.5,
      anchorPlacement     = laika.helium.config.AnchorPlacement.Right
    )
    .site.themeColors(
      primary       = Color.hex("5B7980"),
      secondary     = Color.hex("cc6600"),
      primaryMedium = Color.hex("a7d4de"),
      primaryLight  = Color.hex("e9f1f2"),
      text          = Color.hex("5f5f5f"),
      background    = Color.hex("ffffff"),
      bgGradient    = (Color.hex("5B7980"), Color.hex("a7d4de")) // gradient not used for http4s site atm
    )
    .site.darkMode.disabled
    .site.topNavigationBar(
      homeLink = IconLink.external("../", HeliumIcon.home),
      navLinks = Seq(
        IconLink.external("https://github.com/http4s/http4s", HeliumIcon.github, options = Styles("svg-link")),
        IconLink.external("https://discord.gg/XF3CXcMzqD", HeliumIcon.chat),
        IconLink.external("https://twitter.com/http4s", HeliumIcon.twitter)
      )
    )
    .site.versions(versions.config(currentVersion))
    .build, variables)
}
