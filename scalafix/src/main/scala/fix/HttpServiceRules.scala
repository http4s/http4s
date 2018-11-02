package fix

import scalafix.v1._
import scala.meta._

object HttpServiceRules {
  def unapply(t: Tree): Option[Patch] = t match {
    case t@Type.Name("HttpService") => Some(Patch.replaceTree(t, "HttpRoutes"))
    case t@Term.Name("HttpService") => Some(Patch.replaceTree(t, "HttpRoutes.of"))
    case t@Importee.Name(Name("HttpService")) => Some(Patch.replaceTree(t, "HttpRoutes"))
    case _ => None
  }
}
