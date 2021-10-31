/*
 * Copyright 2021 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fix

import scalafix.v1._

import scala.meta._
import scalafix.lint.LintSeverity

class GeneralLinters extends SemanticRule("Http4sGeneralLinters") {
  override def fix(implicit doc: SemanticDocument): Patch = noFinalCaseObject + noNonFinalCaseClass

  def noFinalCaseObject(implicit doc: SemanticDocument) =
    doc.tree.collect {
      case o @ Defn.Object(mods, _, _) if mods.exists(_.is[Mod.Case]) =>
        mods.collectFirst { case f: Mod.Final =>
          val finalToken = f.tokens.head
          val tokensToDelete = // we want to delete trailing whitespace after `final`
            finalToken :: o.tokens.dropWhile(_ != finalToken).tail.takeWhile(_.text.forall(_.isWhitespace)).toList
          Patch.removeTokens(tokensToDelete)
        }.asPatch
    }.asPatch

  def noNonFinalCaseClass(implicit doc: SemanticDocument) =
    doc.tree.collect {
      case c @ Defn.Class(mods, _, _, _, _)
          if mods.exists(_.is[Mod.Case]) && !mods.exists(mod =>
            (mod.is[Mod.Final] | mod.is[Mod.Sealed] | mod.is[Mod.Private])) && !c.isDescendentOf[Defn.Def] && !c.isDescendentOf[Defn.Val] =>
        Patch.lint(CaseClassWithoutAccessModifier(c))
    }.asPatch

}

final case class CaseClassWithoutAccessModifier(c: Defn.Class) extends Diagnostic {
  override def message: String = "Case classes should be final, sealed, or private"

  override def position: Position = c.pos

  // TODO: fix or exculde existing cases and change this to Error
  override def severity: LintSeverity = LintSeverity.Warning

  override def categoryID: String = "noCaseClassWithoutAccessModifier"
}
