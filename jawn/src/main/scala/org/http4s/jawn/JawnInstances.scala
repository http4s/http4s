package org.http4s
package jawn

import cats.effect._
import cats.implicits._
import fs2.Stream
import _root_.jawn.{AsyncParser, Facade, ParseException}
import jawnfs2._
import Message.messSyntax._

trait JawnInstances {
  def jawnDecoder[F[_]: Effect, J: Facade]: EntityDecoder[F, J] =
    new EntityDecoder[F, J] {
      override def consumes: Set[MediaRange] = Set(MediaType.`application/json`)

      override def decode[M[_[_]]](msg: M[F], strict: Boolean)(implicit M: Message[M, F]): DecodeResult[F, J] =
        org.http4s.EntityDecoder.decodeGeneric(msg, strict)(
          msg => jawnDecoderImpl(msg), consumes
        )
    }

  // some decoders may reuse it and avoid extra content negotiation
  private[http4s] def jawnDecoderImpl[M[_[_]], F[_]: Effect, J: Facade](
      msg: M[F])(implicit M: Message[M, F]): DecodeResult[F, J] =
    DecodeResult {
      msg.body.chunks
        .parseJson(AsyncParser.SingleValue)
        .map(Either.right)
        .onError[Either[DecodeFailure, J]] {
          case pe: ParseException =>
            Stream.emit(Left(MalformedMessageBodyFailure("Invalid JSON", Some(pe))))
          case e => Stream.fail(e)
        }
        .runLast
        .map(_.getOrElse(Left(MalformedMessageBodyFailure("Invalid JSON: empty body"))))
    }
}
