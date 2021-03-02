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
    addParser_(CIString("LAST-MODIFIED"), `Last-Modified`.parse)
    addParser_(CIString("LOCATION"), Location.parse)
    addParser_(CIString("MAX-FORWARDS"), `Max-Forwards`.parse)
    addParser_(CIString("ORIGIN"), Origin.parse)
    addParser_(CIString("RANGE"), Range.parse)
  }
}
