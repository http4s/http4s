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

class v0_22 extends SemanticRule("v0_22") {
  override def fix(implicit doc: SemanticDocument): Patch =
    rewritePackages + rewriteCIString

  def rewritePackages(implicit doc: SemanticDocument): Patch =
    Patch.replaceSymbols(
      "org.http4s.server.tomcat" -> "org.http4s.tomcat.server",
      "org.http4s.server.jetty" -> "org.http4s.jetty.server",
      "org.http4s.client.jetty" -> "org.http4s.jetty.client",
      "org.http4s.client.okhttp" -> "org.http4s.okhttp.client",
      "org.http4s.client.asynchttpclient" -> "org.http4s.asynchttpclient",
      "org.http4s.client.blaze" -> "org.http4s.blaze.client",
      "org.http4s.server.blaze" -> "org.http4s.blaze.server"
    )

  def rewriteCIString(implicit doc: SemanticDocument): Patch =
    Patch.replaceSymbols(
      "org.http4s.util.CaseInsensitiveString" -> "org.typelevel.ci.CIString"
    ) + doc.tree.collect {
      case t @ Term.Select(name, CaseInsensitiveString_value_M(_)) => 
        Patch.replaceTree(t, s"${name.syntax}.toString")
      case t @ Term.Select(s@Lit.String(_), StringOps_ci_M(_)) =>
        Patch.addGlobalImport(CIWildcard) + Patch.replaceTree(t, s"ci$s")
      case t @ Term.Select(s, StringOps_ci_M(_)) =>
        Patch.addGlobalImport(CIWildcard) + Patch.replaceTree(t, s"${CIString_S.displayName}($s)")
      case _ => Patch.empty
    }.asPatch

  val CaseInsensitiveString_value_M = SymbolMatcher.exact("org/http4s/util/CaseInsensitiveString#value.")
  val StringOps_ci_M = SymbolMatcher.exact("org/http4s/syntax/StringOps#ci().")

  val CIString_S = Symbol("org/typelevel/ci/CIString#")

  val CIWildcard = Importer(q"org.typelevel.ci", List(Importee.Wildcard()))
}
