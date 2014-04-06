package org.http4s
package parser

import org.parboiled2._
import java.io.UnsupportedEncodingException
import scala.io.Codec
import java.net.URLDecoder
import util.string._
import org.parboiled2.CharPredicate._
import org.parboiled2.ParseError

// TODO: this could be made more efficient. For a good example, look at the Jetty impl
// https://github.com/eclipse/jetty.project/blob/release-9/jetty-util/src/main/java/org/eclipse/jetty/util/UrlEncoded.java

private[parser] class QueryParser(val input: ParserInput, codec: Codec) extends Parser {

  def charset = codec.charSet

  def QueryString: Rule1[Seq[(String, String)]] = rule {
      EOI ~ push(Seq.empty[(String, String)]) |
      zeroOrMore(QueryParameter).separatedBy("&") ~ EOI
  }

  def QueryParameter: Rule1[(String,String)] = rule {
    capture(zeroOrMore(!anyOf("&=") ~ QChar)) ~ optional('=' ~ capture(zeroOrMore(!anyOf("&") ~ QChar))) ~> {
      (k: String, v: Option[String]) => (decodeParam(k), v.map(decodeParam(_)).getOrElse(""))
    }
  }

  private def decodeParam(str: String): String = {
    try {
      URLDecoder.decode(str, "UTF-8")  // TODO: Fix me don't decode twice for validaton purposes
      str.urlDecode(codec) // rl has a bug where it hangs on invalid escaped values
    } catch {
        case e: IllegalArgumentException     => ""
        case e: UnsupportedEncodingException => ""
    }
  }

  def QChar = rule { !'&' ~ (Pchar | '/' | '?') }

  def Pchar = rule { Unreserved | SubDelims | ":" | "@" | "%" }

  def Unreserved = rule { Alpha | Digit | "-" | "." | "_" | "~" }

  def SubDelims = rule { "!" | "$" | "&" | "'" | "(" | ")" | "*" | "+" | "," | ";" | "=" }
}

private[parser] object QueryParser {
  def parseQueryString(queryString: String, codec: Codec = Codec.UTF8): Either[ParseErrorInfo, Seq[(String, String)]] = {
    try new QueryParser(queryString, codec)
      .QueryString
      .run()(Parser.DeliveryScheme.Either)
      .left.map {
        case ParseError(_, traces) => ParseErrorInfo(s"Illegal query string: '$queryString'", traces.map(_.format).mkString("; "))
        case e => ParseErrorInfo("Illegal query string", e.getMessage)
      }
    catch {
      case e: ParseErrorInfo => println("------------"); Left(e)
      case e: Throwable      => Left(ParseErrorInfo(s"Illegal query string: '$queryString'", e.getMessage))
    }
  }
}