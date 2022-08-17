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
import cats.effect.Async
import cats.effect.Ref
import cats.effect.std.Random
import cats.syntax.all._

import scala.concurrent.duration.FiniteDuration

private[authentication] class NonceF[F[_]](
    val created: FiniteDuration,
    val nc: Ref[F, Int],
    val data: String,
)

private[authentication] object NonceF {
  private[this] val hexAlphabet = "0123456789abcdef".toArray

  private def bytesToHex(arr: Array[Byte]): String = {
    val sb = new StringBuilder(arr.size * 2)
    for (i <- 0 until arr.size) {
      val b = arr(i) & 0xff
      sb.append(hexAlphabet(b >>> 4))
      sb.append(hexAlphabet(b & 0x0f))
    }
    sb.toString
  }

  private def getRandomBits[F[_]: Functor](random: Random[F], bits: Int): F[Array[Byte]] = {
    val bytes = (bits + 7) / 8
    random.nextBytes(bytes).map { arr =>
      if (arr.nonEmpty) {
        val extraBits = 8 * bytes - bits
        arr(0) = (arr(0) & ((1 << (8 - extraBits)) - 1)).toByte
      }
      arr
    }
  }

  def getRandomData[F[_]: Functor](random: Random[F], bits: Int): F[String] =
    getRandomBits[F](random, bits).map(bytesToHex)

  def gen[F[_]](random: Random[F], bits: Int)(implicit
      F: Async[F]
  ): F[NonceF[F]] = for {
    nc <- Ref[F].of(0)
    data <- getRandomData[F](random, bits)
    created <- F.monotonic
  } yield new NonceF(created, nc, data)
}
