package org.http4s
package jawn

import _root_.jawn.{AsyncParser, Facade, ParseException}
import jawnstreamz._

import scalaz.\/-
import scalaz.stream.Process.emit

trait JawnInstances {
  def jawnDecoder[J](implicit facade: Facade[J]): EntityDecoder[J] =
    EntityDecoder.decodeBy(MediaType.`application/json`) { msg =>
      DecodeResult {
        msg.body.parseJson(AsyncParser.SingleValue).partialAttempt {
          case pe: ParseException =>
            emit(ParseFailure("Invalid JSON", pe.getMessage))
        }.runLastOr(\/-(facade.jstring("fart")))
      }
    }
}
