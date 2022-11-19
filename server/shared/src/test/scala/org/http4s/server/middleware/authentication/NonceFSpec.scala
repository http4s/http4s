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

package org.http4s
package server.middleware.authentication

import cats.effect.IO
import cats.effect.std.SecureRandom
import org.scalacheck.Gen
import org.scalacheck.effect.PropF._

class NonceFSpec extends Http4sSuite {
  test("nonce in expected range") {
    SecureRandom.javaSecuritySecureRandom[IO].map { random =>
      forAllF(Gen.chooseNum(1, 240)) { (n: Int) =>
        NonceF
          .gen(random, n)
          .map(nonce => BigInt(nonce.data, 16))
          .map(i => assert(i >= BigInt(0) && i <= BigInt(2).pow(n) - 1, i.toString))
      }
    }
  }

  test("nonce has correct alphabet") {
    val alphabet = "0123456789abcdef".toSet
    SecureRandom.javaSecuritySecureRandom[IO].map { random =>
      forAllF { (_: Unit) =>
        NonceF.gen(random, 160).map(nonce => assert(nonce.data.forall(alphabet), nonce.data))
      }
    }
  }
}
