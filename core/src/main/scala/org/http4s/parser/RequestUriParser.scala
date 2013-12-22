package org.http4s.parser

import org.parboiled2._
import scalaz.NonEmptyList
import java.nio.charset.Charset
import org.http4s.{ RequestUri => Http4sRequestUri }

private[http4s] class RequestUriParser(val input: ParserInput, val charset: Charset)
  extends Parser with Rfc3986Parser
{
  def RequestUri = rule { Asterisk | AbsoluteUri /* | AbsolutePath | Authority */ }

  def Asterisk = rule { "*" ~ push(Http4sRequestUri.`*`) }
}
