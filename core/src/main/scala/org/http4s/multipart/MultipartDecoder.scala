package org.http4s
package multipart

import fs2._
import org.log4s.getLogger

private[http4s] object MultipartDecoder {

  private[this] val logger = getLogger

  val decoder: EntityDecoder[Multipart] =
    EntityDecoder.decodeBy(MediaRange.`multipart/*`) { msg =>

      msg.contentType.flatMap(_.mediaType.extensions.get("boundary")) match {
        case Some(boundary) =>
          DecodeResult {
            msg.body
              .through(MultipartParser.parse(Boundary(boundary)))
              .through(gatherParts)
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


  def gatherParts : Pipe[Task, Either[Headers,Byte], Part] = _.open.flatMap{
    def go(part: Part)(h: Handle[Task, Either[Headers, Byte]]): Pull[Task, Part, Either[Headers, Byte]] = {
      h.await1Option.flatMap{
        case Some((Left(headers), h)) => Pull.output1(part) >> go(Part(headers, EmptyBody))(h)
        case Some((Right(byte), h)) => go(part.copy(body = part.body ++ Stream.emit(byte)))(h)
        case None => Pull.output1(part) >> Pull.done
      }
    }

    _.await1.flatMap{
      case (Left(headers), h) => go(Part(headers, EmptyBody))(h)
      case (Right(byte), h) => Pull.fail(InvalidMessageBodyFailure("No headers in first part"))

    }
  }.close
}
