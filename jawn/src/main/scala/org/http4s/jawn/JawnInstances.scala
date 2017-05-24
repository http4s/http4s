package org.http4s
package jawn

import _root_.jawn.{AsyncParser, Facade, ParseException}
import cats._
import cats.implicits._
import fs2.Stream
import jawnfs2._

trait JawnInstances {
  def jawnDecoder[F[_], J](implicit C: MonadError[F, Throwable], F: Applicative[F], facade: Facade[J]): EntityDecoder[F, J] =
    EntityDecoder.decodeBy(MediaType.`application/json`)(jawnDecoderImpl[F, J])

  // some decoders may reuse it and avoid extra content negotiation
  private[http4s] def jawnDecoderImpl[F[_], J](
      msg: Message[F])(implicit C: MonadError[F, Throwable], F: Applicative[F], facade: Facade[J]): DecodeResult[F, J] =
    DecodeResult {
      msg.body.chunks
        .parseJson(AsyncParser.SingleValue)
        .map(Either.right)
        .onError {
          case pe: ParseException =>
            Stream.emit(Left(MalformedMessageBodyFailure("Invalid JSON", Some(pe))))
          case e => Stream.fail(e)
        }
        .runLast
        .map(_.getOrElse(Left(MalformedMessageBodyFailure("Invalid JSON: empty body"))))
    }
}
