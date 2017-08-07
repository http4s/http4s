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

import org.http4s.headers._
import org.http4s.util.CaseInsensitiveString

import Header.Parsed
import org.http4s.syntax.string._


object HttpHeaderParser extends SimpleHeaders
                    with AcceptHeader
                    with AcceptLanguageHeader
                    with CacheControlHeader
                    with ContentTypeHeader
                    with CookieHeader
                    with AcceptCharsetHeader
                    with AcceptEncodingHeader
                    with AuthorizationHeader
                    with RangeParser
                    with LocationHeader
                    with RefererHeader
                    with ProxyAuthenticateHeader
                    with WwwAuthenticateHeader
                    with ZipkinHeader {

  type HeaderParser = String => ParseResult[Parsed]

  private val allParsers = new util.concurrent.ConcurrentHashMap[FieldName, HeaderParser]


  // Constructor
  gatherBuiltIn()

  /** Add a parser to the global header parser registry
    *
    * @param key name of the header to register the parser for
    * @param parser [[Header]] parser
    * @return any existing parser already registered to that key
    */
  def addParser(key: FieldName, parser: HeaderParser): Option[HeaderParser] =
    Option(allParsers.put(key, parser))


  /** Remove the parser for the specified header key
    *
    * @param key name of the header to be removed
    * @return `Some(parser)` if the parser exists, else `None`
    */
  def dropParser(key: CaseInsensitiveString): Option[HeaderParser] =
    Option(allParsers.remove(key))

  def parseHeader(header: Header.Raw): ParseResult[Header] = {
    allParsers.get(header.name) match {
      case null => ParseResult.success(header) // if we don't have a rule for the header we leave it unparsed
      case parser => parser(header.value.renderString)
    }
  }

  /**
   * Warm up the header parsers by triggering the loading of most classes in this package,
   * so as to increase the speed of the first usage.
   */
  def warmUp(): Unit = {
    val results = List(
      "Accept" -> "*/*,text/plain,custom/custom",
      "Accept-Charset" -> "*,UTF-8",
      "Accept-Encoding" -> "gzip,custom",
      "Accept-Language" -> "*,nl-be,custom",
      "Authorization" -> "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==",
      "Cache-Control" -> "no-cache",
      "Connection" -> "close",
      "Content-Disposition" -> "form-data",
      "Content-Encoding" -> "deflate",
      "Content-Length" -> "42",
      "Content-Type" -> "application/json",
      "Cookie" -> "http4s=cool",
      "Host" -> "http4s.org",
      "X-Forwarded-For" -> "1.2.3.4",
      "Fancy-Custom-Header" -> "yeah"
    ) map { case (k, v) => parseHeader(Header(FieldName.unsafeFromString(k), FieldValue.unsafeFromString(v))) }
    assert(results.forall(_.isRight))
  }

  private def gatherBuiltIn(): Unit =
    this
      .getClass
      .getMethods
      .filter(_.getName.forall(!_.isLower)) // only the header rules have no lower-case letter in their name
      .foreach { method =>
      val key = FieldName.unsafeFromString(method.getName.replace('_', '-'))
      val parser ={ value: String =>
        method.invoke(this, value)
      }.asInstanceOf[HeaderParser]

      addParser(key, parser)
    }
}
