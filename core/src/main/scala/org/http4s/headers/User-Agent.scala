package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.{Renderable, Writer}
import org.parboiled2._

object `User-Agent` extends HeaderKey.Internal[`User-Agent`] with HeaderKey.Singleton {
  override protected def parseHeader(raw: Raw): Option[`User-Agent`] = {
    new Http4sHeaderParser[`User-Agent`](raw.value) {
      def entry = rule {
        product ~ zeroOrMore(RWS ~ (product | comment)) ~> (`User-Agent`(_,_))
      }

      def product: Rule1[AgentProduct] = rule {
        Token ~ optional("/" ~ Token) ~> (AgentProduct(_,_))
      }

      def comment: Rule1[AgentComment] = rule {
        capture(Comment) ~> { s: String => AgentComment(s.substring(1, s.length-1)) }
      }

      def RWS = rule { oneOrMore(anyOf(" \t")) }
    }.parse.toOption
  }
}

sealed trait AgentToken extends Renderable

case class AgentProduct(name: String, version: Option[String] = None) extends AgentToken {
  override def render(writer: Writer): writer.type = {
    writer << name
    version.foreach { v => writer << '/' << v }
    writer
  }
}
case class AgentComment(comment: String) extends AgentToken {
  override def renderString = comment
  override def render(writer: Writer): writer.type = writer << comment
}

case class `User-Agent`(product: AgentProduct, other: Seq[AgentToken] = Seq.empty) extends Header.Parsed {
  def key = `User-Agent`

  override def renderValue(writer: Writer): writer.type = {
    writer << product
    other.foreach { 
      case p: AgentProduct => writer << ' ' << p
      case AgentComment(c) => writer << ' ' << '(' << c << ')'
    }
    writer
  }
}

