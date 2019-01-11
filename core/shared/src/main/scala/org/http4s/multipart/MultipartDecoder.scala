package org.http4s
package multipart

import cats.effect._
import cats.implicits._

private[http4s] object MultipartDecoder extends PlatformMultipartDecoder {

  def decoder[F[_]: Sync]: EntityDecoder[F, Multipart[F]] =
    EntityDecoder.decodeBy(MediaRange.`multipart/*`) { msg =>
      msg.contentType.flatMap(_.mediaType.extensions.get("boundary")) match {
        case Some(boundary) =>
          DecodeResult {
            msg.body
              .through(MultipartParser.parseToPartsStream[F](Boundary(boundary)))
              .compile
              .toVector
              .map[Either[DecodeFailure, Multipart[F]]](parts =>
                Right(Multipart(parts, Boundary(boundary))))
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
