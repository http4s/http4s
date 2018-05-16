package org.http4s
package multipart
package file

import cats.effect._
import cats.implicits._

object MixedMultipartDecoder {

  def decoder[F[_]: Sync](
      limit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 50,
      failOnLimit: Boolean = false): EntityDecoder[F, MixedMultipart[F]] =
    EntityDecoder.decodeBy(MediaRange.`multipart/*`) { msg =>
      msg.contentType.flatMap(_.mediaType.extensions.get("boundary")) match {
        case Some(boundary) =>
          DecodeResult {
            msg.body
              .through(
                MultipartParser.parseToPartsStreamedFile[F](
                  Boundary(boundary),
                  limit,
                  maxSizeBeforeWrite,
                  maxParts,
                  failOnLimit))
              .compile
              .toVector
              .map[Either[DecodeFailure, MixedMultipart[F]]](parts =>
                Right(MixedMultipart(parts, Boundary(boundary))))
              .handleError {
                case e: InvalidMessageBodyFailure => Left(e)
                case e => Left(InvalidMessageBodyFailure("Invalid multipart body", Some(e)))
              }
          }
        case None =>
          DecodeResult.failure(
            InvalidMessageBodyFailure("Missing boundary extension to Content-Type"))
      }
    }

}
