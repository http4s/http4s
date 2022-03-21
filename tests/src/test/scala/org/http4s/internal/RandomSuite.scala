/*
 * Copyright 2013 http4s.org
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

  test("pool.nextBigInt") {
    Random.pool[IO](8)(IO(Random.javaUtilRandomNonBlocking(new JRandom()))).map { random =>
      forAllF(Gen.chooseNum(0, 62)) { (n: Int) =>
        random
          .nextBigInt(n)
          .map(i => (i >= BigInt(0)) && (i < BigInt((1L << n))))
          .assert
      }
    }
  }
}
