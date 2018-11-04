package fix

import scalafix.v1._
import scala.meta._

object MimeRules {
  private[this] val mimeMatcher = SymbolMatcher.normalized("org/http4s/MediaType#")

  def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = {
    val symbol = t.symbol
    symbol.owner match {
      case mimeMatcher(_) =>
        val mediaParts = symbol.displayName.replace("`", "").split("/").map { part =>
          if (!part.forall(c => c.isLetterOrDigit || c == '_'))
            s"`$part`"
          else
            part
        }
        val newSymbol = Symbol(s"${symbol.owner.value}${mediaParts.init.mkString("/")}#")

        Some(
          Patch.renameSymbol(t.symbol, mediaParts.mkString(".")) + Patch.addGlobalImport(newSymbol))
      case _ => None
    }
  }
}
