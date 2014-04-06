package org.http4s
package parser

import org.parboiled2._
import java.io.UnsupportedEncodingException
import scala.io.Codec
import java.net.URLDecoder
import util.string._


private[parser] class QueryParser(val input: ParserInput, codec: Codec) extends Parser {

  def QueryString: Rule1[Seq[(String, String)]] = rule {
      EOI ~ push(Seq.empty[(String, String)]) |
      zeroOrMore(QueryParameter).separatedBy("&") ~ EOI
  }

  def QueryParameter: Rule1[(String,String)] = rule {
    QueryParameterComponent ~ optional("=") ~ (QueryParameterComponent | push("")) ~> {(_: String,_: String)}
  }

  def QueryParameterComponent = rule {
    capture(zeroOrMore(!anyOf("&=") ~ ANY)) ~> { s: String =>
      try {
        URLDecoder.decode(s, "UTF-8")  // TODO: Fix me don't decode twice for validaton purposes
        s.urlDecode(codec) // rl has a bug where it hangs on invalid escaped values
      } catch {
        case e: IllegalArgumentException =>
          throw ParseErrorInfo(s"Illegal query string", e.getMessage)
        case e: UnsupportedEncodingException =>
          throw ParseErrorInfo("Unsupported character encoding in query string", e.getMessage)
      }
    }
  }
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
      case e: ParseErrorInfo => Left(e)
      case e: Throwable      => Left(ParseErrorInfo(s"Illegal query string: '$queryString'", e.getMessage))
    }
  }
}