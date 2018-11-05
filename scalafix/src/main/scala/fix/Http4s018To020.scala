package fix

import scalafix.v1._
import scala.meta._

class Http4s018To020 extends SemanticRule("Http4s018To020") {

  override def fix(implicit doc: SemanticDocument): Patch = {
    doc.tree.collect {
      case HttpServiceRules(patch) => patch
      case WithBodyRules(patch) => patch
      case CookiesRules(patch) => patch
      case MimeRules(patch) => patch
      case ClientRules(patch) => patch
    }
  }.asPatch

}
