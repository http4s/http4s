package fix

import scalafix.v1._
import scala.meta._

class Http4s018To020 extends SemanticRule("Http4s018To020") {

  override def fix(implicit doc: SemanticDocument): Patch = {
    doc.tree.collect{
      case HttpServiceRules(patch) => patch
      case WithBodyRules(patch) => patch
      case CookiesRules(patch) => patch
      case MimeRules(patch) => patch
    }
  }.asPatch

  object HttpServiceRules {
    def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = t match {
      case t@Type.Name("HttpService") => Some(Patch.replaceTree(t, "HttpRoutes"))
      case t@Term.Name("HttpService") => Some(Patch.replaceTree(t, "HttpRoutes.of"))
      case t@Importee.Name(Name("HttpService")) => Some(Patch.replaceTree(t, "HttpRoutes"))
      case _ => None
    }
  }

  object WithBodyRules {
    def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = t match {
      case Defn.Val(_, _, tpe, rhs) if containsWithBody(rhs) =>
        Some(replaceWithBody(rhs) + tpe.map(removeExternalF))
      case Defn.Def(_, _, _, _, tpe, rhs) if containsWithBody(rhs) =>
        Some(replaceWithBody(rhs) + tpe.map(removeExternalF))
      case Defn.Var(_, _, tpe, rhs) if rhs.exists(containsWithBody) =>
        Some(rhs.map(replaceWithBody).asPatch + tpe.map(removeExternalF))
      case _ => None
    }
  }

  object CookiesRules {
    def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] =  t match {
      case Importer(Term.Select(Term.Name("org"), Term.Name("http4s")), is) =>
        Some(is.collect {
          case c@Importee.Name(Name("Cookie")) =>
            Patch.addGlobalImport(Importer(Term.Select(Term.Name("org"), Term.Name("http4s")),
              List(Importee.Rename(Name("ResponseCookie"), Name("Cookie"))))) +
              Patch.removeImportee(c)
          case c@Importee.Rename(Name("Cookie"), rename) =>
            Patch.addGlobalImport(Importer(Term.Select(Term.Name("org"), Term.Name("http4s")),
              List(Importee.Rename(Name("ResponseCookie"), rename)))) +
              Patch.removeImportee(c)
        }.asPatch)
      case _ => None
    }
  }

  object MimeRules {
    val mimeMatcher = SymbolMatcher.normalized("org/http4s/MediaType#")

    def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = t match {
      case Term.Select(mimeMatcher(_), media) =>
        val mediaParts = media.toString.replace("`", "").split("/").map{
          part =>
            if(!part.forall(c => c.isLetterOrDigit || c == '_'))
              s"`$part`"
            else
              part
        }
        Some(Patch.replaceTree(media,
          mediaParts.mkString(".")
        ))
      case _ => None
    }
  }

  def removeExternalF(t: Type) =
    t match {
      case r@t"$a[Request[$b]]" =>
        // Note: we only change type def in request and not in response as normally the responses created with
        // e.g. Ok() are still F[Response[F]]
        Patch.replaceTree(r, s"Request[$b]")
      case _ =>
        Patch.empty
    }

  def replaceWithBody(t: Tree) =
    t.collect{
      case Term.Select(p, t@Term.Name("withBody")) =>
        Patch.replaceTree(t, "withEntity")
    }.asPatch

  def containsWithBody(t: Tree): Boolean =
    t.collect {
      case Term.Select(_, Term.Name("withBody")) =>
        true
    }.contains(true)

}
