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

import org.http4s.syntax.literals._

import java.nio.charset.StandardCharsets
class TraversalSpecItsNotYouItsMe

import org.scalacheck.effect.PropF
import cats.syntax.all._
import cats.effect.{Concurrent, IO}
import cats.effect.std.Queue
import fs2._
import org.http4s._
import org.http4s.laws.discipline.arbitrary._

// FIXME Restore after #3935 is worked out
class TraversalSpec extends Http4sSuite {
  object Helpers {
    def taking[F[_]: Concurrent, A](stream: Stream[F, A]): F[F[Option[Chunk[A]]]] =
      for {
        q <- Queue.unbounded[F, Option[Chunk[A]]]
        _ <- stream.chunks.map(Some(_)).foreach(q.offer(_)).compile.drain
        _ <- q.offer(None)
      } yield q.take

    def assertBodyRoundTrip(req: Request[IO], input: String): IO[Unit] = {
      val parsedBody = for {
        read <- taking[IO, Byte](Encoder.reqToBytes[IO](req))
        end <- Parser.Request
          .parser[IO](Int.MaxValue)(Array.emptyByteArray, read)
        body <- end._1.body.compile.toVector
      } yield body

      val expected = input.getBytes(StandardCharsets.UTF_8).toVector

      parsedBody.assertEquals(expected)
    }
  }

  test("Request Encoder/Parser should preserve existing headers".ignore) {
    PropF.forAllF { (req: Request[IO]) =>
      val res = for {
        read <- Helpers.taking[IO, Byte](Encoder.reqToBytes[IO](req))
        end <- Parser.Request
          .parser[IO](Int.MaxValue)(Array.emptyByteArray, read)
      } yield end._1.headers.headers

      res.assertEquals(req.headers.headers)
    }
  }

  test("Request Encoder/Parser should preserve method with known uri") {
    PropF.forAllF { (req: Request[IO]) =>
      val newReq = req
        .withUri(uri"http://www.google.com")

      val res = for {
        read <- Helpers.taking[IO, Byte](Encoder.reqToBytes[IO](newReq))
        end <- Parser.Request
          .parser[IO](Int.MaxValue)(Array.emptyByteArray, read)
      } yield end._1.method

      res.assertEquals(req.method)
    }
  }

  test("Request Encoder/Parser should preserve uri.scheme".ignore) {
    PropF.forAllF { (req: Request[IO]) =>
      val res = for {
        read <- Helpers.taking[IO, Byte](Encoder.reqToBytes[IO](req))
        end <- Parser.Request
          .parser[IO](Int.MaxValue)(Array.emptyByteArray, read)
      } yield end._1.uri.scheme

      res.assertEquals(req.uri.scheme)
    }
  }

  test("Request Encoder/Parser should preserve body with a known uri") {
    PropF.forAllF { (req: Request[IO], s: String) =>
      val newReq = req
        .withUri(uri"http://www.google.com")
        .withEntity(s)

      Helpers.assertBodyRoundTrip(newReq, s)
    }
  }

  test("Request Encoder/Parser should preserve a UTF-8 BOM body") {
    val s = "\uFEFF"
    val req = Request[IO](Method.POST, uri"http://www.google.com").withEntity(s)
    Helpers.assertBodyRoundTrip(req, s)
  }

}
