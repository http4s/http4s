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
package server
package middleware

import cats.effect._
import cats.syntax.all._
import fs2._
import fs2.compression._
import fs2.io.compression._
import org.http4s.headers._
import org.http4s.syntax.all._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.effect.PropF
import scodec.bits.ByteVector

class GZipStrictSuite extends Http4sSuite {
  private val gzipRequest: Request[IO] =
    Request[IO](Method.GET, uri"/").putHeaders(`Accept-Encoding`(ContentCoding.gzip))

  private def serve(
      entity: Entity[IO],
      level: DeflateParams.Level = DeflateParams.Level.DEFAULT,
  ): IO[Response[IO]] =
    GZip(HttpApp[IO](_ => Response[IO](Status.Ok).withEntity(entity).pure[IO]), level = level)
      .run(gzipRequest)

  private def gunzip(resp: Response[IO]): IO[ByteVector] =
    resp.body
      .through(Compression[IO].gunzip())
      .flatMap(_.content)
      .compile
      .to(ByteVector)

  private def assertStrictGzip(resp: Response[IO], expected: ByteVector): IO[Unit] =
    resp.entity match {
      case Entity.Strict(zipped) =>
        IO {
          assertEquals(
            resp.headers.get[`Content-Encoding`],
            Some(`Content-Encoding`(ContentCoding.gzip)),
          )
          assertEquals(resp.headers.get[`Content-Length`].map(_.length), Some(zipped.size))
          assert(!resp.headers.contains[`Transfer-Encoding`])
        } >> gunzip(resp).assertEquals(expected)
      case other => IO(fail(s"Expected a strict entity, got $other"))
    }

  test("compresses a strict entity in one pass and sets Content-Length") {
    val json = ByteVector.view(("""{"id":1,"name":"http4s"}""" * 150).getBytes("UTF-8"))
    serve(Entity.strict(json)).flatMap(assertStrictGzip(_, json))
  }

  test("compresses an empty entity in one pass") {
    serve(Entity.empty[IO]).flatMap(assertStrictGzip(_, ByteVector.empty))
  }

  test("strict entities round-trip at every compression level") {
    val levels = Gen.oneOf(
      DeflateParams.Level.DEFAULT,
      DeflateParams.Level.ZERO,
      DeflateParams.Level.ONE,
      DeflateParams.Level.FIVE,
      DeflateParams.Level.NINE,
    )
    PropF.forAllF(Gen.listOf(arbitrary[Byte]), levels) {
      (bytes: List[Byte], level: DeflateParams.Level) =>
        val body = ByteVector(bytes)
        serve(Entity.strict(body), level).flatMap(assertStrictGzip(_, body))
    }
  }

  test("a large strict entity stored without compression round-trips") {
    val large = ByteVector.view(Array.tabulate[Byte](200000)(i => (i * 2654435761L >>> 13).toByte))
    serve(Entity.strict(large), DeflateParams.Level.ZERO).flatMap(assertStrictGzip(_, large))
  }

  test("keeps streaming a streamed entity without Content-Length") {
    val chunks = List("hello ", "streamed ", "world")
    val body = Stream.emits(chunks).covary[IO].through(text.utf8.encode)
    serve(Entity.stream(body)).flatMap { resp =>
      IO {
        assertEquals(
          resp.headers.get[`Content-Encoding`],
          Some(`Content-Encoding`(ContentCoding.gzip)),
        )
        assertEquals(resp.headers.get[`Content-Length`], None)
        resp.entity match {
          case Entity.Streamed(_, _) => ()
          case other => fail(s"Expected a streamed entity, got $other")
        }
      } >> gunzip(resp).map(_.decodeUtf8Lenient).assertEquals(chunks.mkString)
    }
  }

  test("leaves a strict entity untouched when the client does not accept gzip") {
    val body = ByteVector.view("plain".getBytes("UTF-8"))
    GZip(HttpApp[IO](_ => Response[IO](Status.Ok).withEntity(Entity.strict(body)).pure[IO]))
      .run(Request[IO](Method.GET, uri"/"))
      .map { resp =>
        assertEquals(resp.headers.get[`Content-Encoding`], None)
        assertEquals(resp.entity, Entity.strict(body))
      }
  }
}
