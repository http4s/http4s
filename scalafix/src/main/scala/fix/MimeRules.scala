package fix

import scalafix.v1._
import scala.meta._

object MimeRules {
  val mimeMatcher = SymbolMatcher.normalized("org/http4s/MediaType#")

  def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = t match {
    case Term.Select(mimeMatcher(_), media) =>
      val mediaParts = media.toString.replace("`", "").split("/").map { part =>
        if (!part.forall(c => c.isLetterOrDigit || c == '_'))
          s"`$part`"
        else
          part
      }
      Some(Patch.replaceTree(media, mediaParts.mkString(".")))
    case _ => None
  }
}
