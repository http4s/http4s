package org.http4s
package jawn

import cats.effect._
import fs2._
import cats.implicits._
import _root_.jawn.{AsyncParser, Facade, ParseException}
import jawnfs2._

trait JawnInstances {
  def jawnDecoder[F[_]: Effect, J: Facade]: EntityDecoder[F, J] =
    EntityDecoder.decodeBy(MediaType.`application/json`)(jawnDecoderImpl[F, J])

  // some decoders may reuse it and avoid extra content negotiation
  private[http4s] def jawnDecoderImpl[F[_]: Effect, J: Facade](
      msg: Message[F]): DecodeResult[F, J] =
    DecodeResult[F, J] {
      msg.body.chunks
        .parseJson(AsyncParser.SingleValue)
        .map(Either.right[DecodeFailure, J])
        .handleErrorWith {
          case pe: ParseException =>
            Stream.emit(Either.left(MalformedMessageBodyFailure("Invalid JSON", Some(pe)))).covary[F]
          case e => Stream.raiseError(e).covary[F]
        }
        .runLast
        .map(_.getOrElse(Either.left(MalformedMessageBodyFailure("Invalid JSON: empty body"))))
    }
}
