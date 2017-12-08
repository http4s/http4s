package org.http4s
package jawn

import _root_.jawn.{AsyncParser, Facade, ParseException}
import cats.effect._
import cats.syntax.either._
import cats.syntax.functor._
import fs2.Stream
import jawnfs2._

trait JawnInstances {
  def jawnDecoder[F[_]: Sync, J: Facade]: EntityDecoder[F, J] =
    EntityDecoder.decodeBy(MediaType.`application/json`)(jawnDecoderImpl[F, J])

  // some decoders may reuse it and avoid extra content negotiation
  private[http4s] def jawnDecoderImpl[F[_]: Sync, J: Facade](
      msg: Message[F]): DecodeResult[F, J] =
    DecodeResult {
      msg.body.chunks
        .parseJson(AsyncParser.SingleValue)
        .map(Either.right)
        .handleErrorWith[Either[DecodeFailure, J]] {
          case pe: ParseException =>
            Stream.emit(Left(MalformedMessageBodyFailure("Invalid JSON", Some(pe))))
          case e =>
            Stream.raiseError(e)
        }
        .runLast
        .map(_.getOrElse(Left(MalformedMessageBodyFailure("Invalid JSON: empty body"))))
    }
}
