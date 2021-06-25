/*
 * Copyright 2015 http4s.org
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
package circe.test // Get out of circe package so we can import custom instances

import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import io.circe._
import io.circe.jawn.CirceSupportParser
import org.http4s.circe._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSuite
import cats.data.EitherT
import org.typelevel.jawn.ParseException

trait CirceSuitePlatform extends JawnDecodeSupportSuite[Json] { self: CirceSuite =>

  val CirceInstancesWithCustomErrors = CirceInstances.builder
    .withEmptyBodyMessage(MalformedMessageBodyFailure("Custom Invalid JSON: empty body"))
    .withJawnParseExceptionMessage(_ => MalformedMessageBodyFailure("Custom Invalid JSON jawn"))
    .withCirceParseExceptionMessage(_ => MalformedMessageBodyFailure("Custom Invalid JSON circe"))
    .withJsonDecodeError { (json, failures) =>
      val failureStr = failures.mkString_("", ", ", "")
      InvalidMessageBodyFailure(
        s"Custom Could not decode JSON: ${json.noSpaces}, errors: $failureStr")
    }
    .build

  test("should successfully decode when parser allows duplicate keys") {
    val circeInstanceAllowingDuplicateKeys = CirceInstances.builder
      .withCirceSupportParser(
        new CirceSupportParser(maxValueSize = None, allowDuplicateKeys = true))
      .build
    val req = Request[IO]()
      .withEntity("""{"bar": 1, "bar":2}""")
      .withContentType(`Content-Type`(MediaType.application.json))

    val decoder = circeInstanceAllowingDuplicateKeys.jsonOf[IO, Foo]
    val result = decoder.decode(req, true).value

    result
      .map {
        case Right(Foo(2)) => true
        case _ => false
      }
      .assertEquals(true)
  }

  test("should should error out when parser does not allow duplicate keys") {
    val circeInstanceNotAllowingDuplicateKeys = CirceInstances.builder
      .withCirceSupportParser(
        new CirceSupportParser(maxValueSize = None, allowDuplicateKeys = false))
      .build
    val req = Request[IO]()
      .withEntity("""{"bar": 1, "bar":2}""")
      .withContentType(`Content-Type`(MediaType.application.json))

    val decoder = circeInstanceNotAllowingDuplicateKeys.jsonOf[IO, Foo]
    val result = decoder.decode(req, true).value
    result
      .map {
        case Left(
              MalformedMessageBodyFailure(
                "Invalid JSON",
                Some(ParsingFailure("Invalid json, duplicate key name found: bar", _)))) =>
          true
        case _ => false
      }
      .assertEquals(true)
  }

  test("stream json array decoder should return stream that fails when run on improper JSON") {
    (for {
      stream <- streamJsonArrayDecoder[IO].decode(
        Media(
          Stream.fromIterator[IO](
            """[{"test1":"CirceSupport"},{"test2":CirceSupport"}]""".getBytes.iterator,
            128),
          Headers("content-type" -> "application/json")
        ),
        true
      )
      list <- EitherT(
        stream.map(Printer.noSpaces.print).compile.toList.map(_.asRight[DecodeFailure]))
    } yield list).value.attempt
      .assertEquals(Left(
        ParseException("expected json value got 'CirceS...' (line 1, column 36)", 35, 1, 36)))
  }

}
