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
