package org.http4s
package jawn

import _root_.jawn.{AsyncParser, Facade, ParseException}
import jawnstreamz._

import scalaz.{-\/, \/-}
import scalaz.stream.Process.emit

trait JawnInstances {
  def jawnDecoder[J](implicit facade: Facade[J]): EntityDecoder[J] =
    // EntityDecoder.decodeBy(MediaType.`application/json`)(jawnDecoderImpl[J])
    EntityDecoder.decodeBy(MediaType.`application/json`)(jawnDecoderImpl[J])

  private[http4s] def jawnDecoderImpl[J](msg: Message)(implicit facade: Facade[J]): DecodeResult[J] =
    DecodeResult {
      msg.body.parseJson(AsyncParser.SingleValue).partialAttempt {
        case pe: ParseException =>
          emit(MalformedMessageBodyFailure("Invalid JSON", Some(pe)))
      }.runLastOr(-\/(MalformedMessageBodyFailure("Invalid JSON: empty body")))
    }
}
