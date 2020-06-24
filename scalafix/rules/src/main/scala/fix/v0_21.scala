/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package fix

import scalafix.v1._

class v0_21 extends SemanticRule("v0_21") {
  override def fix(implicit doc: SemanticDocument): Patch =
    ClientFetchPatches(doc.tree).asPatch
}
