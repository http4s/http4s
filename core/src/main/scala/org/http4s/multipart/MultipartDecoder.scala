package org.http4s
package multipart

import fs2._
import org.http4s.util.ByteVectorChunk
import org.log4s.getLogger
import scodec.bits.ByteVector

private[http4s] object MultipartDecoder {

  private[this] val logger = getLogger

  val decoder: EntityDecoder[Multipart] =
    EntityDecoder.decodeBy(MediaRange.`multipart/*`) { msg =>

      msg.contentType.flatMap(_.mediaType.extensions.get("boundary")) match {
        case Some(boundary) =>
          DecodeResult {
            msg.body
              .through(MultipartParser.parse(Boundary(boundary)))
              .pull(gatherParts)
              .runLog
              .map(parts => Right(Multipart(parts, Boundary(boundary))))
              .handle {
                case e: InvalidMessageBodyFailure => Left(e)
                case e => Left(InvalidMessageBodyFailure("Invalid multipart body", Some(e)))
              }
          }
        case None =>
          DecodeResult.failure(InvalidMessageBodyFailure("Missing boundary extension to Content-Type"))
      }
    }


  def gatherParts(h: Handle[Task, Either[Headers, ByteVector]]): Pull[Task, Part, Either[Headers, ByteVector]] = {
    def go(part: Part, lastWasLeft: Boolean)(h: Handle[Task, Either[Headers, ByteVector]]): Pull[Task, Part, Either[Headers, ByteVector]] = {
      h.receive1Option {
        case Some((Left(headers), h1)) =>
          if (lastWasLeft){
            go(Part(Headers(part.headers.toList ::: headers.toList), EmptyBody), true)(h1)
          } else {
           Pull.output1(part) >> go(Part(headers, EmptyBody), true)(h1)
          }
        case Some((Right(bv), h1)) =>
          go(part.copy(body = part.body.append(Stream.chunk(ByteVectorChunk(bv)))), false)(h1)
        case None => Pull.output1(part) >> Pull.done
      }
    }


    h.receive1 {
      case (Left(headers), h1) =>
        go(Part(headers, EmptyBody), true)(h1)
      case (Right(byte), h) => Pull.fail(InvalidMessageBodyFailure("No headers in first part"))
    }
  }

}

