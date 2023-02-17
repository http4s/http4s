package org.http4s
package headers

import org.http4s.Header
import org.http4s.ParseFailure

import org.typelevel.ci._
import org.http4s.Uri

object `X-Forwarded-Host` {

  def parse(headerValue: String): Either[ParseFailure, `X-Forwarded-Host`] =
    ParseResult.fromParser(
      Uri.Parser.host.map(`X-Forwarded-Host`.apply),
      "Invalid X-Forwarded-Host header",
    )(headerValue)

  implicit val headerInstance: Header[`X-Forwarded-Host`, Header.Single] =
    Header.create(
      ci"X-Forwarded-Host",
      _.host.toString(),
      parse,
    )

}

final case class `X-Forwarded-Host`(host: Uri.Host)
