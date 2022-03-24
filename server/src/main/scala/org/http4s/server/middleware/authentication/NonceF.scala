/*
 * Copyright 2014 http4s.org
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

package org.http4s.server.middleware.authentication

import cats.Functor
import cats.effect.Clock
import cats.effect.Sync
import cats.effect.Timer
import cats.effect.concurrent.Ref
import cats.syntax.all._
import org.http4s.internal.Random

import scala.concurrent.duration.MILLISECONDS

private[authentication] class NonceF[F[_]](
    val createdMillis: Long,
    val nc: Ref[F, Int],
    val data: String,
)

private[authentication] object NonceF {
  private def getRandomData[F[_]: Functor](random: Random[F], bits: Int): F[String] =
    random.nextBigInt(bits).map(_.toString(16))

  def gen[F[_]](random: Random[F], bits: Int)(implicit
      F: Sync[F],
      t: Timer[F],
  ): F[NonceF[F]] = for {
    nc <- Ref[F].of(0)
    data <- getRandomData[F](random, bits)
    createdMillis <- Clock[F].monotonic(MILLISECONDS)
  } yield new NonceF(createdMillis, nc, data)
}
