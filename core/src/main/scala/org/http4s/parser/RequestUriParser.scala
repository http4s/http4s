package org.http4s
package parser

import org.parboiled2._
import scalaz.NonEmptyList
import java.nio.charset.Charset
import org.http4s.util.string._

private[http4s] class RequestUriParser(val input: ParserInput, val charset: Charset)
  extends Parser with Rfc3986Parser
{
  def RequestUri = rule {
    Asterisk    |
    AbsoluteUri |
    OriginForm  |
    Authority ~> (auth => org.http4s.Uri(authority = Some(auth)))
  }

  def OriginForm = rule { PathAbsolute ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) ~> ((path, query, fragment) => org.http4s.Uri(path = path, query = query, fragment = fragment)) }

  def Asterisk: Rule1[Uri] = rule { "*" ~ push(org.http4s.Uri(authority = Some(org.http4s.Uri.Authority(host = org.http4s.Uri.RegName("*"))), path = "")) }
}
