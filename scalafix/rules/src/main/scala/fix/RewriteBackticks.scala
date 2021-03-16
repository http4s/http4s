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

      "org/http4s/Method." -> "ACL" -> "Acl",
      "org/http4s/Method." -> "BASELINE-CONTROL" -> "BaselineControl",
      "org/http4s/Method." -> "BIND" -> "Bind",
      "org/http4s/Method." -> "CHECKIN" -> "Checkin",
      "org/http4s/Method." -> "CHECKOUT" -> "Checkout",
      "org/http4s/Method." -> "CONNECT" -> "Connect",
      "org/http4s/Method." -> "COPY" -> "Copy",
      "org/http4s/Method." -> "DELETE" -> "Delete",
      "org/http4s/Method." -> "GET" -> "Get",
      "org/http4s/Method." -> "HEAD" -> "Head",
      "org/http4s/Method." -> "LABEL" -> "Label",
      "org/http4s/Method." -> "LINK" -> "Link",
      "org/http4s/Method." -> "LOCK" -> "Lock",
      "org/http4s/Method." -> "MERGE" -> "Merge",
      "org/http4s/Method." -> "MKACTIVITY" -> "MkActivity",
      "org/http4s/Method." -> "MKCALENDAR" -> "MkCalendar",
      "org/http4s/Method." -> "MKCOL" -> "MkCol",
      "org/http4s/Method." -> "MKREDIRECTREF" -> "MkRedirectRef",
      "org/http4s/Method." -> "MKWORKSPACE" -> "MkWorkspace",
      "org/http4s/Method." -> "MOVE" -> "Move",
      "org/http4s/Method." -> "OPTIONS" -> "Options",
      "org/http4s/Method." -> "ORDERPATCH" -> "OrderPatch",
      "org/http4s/Method." -> "PATCH" -> "Patch",
      "org/http4s/Method." -> "POST" -> "Post",
      "org/http4s/Method." -> "PRI" -> "Pri",
      "org/http4s/Method." -> "PROPFIND" -> "PropFind",
      "org/http4s/Method." -> "PROPPATCH" -> "PropPatch",
      "org/http4s/Method." -> "PUT" -> "Put",
      "org/http4s/Method." -> "REBIND" -> "Rebind",
      "org/http4s/Method." -> "REPORT" -> "Report",
      "org/http4s/Method." -> "SEARCH" -> "Search",
      "org/http4s/Method." -> "TRACE" -> "Trace",
      "org/http4s/Method." -> "UNBIND" -> "Unbind",
      "org/http4s/Method." -> "UNCHECKOUT" -> "Uncheckout",
      "org/http4s/Method." -> "UNLINK" -> "Unlink",
      "org/http4s/Method." -> "UNLOCK" -> "Unlock",
      "org/http4s/Method." -> "UPDATE" -> "Update",
      "org/http4s/Method." -> "UPDATEREDIRECTREF" -> "UpdateDirectRef",
      "org/http4s/Method." -> "VERSION-CONTROL" -> "VersionControl",

      "org/http4s/MediaRange." -> "*/*" -> "All",
      "org/http4s/MediaRange." -> "application/*" -> "AllApplications",
      "org/http4s/MediaRange." -> "audio/*" -> "AllAudio",
      "org/http4s/MediaRange." -> "image/*" -> "AllImages",
      "org/http4s/MediaRange." -> "message/*" -> "AllMessages",
      "org/http4s/MediaRange." -> "multipart/*" -> "AllMultipart",
      "org/http4s/MediaRange." -> "text/*" -> "AllText",
      "org/http4s/MediaRange." -> "video/*" -> "AllVideo",

      "org/http4s/TransferCoding." -> "chunked" -> "Chunked",
      "org/http4s/TransferCoding." -> "compress" -> "Compress",
      "org/http4s/TransferCoding." -> "deflate" -> "Deflate",
      "org/http4s/TransferCoding." -> "gzip" -> "Gzip",
      "org/http4s/TransferCoding." -> "identity" -> "Identity",
    )

  def replaceFields(renames: ((String, String), String)*)(implicit doc: SemanticDocument): Patch =
    renames.map { case ((className, oldField), newField) =>
      doc.tree.collect {
        case t: Term.Name if t.symbol.displayName == oldField && t.symbol.owner.value == className =>
          Patch.renameSymbol(t.symbol, newField)
      }.asPatch
    }.foldLeft(Patch.empty)(_ + _)
}
