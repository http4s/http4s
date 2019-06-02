/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/HttpParser.scala
 *
 * Copyright (C) 2011-2012 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s
package parser

import java.util
import org.http4s.util.CaseInsensitiveString
import org.http4s.Header.Parsed
import org.http4s.syntax.string._

object HttpHeaderParser
    extends SimpleHeaders
    with AcceptCharsetHeader
    with AcceptEncodingHeader
    with AcceptHeader
    with AcceptLanguageHeader
    with AuthorizationHeader
    with CacheControlHeader
    with ContentTypeHeader
    with CookieHeader
    with LinkHeader
    with LocationHeader
    with OriginHeader
    with ProxyAuthenticateHeader
    with RangeParser
    with RefererHeader
    with StrictTransportSecurityHeader
    with WwwAuthenticateHeader
    with ZipkinHeader {
  type HeaderParser = String => ParseResult[Parsed]

  private val allParsers =
    new util.concurrent.ConcurrentHashMap[CaseInsensitiveString, HeaderParser]

  // Constructor
  gatherBuiltIn()

  /** Add a parser to the global header parser registry
    *
    * @param key name of the header to register the parser for
    * @param parser [[Header]] parser
    * @return any existing parser already registered to that key
    */
  def addParser(key: CaseInsensitiveString, parser: HeaderParser): Option[HeaderParser] =
    Option(allParsers.put(key, parser))

  private def addParser_(key: CaseInsensitiveString, parser: HeaderParser): Unit = {
    addParser(key, parser)
    ()
  }

  /** Remove the parser for the specified header key
    *
    * @param key name of the header to be removed
    * @return `Some(parser)` if the parser exists, else `None`
    */
  def dropParser(key: CaseInsensitiveString): Option[HeaderParser] =
    Option(allParsers.remove(key))

  def parseHeader(header: Header.Raw): ParseResult[Header] =
    allParsers.get(header.name) match {
      case null =>
        ParseResult.success(header) // if we don't have a rule for the header we leave it unparsed
      case parser =>
        try parser(header.value)
        catch {
          // We need a way to bail on invalid dates without throwing.  There should be a better way.
          case _: ParseFailure =>
            ParseResult.success(header)
        }
    }

  /**
    * Warm up the header parsers by triggering the loading of most classes in this package,
    * so as to increase the speed of the first usage.
    */
  def warmUp(): Unit = {
    val results = List(
      Header("Accept", "*/*,text/plain,custom/custom"),
      Header("Accept-Charset", "*,UTF-8"),
      Header("Accept-Encoding", "gzip,custom"),
      Header("Accept-Language", "*,nl-be,custom"),
      Header("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="),
      Header("Cache-Control", "no-cache"),
      Header("Connection", "close"),
      Header("Content-Disposition", "form-data"),
      Header("Content-Encoding", "deflate"),
      Header("Content-Length", "42"),
      Header("Content-Type", "application/json"),
      Header("Cookie", "http4s=cool"),
      Header("Host", "http4s.org"),
      Header("X-Forwarded-For", "1.2.3.4"),
      Header("Fancy-Custom-Header", "yeah"),
      Header("Origin", "http://example.com:12345")
    ).map(parseHeader)
    assert(results.forall(_.isRight))
  }

  private def gatherBuiltIn(): Unit = {
    addParser_("ACCEPT".ci, `ACCEPT`)
    addParser_("ACCEPT-CHARSET".ci, `ACCEPT_CHARSET`)
    addParser_("ACCEPT-ENCODING".ci, `ACCEPT_ENCODING`)
    addParser_("ACCEPT-LANGUAGE".ci, `ACCEPT_LANGUAGE`)
    addParser_("ACCEPT-RANGES".ci, `ACCEPT_RANGES`)
    addParser_("AGE".ci, `AGE`)
    addParser_("ALLOW".ci, `ALLOW`)
    addParser_("AUTHORIZATION".ci, `AUTHORIZATION`)
    addParser_("CACHE-CONTROL".ci, `CACHE_CONTROL`)
    addParser_("CONNECTION".ci, `CONNECTION`)
    addParser_("CONTENT-DISPOSITION".ci, `CONTENT_DISPOSITION`)
    addParser_("CONTENT-ENCODING".ci, `CONTENT_ENCODING`)
    addParser_("CONTENT-LENGTH".ci, `CONTENT_LENGTH`)
    addParser_("CONTENT-RANGE".ci, `CONTENT_RANGE`)
    addParser_("CONTENT-TYPE".ci, `CONTENT_TYPE`)
    addParser_("COOKIE".ci, `COOKIE`)
    addParser_("DATE".ci, `DATE`)
    addParser_("ETAG".ci, `ETAG`)
    addParser_("EXPIRES".ci, `EXPIRES`)
    addParser_("HOST".ci, `HOST`)
    addParser_("IF-MATCH".ci, `IF_MATCH`)
    addParser_("IF-MODIFIED-SINCE".ci, `IF_MODIFIED_SINCE`)
    addParser_("IF-NONE-MATCH".ci, `IF_NONE_MATCH`)
    addParser_("IF-UNMODIFIED-SINCE".ci, `IF_UNMODIFIED_SINCE`)
    addParser_("LAST-EVENT-ID".ci, `LAST_EVENT_ID`)
    addParser_("LAST-MODIFIED".ci, `LAST_MODIFIED`)
    addParser_("LINK".ci, `LINK`)
    addParser_("LOCATION".ci, `LOCATION`)
    addParser_("ORIGIN".ci, `ORIGIN`)
    addParser_("PROXY-AUTHENTICATE".ci, `PROXY_AUTHENTICATE`)
    addParser_("RANGE".ci, `RANGE`)
    addParser_("REFERER".ci, `REFERER`)
    addParser_("RETRY-AFTER".ci, `RETRY_AFTER`)
    addParser_("SERVER".ci, `SERVER`)
    addParser_("SET-COOKIE".ci, `SET_COOKIE`)
    addParser_("STRICT-TRANSPORT-SECURITY".ci, `STRICT_TRANSPORT_SECURITY`)
    addParser_("TRANSFER-ENCODING".ci, `TRANSFER_ENCODING`)
    addParser_("USER-AGENT".ci, `USER_AGENT`)
    addParser_("WWW-AUTHENTICATE".ci, `WWW_AUTHENTICATE`)
    addParser_("X-B3-FLAGS".ci, `X_B3_FLAGS`)
    addParser_("X-B3-PARENTSPANID".ci, `X_B3_PARENTSPANID`)
    addParser_("X-B3-SAMPLED".ci, `X_B3_SAMPLED`)
    addParser_("X-B3-SPANID".ci, `X_B3_SPANID`)
    addParser_("X-B3-TRACEID".ci, `X_B3_TRACEID`)
    addParser_("X-FORWARDED-FOR".ci, `X_FORWARDED_FOR`)
  }
}
