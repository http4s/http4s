package org.http4s
package jawn

import cats.effect._
import cats.implicits._
import fs2.Stream
import org.typelevel.jawn.{AsyncParser, ParseException, RawFacade}
import jawnfs2._

trait JawnInstances {
  def jawnDecoder[F[_]: Sync, J: RawFacade]: EntityDecoder[F, J] =
    EntityDecoder.decodeBy(MediaType.application.json)(jawnDecoderImpl[F, J])

  def jawnParseExceptionMessage(pe: ParseException): DecodeFailure =
    MalformedMessageBodyFailure("Invalid JSON", Some(pe))
  def jawnEmptyBodyMessage: DecodeFailure =
    MalformedMessageBodyFailure("Invalid JSON: empty body")

  // some decoders may reuse it and avoid extra content negotiation
  private[http4s] def jawnDecoderImpl[F[_]: Sync, J: RawFacade](
      msg: Message[F]): DecodeResult[F, J] =
    DecodeResult {
      msg.body.chunks
        .parseJson(AsyncParser.SingleValue)
        .map(Either.right)
        .handleErrorWith {
          case pe: ParseException =>
            Stream.emit(Left(jawnParseExceptionMessage(pe)))
          case e =>
            Stream.raiseError[F](e)
        }
        .compile
        .last
        .map(_.getOrElse(Left(jawnEmptyBodyMessage)))
    }
}
