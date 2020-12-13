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
import org.http4s.{DecodeFailure, Http4sSpec, InvalidMessageBodyFailure, Response, Status}

object CirceSensitiveDataEntityDecoderSpec {

  private final case class Person(ssn: String)
  private object Person {
    implicit val decoder: Decoder[Person] = new Decoder[Person] {
      override def apply(c: HCursor): Decoder.Result[Person] =
        c.downField("ssn").as[String].map(Person(_))
    }
  }

}

class CirceSensitiveDataEntityDecoderSpec extends Http4sSpec {

  import CirceSensitiveDataEntityDecoderSpec.Person

  "CirceSensitiveDataEntityDecoder" should {
    import CirceSensitiveDataEntityDecoder.circeEntityDecoder

    "not include the JSON when failing to decode due to wrong data type of JSON key's value" in {
      val json: Json = Json.obj("ssn" := 123456789)
      val response: Response[IO] = Response[IO](status = Status.Ok).withEntity[Json](json)
      val attmptedAs: EitherT[IO, DecodeFailure, Person] = response.attemptAs[Person]
      val result: Either[DecodeFailure, Person] = attmptedAs.value.unsafeRunSync()

      result match {
        case Left(InvalidMessageBodyFailure(details, Some(cause))) =>
          details ==== "Could not decode JSON: <REDACTED>"
          println(cause.getMessage)
          cause.getMessage ==== "String: DownField(ssn)"
        case other => ko(other.toString)
      }
    }
    "not include the JSON when failing to decode due to incorrect JSON key's name" in {
      val json: Json = Json.obj("the_ssn" := "123456789")
      val response: Response[IO] = Response[IO](status = Status.Ok).withEntity[Json](json)
      val attmptedAs: EitherT[IO, DecodeFailure, Person] = response.attemptAs[Person]
      val result: Either[DecodeFailure, Person] = attmptedAs.value.unsafeRunSync()

      result match {
        case Left(InvalidMessageBodyFailure(details, Some(cause))) =>
          details ==== "Could not decode JSON: <REDACTED>"
          println(cause.getMessage)
          cause.getMessage ==== "Attempt to decode value on failed cursor: DownField(ssn)"
        case other => ko(other.toString)
      }
    }
  }

}
