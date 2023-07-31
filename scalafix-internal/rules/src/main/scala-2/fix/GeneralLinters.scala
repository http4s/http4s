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

class GeneralLinters extends SemanticRule("Http4sGeneralLinters") {
  override def fix(implicit doc: SemanticDocument): Patch =
    noFinalObject + noNonFinalCaseClass + leakingSealedHierarchy + nonValidatingCopyConstructor

  def noFinalObject(implicit doc: SemanticDocument) =
    doc.tree.collect { case o @ Defn.Object(mods, _, _) =>
      mods.collectFirst { case f: Mod.Final =>
        val finalToken = f.tokens.head
        val tokensToDelete = // we want to delete trailing whitespace after `final`
          finalToken :: o.tokens
            .dropWhile(_ != finalToken)
            .tail
            .takeWhile(_.text.forall(_.isWhitespace))
            .toList
        Patch.removeTokens(tokensToDelete)
      }.asPatch
    }.asPatch

  def noNonFinalCaseClass(implicit doc: SemanticDocument) =
    doc.tree.collect {
      case c @ Defn.Class.After_4_6_0(mods, _, _, _, _)
          if mods.exists(_.is[Mod.Case]) && !mods.exists(mod =>
            (mod.is[Mod.Final] | mod.is[Mod.Sealed] | mod.is[Mod.Private])
          ) && !c.isDescendentOf[Defn.Def] && !c.isDescendentOf[Defn.Val] =>
        Patch.lint(CaseClassWithoutAccessModifier(c))
    }.asPatch

  def leakingSealedHierarchy(implicit doc: SemanticDocument) = {
    def doCheck(t: Tree, mods: List[Mod], inits: List[Init]): Patch = inits.collect {
      case Init.After_4_6_0(typ, _, _) if typ.symbol.info.exists(_.isSealed) =>
        if (!mods.exists(mod => (mod.is[Mod.Final] | mod.is[Mod.Sealed] | mod.is[Mod.Private]))) {
          Patch.lint(LeakingSealedHierarchy(t))
        } else Patch.empty
    }.asPatch

    doc.tree.collect {
      case t @ Defn.Class.After_4_6_0(mods, _, _, _, Template.Initial(_, inits, _, _))
          if !t.isDescendentOf[Defn.Def] && !t.isDescendentOf[Defn.Val] =>
        doCheck(t, mods, inits)
      case t @ Defn.Trait.After_4_6_0(mods, _, _, _, Template.Initial(_, inits, _, _))
          if !t.isDescendentOf[Defn.Def] && !t.isDescendentOf[Defn.Val] =>
        doCheck(t, mods, inits)
    }.asPatch
  }

  def nonValidatingCopyConstructor(implicit doc: SemanticDocument) =
    doc.tree.collect {
      case c @ Defn.Class.After_4_6_0(mods, _, _, Ctor.Primary.After_4_6_0(ctorMods, _, _), _)
          if mods.exists(_.is[Mod.Case]) && ctorMods
            .exists(_.is[Mod.Private]) && !mods.exists(_.is[Mod.Abstract]) =>
        Patch.lint(NonValidatingCopyConstructor(c))
    }.asPatch
}

final case class CaseClassWithoutAccessModifier(c: Defn.Class) extends Diagnostic {
  override def message: String = "Case classes should be final, sealed, or private"

  override def position: Position = c.pos

  override def categoryID: String = "noCaseClassWithoutAccessModifier"
}

final case class LeakingSealedHierarchy(t: Tree) extends Diagnostic {
  override def message: String = "descendants of sealed traits should be sealed, final, or private"

  override def position: Position = t.pos

  override def categoryID: String = "leakingSealedHierarchy"
}

final case class NonValidatingCopyConstructor(c: Defn.Class) extends Diagnostic {
  override def message: String =
    "Case classes with private constructors should be abstract to prevent exposing a non-validating copy constructor"

  override def position: Position = c.pos

  override def categoryID: String = "nonValidatingCopyConstructor"
}
