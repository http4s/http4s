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

import cats.MonadError
import cats.syntax.all._
import fs2.{RaiseThrowable, Stream}
import fs2.text.utf8Decode
import org.http4s.headers._

trait Media[F[_]] {
  def body: EntityBody[F]
  def headers: Headers
  def covary[F2[x] >: F[x]]: Media[F2]

  @deprecated(
    "Can go into an infinite loop for charsets other than UTF-8. Replaced by bodyText",
    "0.21.5")
  final def bodyAsText(implicit defaultCharset: Charset = DefaultCharset): Stream[F, String] =
    charset.getOrElse(defaultCharset) match {
      case Charset.`UTF-8` =>
        // suspect this one is more efficient, though this is superstition
        body.through(utf8Decode)
      case cs =>
        body.through(util.decode(cs))
    }

  final def bodyText(implicit
      RT: RaiseThrowable[F],
      defaultCharset: Charset = DefaultCharset): Stream[F, String] =
    charset.getOrElse(defaultCharset) match {
      case Charset.`UTF-8` =>
        // suspect this one is more efficient, though this is superstition
        body.through(utf8Decode)
      case cs =>
        body.through(internal.decode(cs))
    }

  final def contentType: Option[`Content-Type`] =
    headers.get(`Content-Type`)

  final def contentLength: Option[Long] =
    headers.get(`Content-Length`).map(_.length)

  final def charset: Option[Charset] =
    contentType.flatMap(_.charset)

  // Decoding methods

  /** Decode the [[Media]] to the specified type
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Media]]
    * @tparam T type of the result
    * @return the effect which will generate the `DecodeResult[T]`
    */
  final def attemptAs[T](implicit decoder: EntityDecoder[F, T]): DecodeResult[F, T] =
    decoder.decode(this, strict = false)

  /** Decode the [[Media]] to the specified type
    *
    * If no valid [[Status]] has been described, allow Ok
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Media]]
    * @tparam A type of the result
    * @return the effect which will generate the A
    */
  final def as[A](implicit F: MonadError[F, Throwable], decoder: EntityDecoder[F, A]): F[A] =
    // n.b. this will be better with redeem in Cats-2.0
    attemptAs.leftWiden[Throwable].rethrowT
}

object Media {
  def apply[F[_]](b: EntityBody[F], h: Headers): Media[F] =
    new Media[F] {
      def body = b

      def headers: Headers = h

      override def covary[F2[x] >: F[x]]: Media[F2] = this.asInstanceOf[Media[F2]]
    }
}
