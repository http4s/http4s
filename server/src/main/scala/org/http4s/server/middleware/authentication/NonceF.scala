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

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.all._
import org.http4s.crypto.unsafe.SecureRandom

import java.math.BigInteger
import java.util.Date

private[authentication] class NonceF[F[_]](val created: Date, val nc: Ref[F, Int], val data: String)

private[authentication] object NonceF {
  val random = new SecureRandom()

  private def getRandomData(bits: Int): String = new BigInteger(bits, random).toString(16)

  def gen[F[_]: Sync](bits: Int): F[NonceF[F]] =
    Ref[F].of(0).map(nc => new NonceF(new Date(), nc, getRandomData(bits)))
}
