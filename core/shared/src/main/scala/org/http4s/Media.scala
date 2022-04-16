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

import cats.MonadThrow
import fs2.RaiseThrowable
import fs2.Stream
import fs2.text.decodeWithCharset
import org.http4s.Charset.`UTF-8`
import org.http4s.headers._

trait Media[+F[_]] {

  def entity: Entity[F]
  final def body: EntityBody[F] = entity.body
  def headers: Headers
  def covary[F2[x] >: F[x]]: Media[F2]

  final def bodyText[F2[x] >: F[x]](implicit
      RT: RaiseThrowable[F2],
      defaultCharset: Charset = `UTF-8`,
  ): Stream[F2, String] = {
    val cs = charset.getOrElse(defaultCharset).nioCharset
    body.through(decodeWithCharset(cs))
  }

  final def contentType: Option[`Content-Type`] =
    headers.get[`Content-Type`]

  final def contentLength: Option[NonNegative] =
    headers.get[`Content-Length`].map(_.length)

  final def charset: Option[Charset] =
    contentType.flatMap(_.charset)
}

object Media {

  implicit final class InvariantOps[F[_]](private val self: Media[F]) extends AnyVal {

    // Decoding methods

    /** Decode the [[Media]] to the specified type
      *
      * @param decoder [[EntityDecoder]] used to decode the [[Media]]
      * @tparam T type of the result
      * @return the effect which will generate the `DecodeResult[T]`
      */
    def attemptAs[T](implicit decoder: EntityDecoder[F, T]): DecodeResult[F, T] =
      decoder.decode(self, strict = false)

    /** Decode the [[Media]] to the specified type
      *
      * If no valid [[Status]] has been described, allow Ok
      *
      * @param decoder [[EntityDecoder]] used to decode the [[Media]]
      * @tparam A type of the result
      * @return the effect which will generate the A
      */
    def as[A](implicit F: MonadThrow[F], decoder: EntityDecoder[F, A]): F[A] =
      F.rethrow(attemptAs.value)
  }

  def apply[F[_]](e: Entity[F], h: Headers): Media[F] =
    new Media[F] {
      def entity: Entity[F] = e

      def headers: Headers = h

      override def covary[F2[x] >: F[x]]: Media[F2] = this
    }
}
