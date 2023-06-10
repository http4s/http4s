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

class UseLiteralsSyntax extends SemanticRule("Http4sUseLiteralsSyntax") {
  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect {
      case t @ Term.Apply.Initial(
            Uri_unsafeFromString_M(_),
            List(lit @ Lit.String(_))
          ) =>
        Patch.replaceTree(t, s"uri$lit") + importLiteralsIfNeeded
      case t @ Term.Apply.Initial(
            Path_unsafeFromString_M(_),
            List(lit @ Lit.String(_))
          ) =>
        Patch.replaceTree(t, s"path$lit") + importLiteralsIfNeeded
      case t if t.syntax == """Uri.Path.unsafeFromString("foo/bar")""" => show(t)
    }.asPatch

  val Uri_unsafeFromString_M = SymbolMatcher.exact("org/http4s/Uri.unsafeFromString().")
  val Path_unsafeFromString_M = SymbolMatcher.exact("org/http4s/Uri.Path.unsafeFromString().")

  def importLiteralsIfNeeded(implicit doc: SemanticDocument) =
    if (!hasLiteralsImport) Patch.addGlobalImport(importer"org.http4s.syntax.literals._")
    else Patch.empty

  // yuck
  def hasLiteralsImport(implicit doc: SemanticDocument) = doc.tree
    .collect {
      case Importer(
            Term.Select(
              Term.Select(Term.Name("org"), Term.Name("http4s")),
              Term.Name("implicits")
            ),
            List(Importee.Wildcard())) | Importer(
            Term.Select(
              Term.Select(
                Term.Select(Term.Name("org"), Term.Name("http4s")),
                Term.Name("syntax")
              ),
              Term.Name("all" | "literals")
            ),
            List(Importee.Wildcard())
          ) =>
        true
      case _ => false
    }
    .contains(true)
}
