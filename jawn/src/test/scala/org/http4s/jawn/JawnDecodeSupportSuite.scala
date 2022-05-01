/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package jawn

import cats.effect.IO
import cats.syntax.all._

trait JawnDecodeSupportSuite[J] extends Http4sSuite {
  def testJsonDecoder(decoder: EntityDecoder[IO, J]): Unit = {
    test("return right when the entity is valid") {
      val resp = Response[IO](Status.Ok).withEntity("""{"valid": true}""")
      decoder.decode(resp, strict = false).value.map(_.isRight).assert
    }

    testErrors(decoder)(
      emptyBody = { case MalformedMessageBodyFailure("Invalid JSON: empty body", _) => true },
      parseError = { case MalformedMessageBodyFailure("Invalid JSON", _) => true },
    )
  }

  def testJsonDecoderError(decoder: EntityDecoder[IO, J])(
      emptyBody: PartialFunction[DecodeFailure, Boolean],
      parseError: PartialFunction[DecodeFailure, Boolean],
  ): Unit =
    test("json decoder with custom errors") {
      testErrors(decoder)(emptyBody = emptyBody, parseError = parseError)
    }

  def testErrors(decoder: EntityDecoder[IO, J])(
      emptyBody: PartialFunction[DecodeFailure, Boolean],
      parseError: PartialFunction[DecodeFailure, Boolean],
  ): Unit = {
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
