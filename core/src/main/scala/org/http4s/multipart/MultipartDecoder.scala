package org.http4s
package multipart

import cats.effect._
import cats.implicits._
import fs2._

import fs2.interop.scodec.ByteVectorChunk
import org.log4s.getLogger
import scodec.bits.ByteVector

private[http4s] object MultipartDecoder {

  def decoder[F[_]: Sync, M[_[_]]](implicit M: Message[F, M]): EntityDecoder[F, Multipart[F], M] =
    EntityDecoder.decodeBy(MediaRange.`multipart/*`) { msg =>
      M.contentType(msg).flatMap(_.mediaType.extensions.get("boundary")) match {
        case Some(boundary) =>
          DecodeResult {
            M.body(msg)
              .through(MultipartParser.parse(Boundary(boundary)))
              .through(gatherParts)
              .runLog
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

  def gatherParts[F[_]]: Pipe[F, Either[Headers, ByteVector], Part[F]] = s => {
    def go(part: Part[F], lastWasLeft: Boolean)(s: Stream[F, Either[Headers, ByteVector]])
      : Pull[F, Part[F], Option[Either[Headers, ByteVector]]] =
      s.pull.uncons1.flatMap {
        case Some((Left(headers), s)) =>
          if (lastWasLeft) {
            go(
              Part(Headers(part.headers.toList ::: headers.toList), EmptyBody),
              lastWasLeft = true)(s)
          } else {
            Pull.output1(part) >> go(Part(headers, EmptyBody), lastWasLeft = true)(s)
          }
        case Some((Right(bv), s)) =>
          go(
            part.copy(body = part.body.append(Stream.chunk(ByteVectorChunk(bv)))),
            lastWasLeft = false)(s)
        case None =>
          Pull.output1(part) >> Pull.pure(None)
      }

    s.pull.uncons1.flatMap {
      case Some((Left(headers), s)) => go(Part(headers, EmptyBody), lastWasLeft = true)(s)
      case Some((Right(byte), s)) =>
        Pull.fail(InvalidMessageBodyFailure("No headers in first part"))
      case None => Pull.pure(None)
    }.stream
  }

}
