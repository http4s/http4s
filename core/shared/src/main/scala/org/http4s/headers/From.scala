package org.http4s
package headers

import cats.parse.Parser
import org.http4s.internal.parsing.Rfc5322
import org.typelevel.ci.CIStringSyntax

object From extends HeaderCompanion[From]("From") {
  override def parse(s: String): ParseResult[From] =
    ParseResult.fromParser(parser, "Invalid From header")(s)

  private[http4s] val parser: Parser[From] = Rfc5322.mailbox.map(From.apply)

  implicit val headerInstance: Header[From, Header.Single] = Header.createRendered(
    ci"From",
    _.email,
    parse,
  )
}

final case class From(email: String)
