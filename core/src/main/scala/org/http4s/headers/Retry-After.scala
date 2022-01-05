/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package headers

import cats.parse.Parser
import cats.parse.Rfc5234
import org.http4s.util.Renderable._
import org.typelevel.ci._

import scala.concurrent.duration.FiniteDuration

object `Retry-After` {
  private class RetryAfterImpl(retry: Either[HttpDate, Long]) extends `Retry-After`(retry)

  def apply(retry: HttpDate): `Retry-After` = new RetryAfterImpl(Left(retry))

  def fromLong(retry: Long): ParseResult[`Retry-After`] =
    if (retry >= 0) ParseResult.success(new RetryAfterImpl(Right(retry)))
    else
      ParseResult.fail(
        "Invalid retry value",
        s"Retry param $retry must be more or equal than 0 seconds",
      )

  def unsafeFromLong(retry: Long): `Retry-After` =
    fromLong(retry).fold(throw _, identity)

  def unsafeFromDuration(retry: FiniteDuration): `Retry-After` =
    fromLong(retry.toSeconds).fold(throw _, identity)

  def parse(s: String): ParseResult[`Retry-After`] =
    ParseResult.fromParser(parser, "Invalid Retry-After header")(s)

  /* `Retry-After = HTTP-date / delay-seconds` */
  private[http4s] val parser: Parser[`Retry-After`] = {
    import Rfc5234.digit

    def httpDate = HttpDate.parser.map(apply)

    /* delay-seconds  = 1*DIGIT */
    def delaySeconds = digit.rep.string.map(s => unsafeFromLong(s.toLong))

    httpDate.orElse(delaySeconds)
  }

  implicit val headerInstance: Header[`Retry-After`, Header.Single] =
    Header.createRendered(
      ci"Retry-After",
      _.retry,
      parse,
    )

}

/** Response header, used by the server to indicate to the user-agent how long it has to wait before
  * it can try again with a follow-up request.
  *
  * [[https://datatracker.ietf.org/doc/html/rfc7231#section-7.1.3 RFC-7231 Section 7.1.3]]
  *
  * @param retry Indicates the retry time, either as a date of expiration or as a number of seconds from the current time
  * until that expiration.
  */
sealed abstract case class `Retry-After`(retry: Either[HttpDate, Long])
