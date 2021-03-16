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
      "org/http4s/CacheDirective." -> "max-age" -> "MaxAge",
      "org/http4s/CacheDirective." -> "max-stale" -> "MaxStale",
      "org/http4s/CacheDirective." -> "min-fresh" -> "MinFresh",
      "org/http4s/CacheDirective." -> "must-revalidate" -> "MustRevalidate",
      "org/http4s/CacheDirective." -> "no-cache" -> "NoCache",
      "org/http4s/CacheDirective." -> "no-store" -> "NoStore",
      "org/http4s/CacheDirective." -> "no-transform" -> "NoTransform",
      "org/http4s/CacheDirective." -> "only-if-cached" -> "OnlyIfCached",
      "org/http4s/CacheDirective." -> "private" -> "Private",
      "org/http4s/CacheDirective." -> "public" -> "Public",
      "org/http4s/CacheDirective." -> "proxy-revalidate" -> "ProxyRevalidate",
      "org/http4s/CacheDirective." -> "s-maxage" -> "SMaxage",
      "org/http4s/CacheDirective." -> "stale-if-error" -> "StaleIfError",
      "org/http4s/CacheDirective." -> "stale-while-revalidate" -> "StaleWhileRevalidate",

      "org/http4s/Charset." -> "US-ASCII" -> "UsAscii",
      "org/http4s/Charset." -> "ISO-8859-1" -> "Iso8859_1",
      "org/http4s/Charset." -> "UTF-8" -> "Utf8",
      "org/http4s/Charset." -> "UTF-16" -> "Utf16",
      "org/http4s/Charset." -> "UTF-16BE" -> "Utf16be",
      "org/http4s/Charset." -> "UTF-16LE" -> "Utf16le",

      "org/http4s/CharsetRange." -> "*" -> "All",

      "org/http4s/ContentCoding." -> "*" -> "All",
      "org/http4s/ContentCoding." -> "aes128gcm" -> "Aes128gcm",
      "org/http4s/ContentCoding." -> "br" -> "Br",
      "org/http4s/ContentCoding." -> "compress" -> "Compress",
      "org/http4s/ContentCoding." -> "deflate" -> "Deflate",
      "org/http4s/ContentCoding." -> "exi" -> "Exi",
      "org/http4s/ContentCoding." -> "gzip" -> "Gzip",
      "org/http4s/ContentCoding." -> "identity" -> "Identity",
      "org/http4s/ContentCoding." -> "pack200-gzip" -> "Pack200Gzip",
      "org/http4s/ContentCoding." -> "zstd" -> "Zstd",
      "org/http4s/ContentCoding." -> "x-compress" -> "XCompress",
      "org/http4s/ContentCoding." -> "x-gzip" -> "XGzip",

      "org/http4s/HttpVersion." -> "HTTP/1.0" -> "Http1_0",
      "org/http4s/HttpVersion." -> "HTTP/1.1" -> "Http1_1",
      "org/http4s/HttpVersion." -> "HTTP/2.0" -> "Http2_0",

      "org/http4s/LanguageTag." -> "*" -> "All",
    )

  def replaceFields(renames: ((String, String), String)*)(implicit doc: SemanticDocument): Patch =
    renames.map { case ((className, oldField), newField) =>
      doc.tree.collect {
        case t: Term.Name if t.symbol.displayName == oldField && t.symbol.owner.value == className =>
          Patch.renameSymbol(t.symbol, newField)
      }.asPatch
    }.foldLeft(Patch.empty)(_ + _)
}
