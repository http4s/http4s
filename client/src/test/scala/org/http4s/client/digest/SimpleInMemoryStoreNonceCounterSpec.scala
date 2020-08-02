/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org
package http4s
package client
package digest

import java.util.concurrent.CountDownLatch

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits.{catsStdInstancesForList, toTraverseOps}
import org.http4s.testing.Http4sLegacyMatchersIO

import scala.concurrent.duration._

class SimpleInMemoryStoreNonceCounterSpec extends Http4sSpec with Http4sLegacyMatchersIO {

  "SimpleInMemoryStoreNonceCounter" should {
    "start counter from 1" in {
      val nc = NonceCounter.defaultNonceCounter[IO]
      (for {
        (_, counter) <- nc.next("nonce")
      } yield counter must_== 1L).unsafeRunSync()
    }
    "increment counters by 1 and stay with same cnonce" in {
      val nc = NonceCounter.defaultNonceCounter[IO]
      (for {
        (cn1, c1) <- nc.next("nonce")
        (cn2, c2) <- nc.next("nonce")
        (cn3, c3) <- nc.next("nonce")
      } yield {
        c1 must_== 1L
        c2 must_== 2L
        c3 must_== 3L
        cn1 must_== cn2
        cn2 must_== cn3
      }).unsafeRunSync()
    }
    "increment counters by 1 and stay with same cnonce until max and then next cnonce, check concurrent work" in {
      val nc = new SimpleInMemoryStoreNonceCounter[IO](1.minute, NonceCounter.nextCnonce, 100)
      val checkMapRef: Ref[IO, Map[String, List[(String, Long)]]] = Ref.unsafe(Map.empty)
      val latch = new CountDownLatch(1)
      def awaitingLatch: IO[Unit] =
        for {
          _ <- IO(latch.await())
          (cnonce, nonceCounter) <- nc.next("nonce")
          _ <- checkMapRef.update { nonceMap =>
            val counters = nonceMap.getOrElse(cnonce, List.empty)
            val nextCounters = counters :+ ((cnonce, nonceCounter))
            nonceMap + (cnonce -> nextCounters)
          }
        } yield ()

      val runningFibers = (1L to 150L).toList.map(_ => awaitingLatch.start)
      latch.countDown()

      (for {
        list <- runningFibers.traverse(identity)
        _ <- list.map(_.join).traverse(identity)
        map <- checkMapRef.get
      } yield {
        map.size must_== 2
        val key50 = map.minBy(_._2.size)._1
        val key100 = map.maxBy(_._2.size)._1
        map(key50).map(_._2).sorted must_== (1L to 50L).toList
        map(key100).map(_._2).sorted must_== (1L to 100L).toList
      }).unsafeRunSync()
    }

    "reply with new cnonce for new nonce" in {
      val nc = NonceCounter.defaultNonceCounter[IO]
      (for {
        (cn1, c1) <- nc.next("nonce1")
        (cn2, c2) <- nc.next("nonce2")
      } yield {
        c1 must_== 1L
        c2 must_== 1L
        cn1 must_!== cn2
      }).unsafeRunSync()
    }
    "auto clean up nonce data when not used more than configured time and start as from new" in {
      val nonceTtl = 1.seconds
      val nc = new SimpleInMemoryStoreNonceCounter[IO](
        nonceTtl = nonceTtl,
        cnonceF = NonceCounter.nextCnonce)
      (for {
        (cn1, c1) <- nc.next("nonce")
        (cn2, c2) <- nc.next("nonce")
        _ <- IO.sleep(2 * nonceTtl)
        (cn3, c3) <- nc.next("nonce")
        (cn4, c4) <- nc.next("nonce")
      } yield {
        c1 must_== 1L
        c2 must_== 2L
        cn1 must_== cn2
        c3 must_== 1L
        c4 must_== 2L
        cn3 must_== cn4
        cn2 must_!== cn3
      }).unsafeRunSync()
    }

    "reply with new cnonce after evict call" in {
      val nc = NonceCounter.defaultNonceCounter[IO]
      (for {
        (cn1, c1) <- nc.next("nonce")
        _ <- nc.evict("nonce")
        (cn2, c2) <- nc.next("nonce")
      } yield {
        c1 must_== 1L
        c2 must_== 1L
        cn1 must_!== cn2
      }).unsafeRunSync()
    }
    "reply with same cnonce when evict another nonce call" in {
      val nc = NonceCounter.defaultNonceCounter[IO]
      (for {
        (cn1, c1) <- nc.next("nonce")
        _ <- nc.evict("another-nonce")
        (cn2, c2) <- nc.next("nonce")
      } yield {
        c1 must_== 1L
        c2 must_== 2L
        cn1 must_== cn2
      }).unsafeRunSync()
    }
    "reply with new cnonce when evictAll call" in {
      val nc = NonceCounter.defaultNonceCounter[IO]
      (for {
        (_, c1) <- nc.next("nonce1")
        (_, c2) <- nc.next("nonce2")
        _ <- nc.evictAll
        (_, c3) <- nc.next("nonce1")
        (_, c4) <- nc.next("nonce2 ")
      } yield {
        c1 must_== 1L
        c2 must_== 1L
        c3 must_== 1L
        c4 must_== 1L
      }).unsafeRunSync()
    }

  }
}
