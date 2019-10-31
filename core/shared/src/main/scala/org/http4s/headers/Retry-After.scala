package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderer, Writer}
import org.http4s.util.Renderable._
import scala.concurrent.duration.FiniteDuration

object `Retry-After` extends HeaderKey.Internal[`Retry-After`] with HeaderKey.Singleton {
  private class RetryAfterImpl(retry: Either[HttpDate, Long]) extends `Retry-After`(retry)

  def apply(retry: HttpDate): `Retry-After` = new RetryAfterImpl(Left(retry))

  def fromLong(retry: Long): ParseResult[`Retry-After`] =
    if (retry >= 0) ParseResult.success(new RetryAfterImpl(Right(retry)))
    else
      ParseResult.fail(
        "Invalid retry value",
        s"Retry param $retry must be more or equal than 0 seconds")

  def unsafeFromLong(retry: Long): `Retry-After` =
    fromLong(retry).fold(throw _, identity)

  def unsafeFromDuration(retry: FiniteDuration): `Retry-After` =
    fromLong(retry.toSeconds).fold(throw _, identity)

  override def parse(s: String): ParseResult[`Retry-After`] =
    HttpHeaderParser.RETRY_AFTER(s)
}

/**
  * Constructs a `Retry-After` header.
  *
  * The value of this field can be either an HTTP-date or an integer number of seconds (in decimal) after the time of the response.
  *
  * @param retry Either the date of expiration or seconds until expiration
  */
sealed abstract case class `Retry-After`(retry: Either[HttpDate, Long]) extends Header.Parsed {

  val key = `Retry-After`
  override val value = Renderer.renderString(retry)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
