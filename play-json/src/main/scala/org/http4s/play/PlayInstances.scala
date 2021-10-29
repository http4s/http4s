/*
 * Copyright 2018 http4s.org
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

package org.http4s.play

import cats.effect.Sync
import fs2.Chunk
import org.http4s.DecodeResult
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.InvalidMessageBodyFailure
import org.http4s.MediaType
import org.http4s.Message
import org.http4s.Uri
import org.http4s.headers.`Content-Type`
import org.http4s.jawn
import org.http4s.play.Parser.facade
import play.api.libs.json._

trait PlayInstances {
  def jsonOf[F[_]: Sync, A](implicit decoder: Reads[A]): EntityDecoder[F, A] =
    jsonDecoder[F].flatMapR { json =>
      decoder
        .reads(json)
        .fold(
          _ =>
            DecodeResult.failureT(InvalidMessageBodyFailure(s"Could not decode JSON: $json", None)),
          DecodeResult.successT(_)
        )
    }

  implicit def jsonDecoder[F[_]: Sync]: EntityDecoder[F, JsValue] =
    jawn.jawnDecoder[F, JsValue]

  def jsonEncoderOf[F[_], A: Writes]: EntityEncoder[F, A] =
    jsonEncoder[F].contramap[A](Json.toJson(_))

  implicit def jsonEncoder[F[_]]: EntityEncoder[F, JsValue] =
    EntityEncoder[F, Chunk[Byte]]
      .contramap[JsValue] { json =>
        val bytes = json.toString.getBytes("UTF8")
        Chunk.bytes(bytes)
      }
      .withContentType(`Content-Type`(MediaType.application.json))

  implicit val writesUri: Writes[Uri] =
    Writes.of[String].contramap(_.toString)

  implicit val readsUri: Reads[Uri] =
    Reads.of[String].flatMap { str =>
      Uri
        .fromString(str)
        .fold(
          _ => Reads(_ => JsError("Invalid uri")),
          Reads.pure(_)
        )
    }

  implicit class MessageSyntax[F[_]: Sync](self: Message[F]) {
    def decodeJson[A: Reads]: F[A] =
      self.as(implicitly, jsonOf[F, A])
  }
}
