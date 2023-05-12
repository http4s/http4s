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
import io.circe.Decoder
import io.circe.HCursor
import io.circe.Json
import io.circe.syntax._
import org.http4s.DecodeFailure
import org.http4s.Http4sSuite
import org.http4s.InvalidMessageBodyFailure
import org.http4s.Response
import org.http4s.Status

object CirceSensitiveDataEntityDecoderSpec {

  private final case class Person(ssn: String)
  private object Person {
    implicit val decoder: Decoder[Person] = new Decoder[Person] {
      override def apply(c: HCursor): Decoder.Result[Person] =
        c.downField("ssn").as[String].map(Person(_))
    }
  }

}

class CirceSensitiveDataEntityDecoderSpec extends Http4sSuite {

  import CirceSensitiveDataEntityDecoderSpec.Person
  import CirceSensitiveDataEntityDecoder.circeEntityDecoder

  test(
    "should not include the JSON when failing to decode due to wrong data type of JSON key's value"
  ) {
    val json: Json = Json.obj("ssn" := 123456789)
    val response: Response[IO] = Response[IO](status = Status.Ok).withEntity[Json](json)
    val attmptedAs: EitherT[IO, DecodeFailure, Person] = response.attemptAs[Person]
    val result: IO[Either[DecodeFailure, Person]] = attmptedAs.value
    result.map { (it: Either[DecodeFailure, Person]) =>
      it match {
        case Left(InvalidMessageBodyFailure(details, Some(cause))) =>
          assertEquals(details, "Could not decode JSON: <REDACTED>")
          assertEquals(
            cause.getMessage,
            "DecodingFailure at .ssn: Got value '123456789' with wrong type, expecting string",
          )
        case other => fail(other.toString)
      }
    }
  }
  test("not include the JSON when failing to decode due to incorrect JSON key's name") {
    val json: Json = Json.obj("the_ssn" := "123456789")
    val response: Response[IO] = Response[IO](status = Status.Ok).withEntity[Json](json)
    val attmptedAs: EitherT[IO, DecodeFailure, Person] = response.attemptAs[Person]
    val result: IO[Either[DecodeFailure, Person]] = attmptedAs.value

    result.map { (it: Either[DecodeFailure, Person]) =>
      it match {
        case Left(InvalidMessageBodyFailure(details, Some(cause))) =>
          assertEquals(details, "Could not decode JSON: <REDACTED>")
          assertEquals(cause.getMessage, "DecodingFailure at .ssn: Missing required field")
        case other => fail(other.toString)
      }
    }
  }
}
