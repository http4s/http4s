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

object HttpHeaderParser
    extends SimpleHeaders
    with CacheControlHeader
    with ContentLanguageHeader
    with ContentLocationHeader
    with ForwardedHeader
    with LocationHeader
    with RefererHeader
    with StrictTransportSecurityHeader {
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
    addParser_(CIString("ACCEPT"), Accept.parse)
    addParser_(CIString("ACCEPT-CHARSET"), `Accept-Charset`.parse)
    addParser_(CIString("ACCEPT-ENCODING"), `Accept-Encoding`.parse)
    addParser_(CIString("ACCEPT-LANGUAGE"), `Accept-Language`.parse)
    addParser_(CIString("ACCEPT-PATCH"), `ACCEPT_PATCH`)
    addParser_(CIString("ACCEPT-RANGES"), `Accept-Ranges`.parse)
    addParser_(CIString("ACCESS-CONTROL-ALLOW-CREDENTIALS"), `ACCESS_CONTROL_ALLOW_CREDENTIALS`)
    addParser_(CIString("ACCESS-CONTROL-ALLOW-HEADERS"), `ACCESS_CONTROL_ALLOW_HEADERS`)
    addParser_(CIString("ACCESS-CONTROL-EXPOSE-HEADERS"), `ACCESS_CONTROL_EXPOSE_HEADERS`)
    addParser_(CIString("AGE"), `AGE`)
    addParser_(CIString("ALLOW"), `ALLOW`)
    addParser_(CIString("AUTHORIZATION"), Authorization.parse)
    addParser_(CIString("CACHE-CONTROL"), `CACHE_CONTROL`)
    addParser_(CIString("CONNECTION"), `CONNECTION`)
    addParser_(CIString("CONTENT-DISPOSITION"), `CONTENT_DISPOSITION`)
    addParser_(CIString("CONTENT-ENCODING"), `CONTENT_ENCODING`)
    addParser_(CIString("CONTENT-LANGUAGE"), `CONTENT_LANGUAGE`)
    addParser_(CIString("CONTENT-LENGTH"), `CONTENT_LENGTH`)
    addParser_(CIString("CONTENT-LOCATION"), `CONTENT_LOCATION`)
    addParser_(CIString("CONTENT-RANGE"), `Content-Range`.parse)
    addParser_(CIString("CONTENT-TYPE"), `Content-Type`.parse)
    addParser_(CIString("COOKIE"), Cookie.parse)
    addParser_(CIString("DATE"), Date.parse)
    addParser_(CIString("ETAG"), ETag.parse)
    addParser_(CIString("EXPIRES"), Expires.parse)
    addParser_(CIString("FORWARDED"), `FORWARDED`)
    addParser_(CIString("HOST"), `HOST`)
    addParser_(CIString("IF-MATCH"), `IF_MATCH`)
    addParser_(CIString("IF-MODIFIED-SINCE"), `If-Modified-Since`.parse)
    addParser_(CIString("IF-NONE-MATCH"), `IF_NONE_MATCH`)
    addParser_(CIString("IF-UNMODIFIED-SINCE"), `If-Unmodified-Since`.parse)
    addParser_(CIString("LAST-EVENT-ID"), `LAST_EVENT_ID`)
    addParser_(CIString("LAST-MODIFIED"), `Last-Modified`.parse)
    addParser_(CIString("LINK"), Link.parse)
    addParser_(CIString("LOCATION"), `LOCATION`)
    addParser_(CIString("MAX-FORWARDS"), `MAX_FORWARDS`)
    addParser_(CIString("ORIGIN"), Origin.parse)
    //addParser_(CIString("PROXY-AUTHENTICATE"), `PROXY_AUTHENTICATE`)
    addParser_(CIString("RANGE"), Range.parse)
    addParser_(CIString("REFERER"), `REFERER`)
    addParser_(CIString("RETRY-AFTER"), `Retry-After`.parse)
    addParser_(CIString("SERVER"), `SERVER`)
    addParser_(CIString("SET-COOKIE"), `Set-Cookie`.parse)
    addParser_(CIString("STRICT-TRANSPORT-SECURITY"), `STRICT_TRANSPORT_SECURITY`)
    addParser_(CIString("TRANSFER-ENCODING"), `Transfer-Encoding`.parse)
    addParser_(CIString("USER-AGENT"), `USER_AGENT`)
    addParser_(CIString("WWW-AUTHENTICATE"), `WWW-Authenticate`.parse)
    addParser_(CIString("X-B3-FLAGS"), `X-B3-Flags`.parse)
    addParser_(CIString("X-B3-PARENTSPANID"), `X-B3-ParentSpanId`.parse)
    addParser_(CIString("X-B3-SAMPLED"), `X-B3-Sampled`.parse)
    addParser_(CIString("X-B3-SPANID"), `X-B3-SpanId`.parse)
    addParser_(CIString("X-B3-TRACEID"), `X-B3-TraceId`.parse)
    addParser_(CIString("X-FORWARDED-FOR"), `X_FORWARDED_FOR`)
  }
}
