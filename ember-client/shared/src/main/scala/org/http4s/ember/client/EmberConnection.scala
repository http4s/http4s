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

package org.http4s.ember.client

import cats._
import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.kernel.Ref
import cats.syntax.all._

private[ember] final case class EmberConnection[F[_]](
    keySocket: RequestKeySocket[F],
    protocol: Option[String],
    shutdown: F[Unit],
    nextBytes: Ref[F, Array[Byte]],
)(implicit F: MonadThrow[F]) {
  def cleanup: F[Unit] =
    nextBytes.set(Array.emptyByteArray) >>
      keySocket.socket.endOfInput.attempt.void >>
      keySocket.socket.endOfOutput.attempt.void >>
      shutdown.attempt.void
}

private[ember] object EmberConnection {
  def apply[F[_]: Concurrent](
      keySocketResource: Resource[F, (RequestKeySocket[F], Option[String])]
  ): F[EmberConnection[F]] =
    Ref[F].of(Array.emptyByteArray).flatMap { nextBytes =>
      val keySocketResourceAllocated: F[((RequestKeySocket[F], Option[String]), F[Unit])] =
        keySocketResource.allocated
      keySocketResourceAllocated.map { case ((keySocket, opt), release) =>
        EmberConnection(keySocket, opt, release, nextBytes)
      }
    }
}
