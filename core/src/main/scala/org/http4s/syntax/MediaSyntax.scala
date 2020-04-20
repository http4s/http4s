package org.http4s.syntax

import cats._
import cats.implicits._
import org.http4s.headers.`Content-Type`
import org.http4s._
import fs2._

trait MediaSyntax {
  implicit class MediaOps[M[_[_]], F[_]](private val m: M[F])(implicit M: Media[M]){

    def headers: Headers = M.headers(m)

    def body: EntityBody[F] = M.body(m)

    final def bodyAsText(implicit defaultCharset: Charset = DefaultCharset): Stream[F, String] =
      M.bodyAsText(m)

    final def contentType: Option[`Content-Type`] =
      M.contentType(m)

    final def contentLength: Option[Long] =
      M.contentLength(m)

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
      decoder.decode(m, strict = false)

    /** Decode the [[Media]] to the specified type
      *
      * If no valid [[Status]] has been described, allow Ok
      *
      * @param decoder [[EntityDecoder]] used to decode the [[Media]]
      * @tparam A type of the result
      * @return the effect which will generate the A
      */
    final def as[A](implicit F: MonadError[F, Throwable], decoder: EntityDecoder[F, A]): F[A] = {
      // n.b. this will be better with redeem in Cats-2.0
      attemptAs[A].leftWiden[Throwable].rethrowT
    }
  }
}

object MediaSyntax extends MediaSyntax