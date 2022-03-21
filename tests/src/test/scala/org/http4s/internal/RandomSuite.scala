package org.http4s.internal

import cats.effect.IO
import org.http4s.Http4sSuite
import org.scalacheck.Gen
import org.scalacheck.effect.PropF._

import java.security.SecureRandom
import java.util.{Random => JRandom}
import scala.util.Try

class RandomSuite extends Http4sSuite {
  // Nothing in the tests asserts that it's blocking, but let's try
  private val blockingRandom: JRandom =
    Try(SecureRandom.getInstance("NativePRNGBlocking")).getOrElse(new SecureRandom())

  // Nothing in the tests asserts that it's non-blocking, but let's try
  private val nonBlockingRandom: JRandom =
    Try(SecureRandom.getInstance("NativePRNGNonBlocking")).getOrElse(new SecureRandom())

  test("javaUtilRandomBlocking.nextBigInt") {
    forAllF(Gen.chooseNum(0, 62)) { (n: Int) =>
      Random
        .javaUtilRandomBlocking[IO](testBlocker)(blockingRandom)
        .nextBigInt(n)
        .map(i => (i >= BigInt(0)) && (i < BigInt((1L << n))))
        .assert
    }
  }

  test("javaUtilRandomNonBlocking.nextBigInt") {
    forAllF(Gen.chooseNum(0, 62)) { (n: Int) =>
      Random
        .javaUtilRandomNonBlocking[IO](nonBlockingRandom)
        .nextBigInt(n)
        .map(i => (i >= BigInt(0)) && (i < BigInt((1L << n))))
        .assert
    }
  }

  test("javaSecuritySecureRandom.nextBigInt") {
    Random.javaSecuritySecureRandom[IO](testBlocker).map { random =>
      forAllF(Gen.chooseNum(0, 62)) { (n: Int) =>
        random
          .nextBigInt(n)
          .map(i => (i >= BigInt(0)) && (i < BigInt((1L << n))))
          .assert
      }
    }
  }
}
