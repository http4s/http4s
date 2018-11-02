package fix

import scalafix.v1._
import scala.meta._

object CookiesRules {
  def unapply(t: Tree): Option[Patch] =  t match {
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
