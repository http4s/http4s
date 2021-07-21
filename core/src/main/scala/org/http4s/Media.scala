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
import org.http4s.headers._

trait Media[+ Body] {
  def body: B
  def headers: Headers

/*
  TODO this is specific case of -- Body = Stream[F, Byte]

  import fs2.{RaiseThrowable, Stream}
  import fs2.text.utf8Decode

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
 */

  final def contentType: Option[`Content-Type`] =
    headers.get[`Content-Type`]

  final def contentLength: Option[Long] =
    headers.get[`Content-Length`].map(_.length)

  final def charset: Option[Charset] =
    contentType.flatMap(_.charset)

  // Decoding methods

  /** Decode the [[Media]] to the specified type
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Media]]
    * @tparam T type of the result
    * @return the effect which will generate the `DecodeResult[T]`
    */
  final def attemptAs[T](implicit decoder: EntityDecoder[Body, T]): DecodeResult[T] =
    decoder.decode(this, strict = false)

  /** Decode the [[Media]] to the specified type
    *
    * If no valid [[Status]] has been described, allow Ok
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Media]]
    * @tparam A type of the result
    * @return the effect which will generate the A
    */
  final def as[F[_], A](implicit F: MonadThrow[F], decoder: EntityDecoder[Body, A]): F[A] =
    F.fromEither(attemptAs)
}

object Media {
  def apply[Body](b: Body, h: Headers): Media[Body] =
    new Media[Body] {
      def body = b

      def headers: Headers = h
    }
}
