package fix

import scalafix.v1._
import scala.meta._

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

  private[this] def replaceWithBody(t: Tree) =
    t.collect{
      case Term.Select(_, t@Term.Name("withBody")) =>
        Patch.replaceTree(t, "withEntity")
    }.asPatch

  private[this] def containsWithBody(t: Tree): Boolean =
    t.collect {
      case Term.Select(_, Term.Name("withBody")) =>
        true
    }.contains(true)

  private[this] def removeExternalF(t: Type) =
    t match {
      case r@t"$a[Request[$b]]" =>
        // Note: we only change type def in request and not in response as normally the responses created with
        // e.g. Ok() are still F[Response[F]]
        Patch.replaceTree(r, s"Request[$b]")
      case _ =>
        Patch.empty
    }
}
