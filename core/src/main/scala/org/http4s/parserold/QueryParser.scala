package org.http4s
package parserold

import org.parboiled.scala._
import java.io.UnsupportedEncodingException
import org.parboiled.errors.ParsingException
import scala.io.Codec
import java.net.URLDecoder

import org.http4s.parser.ParseErrorInfo


object QueryParser extends Http4sParser {

  def QueryString: Rule1[Seq[(String, String)]] = rule (
      EOI ~ push(Seq.empty[(String, String)])
    | zeroOrMore(QueryParameter, separator = "&") ~ EOI ~~> (x => x)
  )

  def QueryParameter = rule {
    QueryParameterComponent ~ optional("=") ~ (QueryParameterComponent | push(""))
  }

  def QueryParameterComponent = rule {
    zeroOrMore(!anyOf("&=") ~ ANY) ~> { s =>
      try {
        URLDecoder.decode(s, "UTF-8")  // TODO: Fix me don't decode twice for validaton purposes
        s.urlDecode() // rl has a bug where it hangs on invalid escaped values
      } catch {
        case e: IllegalArgumentException =>
          throw new ParsingException("Illegal query string: " + e.getMessage)
        case e: UnsupportedEncodingException =>
          throw new ParsingException("Unsupported character encoding in query string: " + e.getMessage)
      }
    }
  }

  def parseQueryString(queryString: String, codec: Codec = Codec.UTF8): Either[ParseErrorInfo, Seq[(String, String)]] =
    parse(QueryString, queryString).left.map(_.withFallbackSummary("Illegal query string"))
}