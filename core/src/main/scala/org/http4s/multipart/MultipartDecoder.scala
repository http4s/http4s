package org.http4s
package multipart

import fs2._
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
//              .through(printBody)
              .through(MultipartParser.parse(Boundary(boundary)))
//              .evalMap[Task, Task, Either[Headers, Byte]]{
//                case l@Left(e) => Task.delay{println(e); l}
//                case r@Right(b) => Task.delay(r)
//              }
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

//  def printBody : Pipe[Task, Byte, Byte] = s => {
//    val bvStream = s.runLog
//      .map(ByteVector(_))
//
//    Stream.eval(bvStream.map(_.decodeAscii).flatMap{e => Task.delay(println(e))}) >> s
//  }




  def gatherParts(h: Handle[Task, Either[Headers, Byte]]): Pull[Task, Part, Either[Headers, Byte]] = {
    def go(part: Part)(h: Handle[Task, Either[Headers, Byte]]): Pull[Task, Part, Either[Headers, Byte]] = {
      h.receive1Option {
        case Some((Left(headers), h1)) => Pull.output1(part) >> go(Part(headers, EmptyBody))(h1)
        case Some((Right(byte), h1)) => //go(part.copy(body = part.body.append(Stream.emit(byte))))(h1)
          go(Part(part.headers, part.body ++ Stream.emit(byte)))(h1)
        case None => Pull.output1(part) >> Pull.done
      }
    }

    h.receive1 {
      case (Left(headers), h1) => go(Part(headers, EmptyBody))(h1)
      case (Right(byte), h) => Pull.fail(InvalidMessageBodyFailure("No headers in first part"))
    }
  }
}

