package org.http4s
package server.middleware.authentication

import cats.effect.IO
import cats.effect.std.Random
import org.scalacheck.Gen
import org.scalacheck.effect.PropF._


class NonceFSpec extends Http4sSuite {
  test("nonce in expected range") {
    Random.javaSecuritySecureRandom[IO].map { random =>
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
    Random.javaSecuritySecureRandom[IO].map { random =>
      forAllF { (_: Unit) =>
        NonceF.gen(random, 160).map(nonce => assert(nonce.data.forall(alphabet), nonce.data))
      }
    }
  }
}
