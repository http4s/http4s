package fix

import scalafix.v1._
import scala.meta._

object HttpServiceRules {
  val httpService = SymbolMatcher.normalized("org/http4s/HttpService.")
  def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = t match {
    case httpService(Term.Apply(Term.ApplyType(t, _), _)) =>
      Some(Patch.replaceTree(t, "HttpRoutes.of"))
    case httpService(Term.Apply(t: Term.Name, _)) => Some(Patch.replaceTree(t, "HttpRoutes.of"))
    case Type.Apply(t @ Type.Name("HttpService"), _) => Some(Patch.replaceTree(t, "HttpRoutes"))
    case t @ Importee.Name(Name("HttpService")) => Some(Patch.replaceTree(t, "HttpRoutes"))
    case _ => None
  }
}
