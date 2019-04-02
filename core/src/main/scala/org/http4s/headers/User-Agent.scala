package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderable, Writer}

object `User-Agent` extends HeaderKey.Internal[`User-Agent`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`User-Agent`] =
    HttpHeaderParser.USER_AGENT(s)
}

sealed trait AgentToken extends Renderable

final case class AgentProduct(name: String, version: Option[String] = None) extends AgentToken {
  override def render(writer: Writer): writer.type = {
    writer << name
    version.foreach { v =>
      writer << '/' << v
    }
    writer
  }
}
final case class AgentComment(comment: String) extends AgentToken {
  override def renderString: String = comment
  override def render(writer: Writer): writer.type = writer << comment
}

final case class `User-Agent`(product: AgentProduct, other: List[AgentToken] = Nil)
    extends Header.Parsed {
  def key: `User-Agent`.type = `User-Agent`

  override def renderValue(writer: Writer): writer.type = {
    writer << product
    other.foreach {
      case p: AgentProduct => writer << ' ' << p
      case AgentComment(c) => writer << ' ' << '(' << c << ')'
    }
    writer
  }
}
