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

package org
package http4s
package client
package digest

import cats.effect.Ref
import cats.effect._
import cats.effect.std.CountDownLatch
import cats.effect.testkit.TestControl
import cats.syntax.all._

import scala.concurrent.duration._

class SimpleInMemoryStoreNonceCounterSpec extends Http4sSuite {

  test("SimpleInMemoryStoreNonceCounter should start counter from 1") {
    (for {
      nc <- NonceCounter.make[IO]()
      tuple <- nc.next("nonce")
      (_, counter) = tuple
    } yield counter).assertEquals(1L)
  }

  test("increment counters by 1 and stay with same cnonce") {
    NonceCounter
      .make[IO]()
      .flatMap { nc =>
        (nc.next("nonce"), nc.next("nonce"), nc.next("nonce"))
          .mapN { case ((cn1, c1), (cn2, c2), (cn3, c3)) =>
            (c1 == 1L) &&
            (c2 == 2L) &&
            (c3 == 3L) &&
            (cn1 == cn2) &&
            (cn2 == cn3)
          }
      }
      .assert
  }
  test(
    "increment counters by 1 and stay with same cnonce until max and then next cnonce, check concurrent work"
  ) {
    type NonceCounters = Map[String, List[(String, Long)]]

    def awaitingLatch(
        nc: NonceCounter[IO],
        latch: CountDownLatch[IO],
        checkMapRef: Ref[IO, NonceCounters],
    ): IO[Unit] =
      for {
        _ <- latch.await
        tuple <- nc.next("nonce")
        (cnonce, nonceCounter) = tuple
        _ <- checkMapRef.update { nonceMap =>
          val counters = nonceMap.getOrElse(cnonce, List.empty)
          val nextCounters = counters :+ ((cnonce, nonceCounter))
          nonceMap + (cnonce -> nextCounters)
        }
      } yield ()

    (for {
      nc <- NonceCounter.make[IO](maxNonce = 100)
      latch <- CountDownLatch[IO](1)
      checkMapRef <- Ref.of[IO, NonceCounters](Map.empty)
      runningFibers <- (1L to 150L).toList.traverse(_ =>
        awaitingLatch(nc, latch, checkMapRef).start
      )
      _ <- latch.release
      _ <- runningFibers.traverse(_.join)
      map <- checkMapRef.get
    } yield {
      val key50 = map.minBy(_._2.size)._1
      val key100 = map.maxBy(_._2.size)._1

      map.size == 2 &&
      map(key50).map(_._2).sorted == (1L to 50L).toList &&
      map(key100).map(_._2).sorted == (1L to 100L).toList
    }).assert
  }

  test("reply with new cnonce for new nonce") {

    NonceCounter
      .make[IO]()
      .flatMap { nc =>
        (nc.next("nonce1"), nc.next("nonce2")).mapN { case ((cn1, c1), (cn2, c2)) =>
          (c1 == 1L) && (c2 == 1L) && (cn1 != cn2)
        }
      }
      .assert
  }

  test("auto clean up nonce data when not used more than configured time and start as from new") {
    val ttl = 1.second
    val prog = for {
      nc <- NonceCounter.make[IO](ttl = ttl)
      tuple1 <- nc.next("nonce")
      (cn1, c1) = tuple1
      tuple2 <- nc.next("nonce")
      (cn2, c2) = tuple2
      _ <- IO.sleep(2 * ttl)
      tuple3 <- nc.next("nonce")
      (cn3, c3) = tuple3
      tuple4 <- nc.next("nonce")
      (cn4, c4) = tuple4
    } yield c1 == 1L &&
      c2 == 2L &&
      cn1 == cn2 &&
      c3 == 1L &&
      c4 == 2L &&
      cn3 == cn4 &&
      cn2 != cn3

    TestControl.executeEmbed(prog).assert
  }

  test("reply with new cnonce after evict call") {
    (for {
      nc <- NonceCounter.make[IO]()
      tuple1 <- nc.next("nonce")
      (cn1, c1) = tuple1
      _ <- nc.evict("nonce")
      tuple2 <- nc.next("nonce")
      (cn2, c2) = tuple2
    } yield c1 == 1L &&
      c2 == 1L &&
      cn1 != cn2).assert
  }

  // Is there a value in testing this?
  test("reply with same cnonce when evict another nonce call") {
    (for {
      nc <- NonceCounter.make[IO]()
      tuple1 <- nc.next("nonce")
      (cn1, c1) = tuple1
      _ <- nc.evict("another-nonce")
      tuple2 <- nc.next("nonce")
      (cn2, c2) = tuple2
    } yield c1 == 1L &&
      c2 == 2L &&
      cn1 == cn2).assert
  }

  test("reply with new cnonce when evictAll call in performed") {
    (for {
      nc <- NonceCounter.make[IO]()
      tuple1 <- nc.next("nonce1")
      (_, c1) = tuple1
      tuple2 <- nc.next("nonce2")
      (_, c2) = tuple2
      _ <- nc.evictAll
      tuple3 <- nc.next("nonce1")
      (_, c3) = tuple3
      tuple4 <- nc.next("nonce2 ")
      (_, c4) = tuple4
    } yield c1 == 1L &&
      c2 == 1L &&
      c3 == 1L &&
      c4 == 1L).assert
  }
}
