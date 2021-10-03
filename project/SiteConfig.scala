package org.http4s.sbt

import laika.ast.LengthUnit._
import laika.ast._
import laika.bundle.ExtensionBundle
import laika.config.ConfigBuilder
import laika.helium.Helium
import laika.parse.code.CodeCategory
import laika.sbt.LaikaConfig
import laika.sbt.LaikaPlugin.autoImport.laikaSpanRewriteRule
import laika.theme.ThemeProvider
import org.http4s.sbt.Http4sPlugin.{circeJawn, cryptobits, docExampleVersion, latestPerMinorVersion}
import sbt.Keys.{baseDirectory, version}
import sbt.librarymanagement.VersionNumber

object SiteConfig {

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

  private val LaikaCodeSubstitution = "(.*)@\\{(.*)}(.*)".r

  lazy val extensions: sbt.Def.Initialize[Seq[ExtensionBundle]] = sbt.Def.setting {
    Seq(
      laika.markdown.github.GitHubFlavor,
      laika.parse.code.SyntaxHighlighting,
      laikaSpanRewriteRule {
        // Laika currently does not do variable substitutions in code blocks, this serves as a temporary workaround.
        // This will potentially be supported out of the box in the 0.19 series, but requires some significant work
        // due to issues with processing order (variable substitutions normally run after highlighters which would
        // be the wrong order - Laika's default variable substitution format `${...}` also conflicts with Scala code.
        case CodeSpan(content, cats, opts) if cats.contains(CodeCategory.StringLiteral) => content match {
          case SiteConfig.LaikaCodeSubstitution(pre, varName, post) =>
            val newNode = SiteConfig.variables.value.get(varName).fold[Span](
              InvalidSpan(s"Unknown variable: '$varName'", laika.parse.GeneratedSource)
            ){ value => CodeSpan(pre + value + post, cats, opts) }
            Replace(newNode)
          case _ => Retain
        }
      }
    )
  }

  lazy val config: sbt.Def.Initialize[LaikaConfig] = sbt.Def.setting {
    laika.sbt.LaikaConfig(
      configBuilder = ConfigBuilder.empty
        .withValue(laika.config.Key.root, variables.value)
    )
  }

  val theme: ThemeProvider = Helium.defaults
    .site.markupEditLinks(
      text = "Edit this page",
      baseURL = "https://github.com/http4s/http4s/tree/main/docs/jvm/src/main/mdoc")
    .site.layout(
      contentWidth = px(860),
      navigationWidth = px(275),
      topBarHeight = px(35),
      defaultBlockSpacing = px(10),
      defaultLineHeight = 1.5,
      anchorPlacement = laika.helium.config.AnchorPlacement.Right
    )
    .build
}
