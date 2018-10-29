package fix

import scalafix.v1._
import scala.meta._

class Http4s018To020 extends SemanticRule("Http4s018To020") {

  val mimeMatcher = SymbolMatcher.normalized("org/http4s/MediaType#")

  override def fix(implicit doc: SemanticDocument): Patch = {
    doc.tree.collect{
      // HttpService -> HttpRoutes.of
      case t@Type.Name("HttpService") => Patch.replaceTree(t, "HttpRoutes")
      case t@Term.Name("HttpService") => Patch.replaceTree(t, "HttpRoutes.of")
      case t@Importee.Name(Name("HttpService")) => Patch.replaceTree(t, "HttpRoutes")

      // withBody -> withEntity
      case Defn.Val(_, _, tpe, rhs) if containsWithBody(rhs) =>
        replaceWithBody(rhs) ++ tpe.map(removeExternalF).toList
      case Defn.Def(_, _, _, _, tpe, rhs) if containsWithBody(rhs) =>
        replaceWithBody(rhs) ++ tpe.map(removeExternalF).toList
      case Defn.Var(_, _, tpe, rhs) if rhs.exists(containsWithBody) =>
        rhs.map(replaceWithBody).asPatch ++ tpe.map(removeExternalF).toList

      // cookies
      case Importer(Term.Select(Term.Name("org"), Term.Name("http4s")), is) =>
        is.collect{
          case c@Importee.Name(Name("Cookie")) =>
            Patch.addGlobalImport(Importer(Term.Select(Term.Name("org"), Term.Name("http4s")),
              List(Importee.Rename(Name("ResponseCookie"), Name("Cookie"))))) +
            Patch.removeImportee(c)
          case c@Importee.Rename(Name("Cookie"), rename) =>
            Patch.addGlobalImport(Importer(Term.Select(Term.Name("org"), Term.Name("http4s")),
              List(Importee.Rename(Name("ResponseCookie"), rename)))) +
              Patch.removeImportee(c)
        }.asPatch

      // MiMe types
      case Term.Select(mimeMatcher(t), media) =>
        val mediaParts = media.toString.replace("`", "").split("/").map{
          part =>
            if(!part.forall(c => c.isLetterOrDigit || c == '_'))
              s"`$part`"
            else
              part
        }
        Patch.replaceTree(media,
          mediaParts.mkString(".")
        )
    }
  }.asPatch

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
