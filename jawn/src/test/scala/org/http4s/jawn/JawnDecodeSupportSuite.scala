/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package jawn

import cats.syntax.all._
import cats.effect.IO

trait JawnDecodeSupportSuite[J] extends Http4sSuite {
  def testJsonDecoder(decoder: EntityDecoder[IO, J]) = {
    test("return right when the entity is valid") {
      val resp = Response[IO](Status.Ok).withEntity("""{"valid": true}""")
      decoder.decode(resp, strict = false).value.map(_.isRight).assertEquals(true)
    }

    testErrors(decoder)(
      emptyBody = { case MalformedMessageBodyFailure("Invalid JSON: empty body", _) => true },
      parseError = { case MalformedMessageBodyFailure("Invalid JSON", _) => true }
    )
  }

  def testJsonDecoderError(decoder: EntityDecoder[IO, J])(
      emptyBody: PartialFunction[DecodeFailure, Boolean],
      parseError: PartialFunction[DecodeFailure, Boolean]
  ) =
    test("json decoder with custom errors") {
      testErrors(decoder)(emptyBody = emptyBody, parseError = parseError)
    }

  private def testErrors(decoder: EntityDecoder[IO, J])(
      emptyBody: PartialFunction[DecodeFailure, Boolean],
      parseError: PartialFunction[DecodeFailure, Boolean]
  ) = {
    test("return a ParseFailure when the entity is invalid") {
      val resp = Response[IO](Status.Ok).withEntity("""garbage""")
      decoder
        .decode(resp, strict = false)
        .value
        .map(_.leftMap(r => emptyBody.applyOrElse(r, (_: DecodeFailure) => false)))
    }

    test("return a ParseFailure when the entity is empty") {
      val resp = Response[IO](Status.Ok).withEntity("")
      decoder
        .decode(resp, strict = false)
        .value
        .map(_.leftMap(r => parseError.applyOrElse(r, (_: DecodeFailure) => false)))
    }
  }
}
