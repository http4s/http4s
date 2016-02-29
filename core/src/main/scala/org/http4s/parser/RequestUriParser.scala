package org.http4s
package parser

import org.parboiled2._
import org.http4s.{ Query => Q }
import java.nio.charset.Charset
import org.http4s.util.string._

private[http4s] class RequestUriParser(val input: ParserInput, val charset: Charset)
  extends Parser with Rfc3986Parser
{
  def RequestUri = rule {
    (OriginForm  |
     AbsoluteUri |
     Authority ~> (auth => org.http4s.Uri(authority = Some(auth))) |
     Asterisk) ~ EOI
  }

  def OriginForm = rule { PathAbsolute ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) ~> { (path, query, fragment) =>
    val q = query.map(Q.fromString).getOrElse(Q.empty)
    org.http4s.Uri(path = path, query = q, fragment = fragment)
  } }

  def Asterisk: Rule1[Uri] = rule { "*" ~ push(org.http4s.Uri(authority = Some(org.http4s.Uri.Authority(host = org.http4s.Uri.RegName("*"))), path = "")) }
}
