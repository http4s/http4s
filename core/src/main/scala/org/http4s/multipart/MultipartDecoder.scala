package org.http4s
package multipart

import cats.effect._
import cats.implicits._
import fs2._
import fs2.interop.scodec.ByteVectorChunk
import scodec.bits.ByteVector

private[http4s] object MultipartDecoder {

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
      case Some((Right(byte @ _), _)) =>
        Pull.raiseError(InvalidMessageBodyFailure("No headers in first part"))
      case None => Pull.pure(None)
    }.stream
  }

  /** Multipart decoder that streams all parts past a threshold
    * (anything above maxSizeBeforeWrite) into a temporary file.
    *
    * Note: (BIG NOTE) Using this decoder for multipart decoding is good for the sake of
    * not holding all information in memory, as it will never have more than
    * `maxSizeBeforeWrite` in memory before writing to a temporary file. On top of this,
    * you can gate the # of parts to further stop the quantity of parts you can have.
    * That said, because after a threshold it writes into a temporary file, given
    * bincompat reasons on 0.18.x, there is no way to make a distinction about which `Part[F]`
    * is a stream reference to a file or not. Thus, consumers using this decoder
    * should drain all `Part[F]` bodies if they were decoded correctly. That said,
    * this decoder gives you more control about how many part bodies it parses in the first place, thus you can have
    * more fine-grained control about how many parts you accept.
    *
    * @param headerLimit the max size for the headers, in bytes. This is required as
    *                    headers are strictly evaluated and parsed.
    * @param maxSizeBeforeWrite the maximum size of a particular part before writing to a file is triggered
    * @param maxParts the maximum number of parts this decoder accepts. NOTE: this also may mean that a body that doesn't
    *                 conform perfectly to the spec (i.e isn't terminated properly) but has a lot of parts might
    *                 be parsed correctly, despite the total body being malformed due to not conforming to the multipart
    *                 spec. You can control this by `failOnLimit`, by setting it to true if you want to raise
    *                 an error if sending too many parts to a particular endpoint
    * @param failOnLimit Fail if `maxParts` is exceeded _during_ multipart parsing.
    * @return A multipart/form-data encoded vector of parts with some part bodies held in
    *         temporary files.
    */
  def mixedMultipart[F[_]: Sync](
      headerLimit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 50,
      failOnLimit: Boolean = false): EntityDecoder[F, Multipart[F]] =
    EntityDecoder.decodeBy(MediaRange.`multipart/*`) { msg =>
      msg.contentType.flatMap(_.mediaType.extensions.get("boundary")) match {
        case Some(boundary) =>
          DecodeResult {
            msg.body
              .through(
                MultipartParser.parseToPartsStreamedFile[F](
                  Boundary(boundary),
                  headerLimit,
                  maxSizeBeforeWrite,
                  maxParts,
                  failOnLimit))
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
