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

import scala.meta._

class RewriteBackticks extends SemanticRule("RewriteBackticks") {
  override def fix(implicit doc: SemanticDocument): Patch =
    replaceFields(
      "org/http4s/CacheDirective." -> "max-age" -> "maxAge",
      "org/http4s/CacheDirective." -> "max-stale" -> "maxStale",
      "org/http4s/CacheDirective." -> "min-fresh" -> "minFresh",
      "org/http4s/CacheDirective." -> "must-revalidate" -> "mustRevalidate",
      "org/http4s/CacheDirective." -> "no-cache" -> "noCache",
      "org/http4s/CacheDirective." -> "no-store" -> "noStore",
      "org/http4s/CacheDirective." -> "no-transform" -> "noTransform",
      "org/http4s/CacheDirective." -> "only-if-cached" -> "onlyIfCached",
      "org/http4s/CacheDirective." -> "proxy-revalidate" -> "proxyRevalidate",
      "org/http4s/CacheDirective." -> "s-maxage" -> "sMaxage",
      "org/http4s/CacheDirective." -> "stale-if-error" -> "staleIfError",
      "org/http4s/CacheDirective." -> "stale-while-revalidate" -> "staleWhileRevalidate",

      "org/http4s/HttpVersion." -> "HTTP/1.0" -> "Http1_0",
      "org/http4s/HttpVersion." -> "HTTP/1.1" -> "Http1_1",
      "org/http4s/HttpVersion." -> "HTTP/2.0" -> "Http2_0",
    )

  def replaceFields(renames: ((String, String), String)*)(implicit doc: SemanticDocument): Patch =
    renames.map { case ((className, oldField), newField) =>
      doc.tree.collect {
        case t: Term.Name if t.symbol.displayName == oldField && t.symbol.owner.value == className =>
          Patch.renameSymbol(t.symbol, newField)
      }.asPatch
    }.foldLeft(Patch.empty)(_ + _)
}
