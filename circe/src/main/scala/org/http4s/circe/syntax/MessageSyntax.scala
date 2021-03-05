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

package org.http4s.circe.syntax

import org.http4s.Message
import org.http4s.circe.JsonDecoder
import org.http4s.circe.implicits._
import io.circe.Json
import io.circe.Decoder
import cats.effect.Sync

trait MessageSyntax {
  implicit def messageSyntax[F[_]: JsonDecoder](req: Message[F]): MessageOps[F] =
    new MessageOps[F](req)
}

final class MessageOps[F[_]: JsonDecoder](val req: Message[F]) {
  def asJson: F[Json] =
    JsonDecoder[F].asJson(req)

  def asJsonDecode[A](implicit decoder: Decoder[A]): F[A] =
    JsonDecoder[F].asJsonDecode(req)

  def decodeJson[A](implicit F: Sync[F], decoder: Decoder[A]): F[A] =
    req.as(F, jsonOf[F, A])

  def json(implicit F: Sync[F]): F[Json] =
    req.as(F, jsonDecoder[F])
}
