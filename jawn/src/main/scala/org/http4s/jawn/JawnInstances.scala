package org.http4s
package jawn

import cats.implicits._
import _root_.jawn.{AsyncParser, Facade, ParseException}
import fs2.Stream
import jawnfs2._

trait JawnInstances {
  def jawnDecoder[J](implicit facade: Facade[J]): EntityDecoder[J] =
    EntityDecoder.decodeBy(MediaType.`application/json`)(jawnDecoderImpl[J])

  // some decoders may reuse it and avoid extra content negotiation
  private[http4s] def jawnDecoderImpl[J](msg: Message)(implicit facade: Facade[J]) =
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
