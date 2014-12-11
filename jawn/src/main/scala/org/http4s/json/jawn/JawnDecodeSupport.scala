package org.http4s
package json
package jawn

import _root_.jawn.{AsyncParser, Facade, ParseException}
import jawnstreamz.JsonSourceSyntax

import scalaz.\/-
import scalaz.stream.Process._

trait JawnDecodeSupport[J] extends JsonDecodeSupport[J] {
  protected implicit def jawnFacade: Facade[J]

  override def decodeJson(body: EntityBody): DecodeResult[J] = DecodeResult {
    body.parseJson(AsyncParser.SingleValue).partialAttempt {
      case pe: ParseException => emit(ParseFailure("Invalid JSON", pe.getMessage))
    }.runLastOr(\/-(jawnFacade.jnull()))
  }
}
