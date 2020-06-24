package fix

import scalafix.v1._

class v0_21 extends SemanticRule("v0_21") {
  override def fix(implicit doc: SemanticDocument): Patch = {
    ClientFetchPatches(doc.tree)
  }.asPatch
}
