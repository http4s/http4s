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
import org.http4s.headers.`Content-Length`
import org.http4s.headers.`Transfer-Encoding`

trait EntityEncoderLaws[F[_], A] {
  implicit def F: Concurrent[F]

  implicit def encoder: EntityEncoder[F, A]

  def accurateContentLengthIfDefined(a: A): IsEq[F[Boolean]] =
    (for {
      entity <- F.pure(encoder.toEntity(a))
      body <- entity.body.compile.toVector
      bodyLength = body.size.toLong
      contentLength = entity.length
    } yield contentLength.fold(true)(_ === bodyLength)) <-> F.pure(true)

  def noContentLengthInStaticHeaders: Boolean =
    !encoder.headers.contains[`Content-Length`]

  def noTransferEncodingInStaticHeaders: Boolean =
    !encoder.headers.contains[`Transfer-Encoding`]
}

object EntityEncoderLaws {
  def apply[F[_], A](implicit
      F0: Concurrent[F],
      entityEncoderFA: EntityEncoder[F, A],
  ): EntityEncoderLaws[F, A] =
    new EntityEncoderLaws[F, A] {
      val F: Concurrent[F] = F0
      val encoder: EntityEncoder[F, A] = entityEncoderFA
    }
}
