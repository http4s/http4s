/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import cats.parse.{Parser1, Rfc5234}
import cats.syntax.all._
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
    parser.parseAll(s).leftMap { e =>
      ParseFailure("Invalid Retry-After header", e.toString)
    }

  /* `Retry-After = HTTP-date / delay-seconds` */
  private[http4s] val parser: Parser1[`Retry-After`] = {
    import Rfc5234.digit

    def httpDate = HttpDate.parser.map(apply)

    /* delay-seconds  = 1*DIGIT */
    def delaySeconds = digit.rep1.string.map(s => unsafeFromLong(s.toLong))

    httpDate.orElse1(delaySeconds)
  }
}

/** Response header, used by the server to indicate to the user-agent how long it has to wait before
  * it can try again with a follow-up request.
  *
  * [[https://tools.ietf.org/html/rfc7231#section-7.1.3 RFC-7231 Section 7.1.3]]
  *
  * @param retry Indicates the retry time, either as a date of expiration or as a number of seconds from the current time
  * until that expiration.
  */
sealed abstract case class `Retry-After`(retry: Either[HttpDate, Long]) extends Header.Parsed {
  val key = `Retry-After`
  override val value = Renderer.renderString(retry)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
