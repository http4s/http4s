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

import org.http4s.crypto.unsafe.SecureRandom

import java.math.BigInteger
import java.util.Date

@deprecated("Contains mutable java.util.Date. Use NonceF.", "0.22.13")
private[authentication] class Nonce(val created: Date, var nc: Int, val data: String)

@deprecated("Untracked side effects. Use NonceF.", "0.22.13")
private[authentication] object Nonce {
  val random = new SecureRandom()

  private def getRandomData(bits: Int): String = new BigInteger(bits, random).toString(16)

  def gen(bits: Int): Nonce = new Nonce(new Date(), 0, getRandomData(bits))
}
