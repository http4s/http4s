/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/HttpParser.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s
package parser

import java.util
import org.http4s.Header.Parsed
import org.http4s.headers._
import org.typelevel.ci.CIString

object HttpHeaderParser {
  type HeaderParser = String => ParseResult[Parsed]

  private val allParsers =
    new util.concurrent.ConcurrentHashMap[CIString, HeaderParser]

  // Constructor
  gatherBuiltIn()

  /** Add a parser to the global header parser registry
    *
    * @param key name of the header to register the parser for
    * @param parser [[Header]] parser
    * @return any existing parser already registered to that key
    */
  def addParser(key: CIString, parser: HeaderParser): Option[HeaderParser] =
    Option(allParsers.put(key, parser))

  private def addParser_(key: CIString, parser: HeaderParser): Unit = {
    addParser(key, parser)
    ()
  }

  /** Remove the parser for the specified header key
    *
    * @param key name of the header to be removed
    * @return `Some(parser)` if the parser exists, else `None`
    */
  def dropParser(key: CIString): Option[HeaderParser] =
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

  /** Warm up the header parsers by triggering the loading of most classes in this package,
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
    addParser_(CIString("ACCEPT-ENCODING"), `Accept-Encoding`.parse)
    addParser_(CIString("ACCEPT-RANGES"), `Accept-Ranges`.parse)
    addParser_(
      CIString("ACCESS-CONTROL-ALLOW-CREDENTIALS"),
      `Access-Control-Allow-Credentials`.parse)
    addParser_(CIString("AGE"), Age.parse)
    addParser_(CIString("ALLOW"), Allow.parse)
    addParser_(CIString("AUTHORIZATION"), Authorization.parse)
    addParser_(CIString("CONNECTION"), Connection.parse)
    addParser_(CIString("CONTENT-ENCODING"), `Content-Encoding`.parse)
    addParser_(CIString("CONTENT-LENGTH"), `Content-Length`.parse)
    addParser_(CIString("CONTENT-RANGE"), `Content-Range`.parse)
    addParser_(CIString("CONTENT-TYPE"), `Content-Type`.parse)
    addParser_(CIString("DATE"), Date.parse)
    addParser_(CIString("ETAG"), ETag.parse)
    addParser_(CIString("EXPIRES"), Expires.parse)
    addParser_(CIString("HOST"), Host.parse)
    addParser_(CIString("IF-MATCH"), `If-Match`.parse)
    addParser_(CIString("IF-MODIFIED-SINCE"), `If-Modified-Since`.parse)
    addParser_(CIString("IF-NONE-MATCH"), `If-None-Match`.parse)
    addParser_(CIString("IF-UNMODIFIED-SINCE"), `If-Unmodified-Since`.parse)
    addParser_(CIString("LAST-EVENT-ID"), `Last-Event-Id`.parse)
    addParser_(CIString("LAST-MODIFIED"), `Last-Modified`.parse)
    addParser_(CIString("LOCATION"), Location.parse)
    addParser_(CIString("MAX-FORWARDS"), `Max-Forwards`.parse)
    addParser_(CIString("ORIGIN"), Origin.parse)
    addParser_(CIString("RANGE"), Range.parse)
    addParser_(CIString("REFERER"), Referer.parse)
    addParser_(CIString("RETRY-AFTER"), `Retry-After`.parse)
    addParser_(CIString("SERVER"), Server.parse)
    addParser_(CIString("SET-COOKIE"), `Set-Cookie`.parse)
    addParser_(CIString("STRICT-TRANSPORT-SECURITY"), `Strict-Transport-Security`.parse)
    addParser_(CIString("TRANSFER-ENCODING"), `Transfer-Encoding`.parse)
    addParser_(CIString("USER-AGENT"), `User-Agent`.parse)
    addParser_(CIString("X-B3-FLAGS"), `X-B3-Flags`.parse)
    addParser_(CIString("X-B3-PARENTSPANID"), `X-B3-ParentSpanId`.parse)
    addParser_(CIString("X-B3-SAMPLED"), `X-B3-Sampled`.parse)
    addParser_(CIString("X-B3-SPANID"), `X-B3-SpanId`.parse)
    addParser_(CIString("X-B3-TRACEID"), `X-B3-TraceId`.parse)
  }
}
