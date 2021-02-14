/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.core

import cats.effect._
import cats.syntax.all._
import fs2._
import fs2.concurrent.Queue
import org.http4s.Http4sSuite
import org.scalacheck.Gen
import org.scalacheck.Prop._
import org.scalacheck.effect.PropF

class ParserChunkingSuite extends Http4sSuite {

  object Helpers {
    def taking[F[_]: Concurrent, A](stream: Stream[F, A]): F[F[Option[Chunk[A]]]] =
      for {
        q <- Queue.unbounded[F, Option[Chunk[A]]]
        _ <- stream.chunks.map(Some(_)).evalMap(q.enqueue1(_)).compile.drain.void
        _ <- q.enqueue1(None)
      } yield q.dequeue1

    def subdivided[A](as: List[A], count: Int): Gen[List[List[A]]] = {
      def go(out: List[List[A]], remaining: Int): Gen[List[List[A]]] =
        if (remaining > 0) {
          Gen.chooseNum(0, out.length - 1).flatMap { idx =>
            val prefix = out.take(idx)
            val curr = out(idx)
            val suffix = out.drop(idx + 1)

            Gen.chooseNum(0, curr.length - 1).flatMap { splitIdx =>
              val (l, r) = curr.splitAt(splitIdx)
              val split = List(l, r).filterNot(_.isEmpty)
              val next = List(prefix, split, suffix).filterNot(_.isEmpty).flatten
              go(next, remaining - 1)
            }
          }
        } else Gen.const(out)

      Gen.delay(go(List(as), count))
    }
  }

  // println("------")
  // segments.foreach { s =>
  //   s.foreach { byte =>
  //     if (byte == 13) print("CR")
  //     else if (byte == 10) print("NL")
  //     else print(new String(Array[Byte](byte)))
  //   }
  //   println()
  // }

  // println()

  // yield messageBytes == segments.flatten).handleErrorWith(e => {
  //   e.printStackTrace()
  //   IO(false)
  // }).assert

  test("parse single response") {
    val messageBytes = List(
      "HTTP/1.1 200 OK\r\n",
      "Content-Type: text/plain\r\n",
      "Transfer-Encoding: chunked\r\n\r\n",
      "7\r\n",
      "Mozilla\r\n",
      "9\r\n",
      "Developer\r\n",
      "7\r\n",
      "Network\r\n",
      "0\r\n",
      "\r\n"
    ).mkString.getBytes(java.nio.charset.StandardCharsets.US_ASCII).toList

    PropF.forAllNoShrinkF(Helpers.subdivided(messageBytes, 1)) { segments =>
      (for {
        read <- Helpers.taking[IO, Byte](
          segments.map(bytes => Stream.chunk(Chunk.seq(bytes))).reduceLeft(_ ++ _))
        result <- Parser.Response.parser(Int.MaxValue)(Array.emptyByteArray, read)
      } yield true).assert
    }
  }

  test("subdivided") {
    val gen: Gen[(List[Int], List[List[Int]])] = for {
      size <- Gen.choose(1, 100)
      count <- Gen.choose(1, 100)
      list <- Gen.listOfN(size, Gen.choose(Int.MinValue, Int.MaxValue))
      divided <- Helpers.subdivided(list, count)
    } yield (list, divided)

    forAll(gen) { case (a, b) =>
      a == b.flatten
    }
  }

}
