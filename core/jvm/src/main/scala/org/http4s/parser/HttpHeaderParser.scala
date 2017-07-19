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
import java.lang.reflect.InvocationTargetException
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
          case e: InvocationTargetException if e.getCause.isInstanceOf[ParseFailure] =>
            // TODO curse this runtime reflection
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

  private def gatherBuiltIn(): Unit =
    this.getClass.getMethods
      .filter(_.getName.forall(!_.isLower)) // only the header rules have no lower-case letter in their name
      .foreach { method =>
        val key = method.getName.replace('_', '-').ci
        val parser = { value: String =>
          method.invoke(this, value)
        }.asInstanceOf[HeaderParser]

        addParser(key, parser)
      }
}
