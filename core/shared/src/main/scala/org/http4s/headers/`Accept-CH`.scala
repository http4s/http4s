package org.http4s
package headers

import cats.parse.Parser0
import org.http4s.internal.parsing.Rfc7230
import org.typelevel.ci._

/*
Accept-CH response header
see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-CH
 */
object `Accept-CH` {
  def parse(s: String): ParseResult[`Accept-CH`] =
    ParseResult.fromParser(parser, "Invalid Accept-CH header")(s)

  private[http4s] val parser: Parser0[`Accept-CH`] =
    Rfc7230.headerRep(Rfc7230.token.map(CIString(_))).map(`Accept-CH`(_))

  implicit val headerInstance: Header[`Accept-CH`, Header.Recurring] =
    Header.createRendered(
      ci"Accept-CH",
      _.clientHints,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Monoid[`Accept-CH`] =
    cats.Monoid.instance(`Accept-CH`(Nil), (one, two) => `Accept-CH`(one.clientHints ++ two.clientHints))
}

final case class `Accept-CH`(clientHints: List[CIString])
