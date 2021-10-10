package org.http4s.sbt

import cats.effect.{Resource, Sync}
import laika.ast._
import laika.bundle.ExtensionBundle
import laika.factory.Format
import laika.format.HTML
import laika.io.model.InputTree
import laika.parse.code.CodeCategory
import laika.render.HTMLFormatter
import laika.theme.Theme.TreeProcessor
import laika.theme.{Theme, ThemeBuilder, ThemeProvider, TreeProcessorBuilder}

object HeliumExtensions {

  def treeProcessor[F[_]: Sync]: TreeProcessor[F] = TreeProcessorBuilder[F].mapTree { tree =>

    val indexName = "README.md"

    def transformTree(tree: DocumentTree): DocumentTree = tree.copy(content = tree.content.map {
      case childTree: DocumentTree => transformTree(childTree)
      case doc: Document if doc.path.name == indexName => doc
      case doc: Document =>
        val treePath = doc.path.withoutSuffix
        DocumentTree(treePath, Seq(doc.copy(path = treePath / indexName)))
    })

    tree.copy(root = tree.root.copy(tree = transformTree(tree.root.tree)))
  }

  def adjustForPrettyURL(target: Target): Target = {

    def translateAbs (path: Path): Path =
      if (path.basename == "README" || !path.suffix.contains("md")) path
      else path.withoutSuffix.withoutFragment / ("index.md" + path.fragment.fold("")("#"+_))

    def translateRel (path: RelativePath): RelativePath =
      if (path.basename == "README" || !path.suffix.contains("md")) path
      else path.withoutSuffix.withoutFragment / ("index.md" + path.fragment.fold("")("#"+_))

    target match {
      case ext: ExternalTarget => ext
      case it: InternalTarget => it match {
        case resIt: ResolvedInternalTarget => resIt.copy(
          absolutePath = translateAbs(resIt.absolutePath),
          relativePath = translateRel(resIt.relativePath)
        )
        case relIt: RelativeInternalTarget => relIt.copy(path = translateRel(relIt.path))
        case absIt: AbsoluteInternalTarget => absIt.copy(path = translateAbs(absIt.path))
      }
    }
  }

  def renderTarget (fmt: HTMLFormatter, target: Target): String =
    fmt.pathTranslator.translate(adjustForPrettyURL(target)) match {
      case ext: ExternalTarget if ext.url.startsWith("http:mailto:") => ext.url.stripPrefix("http:") // remove this when 0.18.1 is out
      case ext: ExternalTarget => ext.url
      case int: InternalTarget => int.relativeTo(fmt.path).relativePath.toString.replace("index.html","")
    }

  val renderOverride: PartialFunction[(HTMLFormatter, Element), String] = {
    case (fmt, SpanLink(content, target, None, opt)) =>
      fmt.element("a", opt, content, "href" -> renderTarget(fmt, target))
    case (_, bli: BulletListItem) if bli.hasStyle("nav-header") => "" // skip fake-directories created for pretty-print URLs
    //case (_, bli: BulletListItem) => println(bli.options.styles.mkString(",")); ""
  }

  private val LaikaCodeSubstitution = "(.*)@\\{(.*)}(.*)".r

  def codeBlockVariables (variables: Map[String, String]): RewriteRules = RewriteRules.forSpans {
    // Laika currently does not do variable substitutions in code blocks, this serves as a temporary workaround.
    // This will potentially be supported out of the box in the 0.19 series, but requires some significant work
    // due to issues with processing order (variable substitutions normally run after highlighters which would
    // be the wrong order - Laika's default variable substitution format `${...}` also conflicts with Scala code.
    case CodeSpan(content, cats, opts) if cats.contains(CodeCategory.StringLiteral) => content match {
      case LaikaCodeSubstitution(pre, varName, post) =>
        val newNode = variables.get(varName).fold[Span](
          InvalidSpan(s"Unknown variable: '$varName'", laika.parse.GeneratedSource)
        ){ value => CodeSpan(pre + value + post, cats, opts) }
        Replace(newNode)
      case _ => Retain
    }
  }

  def applyTo(helium: ThemeProvider, variables: Map[String, String], versionLinks: Seq[Path]): ThemeProvider = new ThemeProvider {

    override def build[F[_]: Sync]: Resource[F, Theme[F]] = {
      val heliumTheme = helium.build[F]
      val extensionTheme = ThemeBuilder[F]("Extensions for http4s")
        .processTree(treeProcessor, HTML)
        .addRenderOverrides(HTML.Overrides(renderOverride))
        .addRewriteRules(codeBlockVariables(variables))
        .addInputs(InputTree[F].addProvidedPaths(versionLinks))
        .build
      for {
        hel <- heliumTheme
        ext <- extensionTheme
      } yield new Theme[F] {
        override def inputs: InputTree[F] = hel.inputs ++ ext.inputs
        override def extensions: Seq[ExtensionBundle] = hel.extensions ++ ext.extensions
        override def treeProcessor: Format => TreeProcessor[F] = fmt =>
          hel.treeProcessor(fmt).andThen(ext.treeProcessor(fmt))
      }
    }
  }

}
