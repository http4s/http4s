package org.http4s
package jawn

import _root_.jawn.{AsyncParser, ParseException, RawFacade}
import cats.effect._
import cats.implicits._
import fs2.Stream
import jawnfs2._

trait JawnInstances {
  def jawnDecoder[F[_]: Sync, J: RawFacade]: EntityDecoder[F, J] =
    EntityDecoder.decodeBy(MediaType.application.json)(jawnDecoderImpl[F, J])

  // some decoders may reuse it and avoid extra content negotiation
  private[http4s] def jawnDecoderImpl[F[_]: Sync, J: RawFacade](
      msg: Message[F]): DecodeResult[F, J] =
    DecodeResult {
      msg.body.chunks
        .parseJson(AsyncParser.SingleValue)
        .map(Either.right)
        .handleErrorWith {
          case pe: ParseException =>
            Stream.emit(Left(MalformedMessageBodyFailure("Invalid JSON", Some(pe))))
          case e =>
            Stream.raiseError[F](e)
        }
        .compile
        .last
        .map(_.getOrElse(Left(MalformedMessageBodyFailure("Invalid JSON: empty body"))))
    }
}
