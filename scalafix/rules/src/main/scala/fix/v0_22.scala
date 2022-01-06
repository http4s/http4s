/*
 * Copyright 2018 http4s.org
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

import scala.meta.Token._
import scala.meta._
import scala.xml.transform.RewriteRule

class v0_22 extends SemanticRule("v0_22") {
  override def fix(implicit doc: SemanticDocument): Patch =
    rewritePackages + rewriteCIString + rewriteHeaders + rewriteUri

  def rewritePackages(implicit doc: SemanticDocument): Patch =
    Patch.replaceSymbols(
      "org.http4s.server.tomcat" -> "org.http4s.tomcat.server",
      "org.http4s.server.jetty" -> "org.http4s.jetty.server",
      "org.http4s.client.jetty" -> "org.http4s.jetty.client",
      "org.http4s.client.okhttp" -> "org.http4s.okhttp.client",
      "org.http4s.client.asynchttpclient" -> "org.http4s.asynchttpclient.client",
      "org.http4s.client.blaze" -> "org.http4s.blaze.client",
      "org.http4s.server.blaze" -> "org.http4s.blaze.server"
    )

  def rewriteCIString(implicit doc: SemanticDocument): Patch =
    Patch.replaceSymbols(
      "org.http4s.util.CaseInsensitiveString" -> "org.typelevel.ci.CIString"
    ) + doc.tree.collect {
      case t @ Term.Select(name, CaseInsensitiveString_value_M(_)) => 
        Patch.replaceTree(t, s"${name.syntax}.toString")
      case t @ Term.Select(s, StringOps_ci_M(_)) => 
        makeCI(t, s)
      case _ => Patch.empty
    }.asPatch

  def makeCI(t: Term, s: Term): Patch =
    Patch.addGlobalImport(CIWildcard) + Patch.replaceTree(t, s match {
      case s @ Lit.String(_) => s"ci$s"
      case _ => s"${CIString_S.displayName}($s)"
    })

  val CaseInsensitiveString_value_M = SymbolMatcher.exact("org/http4s/util/CaseInsensitiveString#value.")
  val StringOps_ci_M = SymbolMatcher.exact("org/http4s/syntax/StringOps#ci().")

  val CIString_S = Symbol("org/typelevel/ci/CIString#")

  val CIWildcard = Importer(q"org.typelevel.ci", List(Importee.Wildcard()))

  def rewriteHeaders(implicit doc: SemanticDocument) =
    doc.tree.collect {
      // ci-ify header name
      case Term.Apply(Header_M(_), List(k, _)) =>
        makeCI(k, k)
      // `Header` => `Header.Raw` 
      case t @ Header_M(Type.Name(_) | Term.Name(_)) => 
        Patch.replaceTree(t, s"${Header_S.displayName}.Raw")
      // `Headers.of` => `Headers`
      case t @ Term.Apply(fun@Headers_of_M(_), args) =>
        Patch.replaceTree(t, s"${Headers_S.displayName}(${args.mkString(", ")})")

      case t @ AgentProduct_M(Type.Name(_) | Term.Name(_)) => 
        Patch.addGlobalImport(ProductId_S) + 
          Patch.replaceTree(t, ProductId_S.displayName)
      case AgentProduct_M(imp: Importee) =>
        Patch.removeImportee(imp)

    }.asPatch

  val Header_M = SymbolMatcher.normalized("org/http4s/Header.")
  val Header_S = Symbol("org/http4s/Header#")

  val Headers_of_M = SymbolMatcher.exact("org/http4s/Headers.of().")
  val Headers_S = Symbol("org/http4s/Headers#")

  val AgentProduct_M = SymbolMatcher.exact("org/http4s/headers/AgentProduct.")
  val AgentProduct_S = Symbol("org/http4s/headers/AgentProduct.")
  val ProductId_S = Symbol("org.http4s.ProductId#")

  def rewriteUri(implicit doc: SemanticDocument) = 
    doc.tree.collect {
      case t @ Term.Apply(Uri_M(_), args) =>
        val path: Option[Term] =
          args match {
            case _ :: _ :: (path @ Lit.String(_)) :: _ => Some(path)
            case _ => args.collectFirst { case Term.Assign(Term.Name("path"), path @ Lit.String(_)) => path }
          }
        path.fold(Patch.empty){ path =>
          Patch.addGlobalImport(LiteralsSyntaxWildcard) + 
            Patch.replaceTree(path, s"path$path")
        }
    }.asPatch

  val Uri_M = SymbolMatcher.exact("org/http4s/Uri.")

  val LiteralsSyntaxWildcard = Importer(q"org.http4s.syntax.literals", List(Importee.Wildcard()))
}
