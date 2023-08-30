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
import cats.effect.std.Queue
import cats.syntax.all._
import fs2._
import org.http4s.Http4sSuite
import org.scalacheck.Gen
import org.scalacheck.Prop._
import org.scalacheck.effect.PropF

class StreamingParserSuite extends Http4sSuite {

  object Helpers {
    def taking[F[_]: Concurrent, A](segments: List[List[A]]): F[F[Option[Chunk[A]]]] =
      for {
        q <- Queue.unbounded[F, Option[Chunk[A]]]
        _ <- segments.traverse(bytes => q.offer(Some(Chunk.from(bytes))))
        _ <- q.offer(None)
      } yield q.take

    def subdivided[A](as: List[A], count: Int): Gen[List[List[A]]] = {
      def go(out: List[List[A]], remaining: Int): Gen[List[List[A]]] =
        if (remaining <= 0) Gen.const(out)
        else {
          Gen.chooseNum(0, out.length - 1).flatMap { idx =>
            val prefix = out.take(idx)
            val curr = out(idx)
            val suffix = out.drop(idx + 1)

            if (curr.length == 1) {
              // No splits to perform
              go(out, remaining - 1)
            } else {
              // Guarantees a split happens
              Gen.chooseNum(1, curr.length - 1).flatMap { splitIdx =>
                val (l, r) = curr.splitAt(splitIdx)
                val split = List(l, r)
                val next = List(prefix, split, suffix).flatten
                go(next, remaining - 1)
              }
            }
          }
        }

      if (as.isEmpty) Gen.const(Nil) else Gen.delay(go(List(as), count))
    }
  }

  object Fixtures {
    private val RequestFixedBody = toBytes(
      List(
        "POST /foo HTTP/1.1\r\n",
        "Content-Type: text/plain\r\n",
        "Content-Length: 5\r\n\r\n",
        "hello",
      )
    )

    private val RequestChunkedBody = toBytes(
      List(
        "POST /foo HTTP/1.1\r\n",
        "Content-Type: text/plain\r\n",
        "Transfer-Encoding: chunked\r\n\r\n",
        "7\r\n",
        "Mozilla\r\n",
        "9\r\n",
        "Developer\r\n",
        "7\r\n",
        "Network\r\n",
        "0\r\n",
        "\r\n",
      )
    )

    private val ResponseFixedBody = toBytes(
      List(
        "HTTP/1.1 200 OK\r\n",
        "Content-Type: text/plain\r\n",
        "Content-Length: 5\r\n\r\n",
        "hello",
      )
    )

    private val ResponseVariableBody = toBytes(
      List(
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
        "\r\n",
      )
    )

    // followup: try to generate raw http messages
    def genRequest(min: Int = 0, max: Int = 25): Gen[List[List[Byte]]] =
      for {
        req <- Gen.oneOf(RequestFixedBody, RequestChunkedBody)
        splits <- Gen.chooseNum(min, max)
        chunks <- Helpers.subdivided(req, splits)
      } yield chunks
    def genResponse(min: Int = 0, max: Int = 25): Gen[List[List[Byte]]] =
      for {
        req <- Gen.oneOf(ResponseFixedBody, ResponseVariableBody)
        splits <- Gen.chooseNum(min, max)
        chunks <- Helpers.subdivided(req, splits)
      } yield chunks

    def toBytes(lines: List[String]): List[Byte] =
      lines.mkString.getBytes(java.nio.charset.StandardCharsets.US_ASCII).toList
  }

  test("parse single request") {
    PropF.forAllNoShrinkF(Fixtures.genRequest()) { segments =>
      (for {
        read <- Helpers.taking[IO, Byte](segments)
        result <- Parser.Request.parser(Int.MaxValue)(Array.emptyByteArray, read)
        _ <- result._1.body.compile.drain
        _ <- result._2
      } yield true).assert
    }
  }

  test("parse single response") {
    PropF.forAllNoShrinkF(Fixtures.genResponse()) { segments =>
      (for {
        read <- Helpers.taking[IO, Byte](segments)
        result <- Parser.Response.parser(Int.MaxValue)(Array.emptyByteArray, read)
        _ <- result._1.body.compile.drain
        _ <- result._2
      } yield true).assert
    }
  }

  test("parse two requests where bodies are fully read") {
    PropF.forAllNoShrinkF(Fixtures.genRequest(), Fixtures.genRequest()) { (s0, s1) =>
      (for {
        read <- Helpers.taking[IO, Byte](s0 ++ s1)
        result1 <- Parser.Request.parser(Int.MaxValue)(Array.emptyByteArray, read)
        _ <- result1._1.body.compile.drain
        rest1 <- result1._2
        result2 <- Parser.Request.parser(Int.MaxValue)(rest1.get, read)
        _ <- result2._1.body.compile.drain
        _ <- result2._2
      } yield true).assert
    }
  }

  test("parse two responses where bodies are fully read") {
    PropF.forAllNoShrinkF(Fixtures.genResponse(), Fixtures.genResponse()) { (s0, s1) =>
      (for {
        read <- Helpers.taking[IO, Byte](s0 ++ s1)
        result1 <- Parser.Response.parser(Int.MaxValue)(Array.emptyByteArray, read)
        _ <- result1._1.body.compile.drain
        rest1 <- result1._2
        result2 <- Parser.Response.parser(Int.MaxValue)(rest1.get, read)
        _ <- result2._1.body.compile.drain
        _ <- result2._2
      } yield true).assert
    }
  }

  test("raise end of stream for requests") {
    PropF.forAllNoShrinkF(Fixtures.genRequest(min = 3)) { segments =>
      (for {
        read <- Helpers.taking[IO, Byte](segments.dropRight(1))
        result <- Parser.Request.parser(Int.MaxValue)(Array.emptyByteArray, read)
        _ <- result._1.body.compile.drain
        _ <- result._2
      } yield ()).intercept[EmberException.ReachedEndOfStream].void
    }
  }

  // https://github.com/http4s/http4s/issues/6580
  test("raise end of stream for request with EOF after request line") {
    val segments = Fixtures.toBytes(List("POST /foo HTTP/1.1\r\n"))
    (for {
      read <- Helpers.taking[IO, Byte](List(segments))
      result <- Parser.Request.parser(Int.MaxValue)(Array.emptyByteArray, read)
      _ <- result._1.body.compile.drain
      _ <- result._2
    } yield ()).intercept[EmberException.ReachedEndOfStream].void
  }

  test("raise end of stream for response") {
    PropF.forAllNoShrinkF(Fixtures.genResponse(min = 3)) { segments =>
      (for {
        read <- Helpers.taking[IO, Byte](segments.dropRight(1))
        result <- Parser.Response.parser(Int.MaxValue)(Array.emptyByteArray, read)
        _ <- result._1.body.compile.drain
        _ <- result._2
      } yield ()).intercept[EmberException.ReachedEndOfStream].void
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
