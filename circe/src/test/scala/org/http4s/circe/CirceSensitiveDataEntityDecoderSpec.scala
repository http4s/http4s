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

package org.http4s.circe

import cats.data.EitherT
import cats.effect.IO
import io.circe.{Decoder, HCursor, Json}
import io.circe.syntax._
import org.http4s.{DecodeFailure, InvalidMessageBodyFailure, Response, Status}
import munit.CatsEffectSuite

object CirceSensitiveDataEntityDecoderSpec {

  private final case class Person(ssn: String)
  private object Person {
    implicit val decoder: Decoder[Person] = new Decoder[Person] {
      override def apply(c: HCursor): Decoder.Result[Person] =
        c.downField("ssn").as[String].map(Person(_))
    }
  }

}

class CirceSensitiveDataEntityDecoderSpec extends CatsEffectSuite {

  import CirceSensitiveDataEntityDecoderSpec.Person
  import CirceSensitiveDataEntityDecoder.circeEntityDecoder

  test(
    "should not include the JSON when failing to decode due to wrong data type of JSON key's value") {
    val json: Json = Json.obj("ssn" := 123456789)
    val response: Response[IO] = Response[IO](status = Status.Ok).withEntity[Json](json)
    val attmptedAs: EitherT[IO, DecodeFailure, Person] = response.attemptAs[Person]
    val result: IO[Either[DecodeFailure, Person]] = attmptedAs.value
    result.map { it: Either[DecodeFailure, Person] =>
      it match {
        case Left(InvalidMessageBodyFailure(details, Some(cause))) =>
          assertEquals(details, "Could not decode JSON: <REDACTED>")
          assertEquals(cause.getMessage, "String: DownField(ssn)")
        case other => fail(other.toString)
      }
    }
  }
  test("not include the JSON when failing to decode due to incorrect JSON key's name") {
    val json: Json = Json.obj("the_ssn" := "123456789")
    val response: Response[IO] = Response[IO](status = Status.Ok).withEntity[Json](json)
    val attmptedAs: EitherT[IO, DecodeFailure, Person] = response.attemptAs[Person]
    val result: IO[Either[DecodeFailure, Person]] = attmptedAs.value

    result.map { it: Either[DecodeFailure, Person] =>
      it match {
        case Left(InvalidMessageBodyFailure(details, Some(cause))) =>
          assertEquals(details, "Could not decode JSON: <REDACTED>")
          assertEquals(cause.getMessage, "Attempt to decode value on failed cursor: DownField(ssn)")
        case other => fail(other.toString)
      }
    }
  }
}
