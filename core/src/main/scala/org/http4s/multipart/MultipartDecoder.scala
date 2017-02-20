// TODO fs2 port
/*
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
      def gatherParts = {
        def go(part: Part): Process1[Headers \/ ByteVector, Part] =
          receive1Or[Headers \/ ByteVector, Part](emit(part)) {
            case -\/(headers) =>
              emit(part) fby go(Part(headers, EmptyBody))
            case \/-(chunk) =>
              go(part.copy(body = part.body ++ emit(chunk)))
          }

        receive1[Headers \/ ByteVector, Part] {
          case -\/(headers) =>
            go(Part(headers, EmptyBody))
          case \/-(chunk) =>
            Process.fail(InvalidMessageBodyFailure("No headers in first part"))
        }
      }

      msg.contentType.flatMap(_.mediaType.extensions.get("boundary")) match {
        case Some(boundary) =>
          DecodeResult {
            msg.body
              .pipe(MultipartParser.parse(Boundary(boundary)))
              .pipe(gatherParts)
              .runLog
              .map(parts => \/-(Multipart(parts, Boundary(boundary))))
              .handle {
                case e: InvalidMessageBodyFailure => -\/(e)
                case e => -\/(InvalidMessageBodyFailure("Invalid multipart body", Some(e)))
            }
          }
        case None =>
          DecodeResult.failure(InvalidMessageBodyFailure("Missing boundary extension to Content-Type"))
      }
    }
}
*/*/
