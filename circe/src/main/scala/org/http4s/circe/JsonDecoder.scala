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

import cats.effect.Concurrent
import io.circe._
import org.http4s._

/** F-algebra for separating the Sync required for extracting
  * the Json from the body. As such if F is Sync at some layer,
  * then this can be used to extract without the lower layer
  * needing to be aware of the strong constraint.
  */
trait JsonDecoder[F[_]] {
  def asJson(m: Message[F]): F[Json]
  def asJsonDecode[A: Decoder](m: Message[F]): F[A]
}

object JsonDecoder {
  def apply[F[_]](implicit ev: JsonDecoder[F]): JsonDecoder[F] = ev

  implicit def impl[F[_]: Concurrent]: JsonDecoder[F] =
    new JsonDecoder[F] {
      def asJson(m: Message[F]): F[Json] = m.as[Json]
      def asJsonDecode[A: Decoder](m: Message[F]): F[A] = m.decodeJson
    }
}
