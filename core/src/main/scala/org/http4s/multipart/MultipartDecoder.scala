package org.http4s
package multipart

import scala.util.{Try, Success, Failure}
import scala.util.control._

import cats._
import fs2._
import org.http4s.parser._
import org.http4s.headers._
import org.log4s.getLogger
import org.http4s.internal.parboiled2._
import scodec.bits.ByteVector

private[http4s] object MultipartDecoder {

  private[this] val logger = getLogger

  val decoder: EntityDecoder[Multipart] =
    EntityDecoder.decodeBy(MediaRange.`multipart/*`) { msg =>
      def gatherParts : Pipe[Task, Either[Headers, Byte], Part] = s => {

        def go(part: Part): Handle[Task, Either[Headers, Byte]] => Pull[Task, Part, Unit] =
          _.receive1Option{
            case Some((Left(headers), handle)) =>
                Pull.output1(part) >> go(Part(headers, EmptyBody))(handle)
            case Some((Right(chunk), handle)) =>
                go(part.copy(body = part.body ++ Stream.emit(chunk)))(handle)
            case None => Pull.output1(part)
          }

        s.open.flatMap(_.receive1{
          case (Left(headers), handle) =>
            go(Part(headers, EmptyBody))(handle)
          case (Right(chunk), handle) =>
            Pull.fail(InvalidMessageBodyFailure("No headers in first part"))
        }).close
      }

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
}
