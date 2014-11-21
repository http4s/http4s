/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/QueryParser.scala
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

import org.parboiled2._
import java.io.UnsupportedEncodingException
import scala.io.Codec
import org.http4s.parser.QueryParser._
import org.http4s.util.string._
import org.parboiled2.CharPredicate._
import org.parboiled2.ParseError

// TODO: this could be made more efficient. For a good example, look at the Jetty impl
// https://github.com/eclipse/jetty.project/blob/release-9/jetty-util/src/main/java/org/eclipse/jetty/util/UrlEncoded.java

private[parser] class QueryParser(val input: ParserInput, codec: Codec) extends Parser {

  def charset = codec.charSet

  def QueryString: Rule1[Seq[Param]] = rule {
      EOI ~ push(Seq.empty[Param]) |
      zeroOrMore(QueryParameter).separatedBy("&") ~ EOI
  }

  def QueryParameter: Rule1[Param] = rule {
    capture(zeroOrMore(!anyOf("&=") ~ QChar)) ~ optional('=' ~ capture(zeroOrMore(!anyOf("&") ~ QChar))) ~> {
      (k: String, v: Option[String]) => (decodeParam(k), v.map(decodeParam(_)))
    }
  }

  private def decodeParam(str: String): String =
    try str.formDecode(codec)
    catch {
        case e: IllegalArgumentException     => ""
        case e: UnsupportedEncodingException => ""
    }

  def QChar = rule { !'&' ~ (Pchar | '/' | '?') }

  // Different than the RFC 3986 in that it lacks % encoded requirements.
  def Pchar = rule { Unreserved | SubDelims | ":" | "@" | "%" }

  def Unreserved = rule { Alpha | Digit | "-" | "." | "_" | "~" }

  def SubDelims = rule { "!" | "$" | "&" | "'" | "(" | ")" | "*" | "+" | "," | ";" | "=" }
}

private[http4s] object QueryParser {
  type Param = (String,Option[String])
  def parseQueryString(queryString: String, codec: Codec = Codec.UTF8): ParseResult[Seq[Param]] = {
    new QueryParser(queryString, codec)
      .QueryString
      .run()(ScalazDeliverySchemes.Disjunction)
  }
}
