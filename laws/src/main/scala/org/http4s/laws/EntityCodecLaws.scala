/*
 * Copyright 2019 http4s.org
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
package laws

import cats.effect._
import cats.laws._
import cats.syntax.all._

trait EntityCodecLaws[F[_], A] extends EntityEncoderLaws[F, A] {
  implicit def F: Concurrent[F]
  implicit def encoder: EntityEncoder[F, A]
  implicit def decoder: EntityDecoder[F, A]

  def entityCodecRoundTrip(a: A): IsEq[F[Either[DecodeFailure, A]]] =
    (for {
      entity <- F.pure(encoder.toEntity(a))
      message = Request(body = entity.body, headers = encoder.headers)
      a0 <- decoder.decode(message, strict = true).value
    } yield a0) <-> F.pure(Right(a))
}

object EntityCodecLaws {
  def apply[F[_], A](implicit
      F0: Concurrent[F],
      entityEncoderFA: EntityEncoder[F, A],
      entityDecoderFA: EntityDecoder[F, A],
  ): EntityCodecLaws[F, A] =
    new EntityCodecLaws[F, A] {
      val F: Concurrent[F] = F0
      val encoder: EntityEncoder[F, A] = entityEncoderFA
      val decoder: EntityDecoder[F, A] = entityDecoderFA
    }
}
