/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.ember.core

import org.specs2.mutable.Specification
import cats.effect._
import org.http4s._
import fs2.{Stream, text}
import io.chrisdavenport.log4cats.testing.TestingLogger
import org.specs2.concurrent.ExecutionEnv
import fs2._
import cats.effect.concurrent._
import cats.data.OptionT
import cats.implicits._

class ParsingSpec(implicit ee: ExecutionEnv) extends Specification {
  implicit val cs: ContextShift[IO] = IO.contextShift(ee.ec)

  object Helpers {
    def stripLines(s: String): String = s.replace("\r\n", "\n")
    def httpifyString(s: String): String = s.replace("\n", "\r\n")

    // Only for Use with Text Requests
    def parseRequestRig[F[_]: Sync](s: String): F[Request[F]] = {
      val logger = TestingLogger.impl[F]()
      val byteStream: Stream[F, Byte] = Stream
        .emit(s)
        .covary[F]
        .map(httpifyString)
        .through(fs2.text.utf8Encode[F])

      Parser.Request.parser[F](Int.MaxValue)(byteStream)(logger)
    }

    def parseResponseRig[F[_]: Sync](s: String): Resource[F, Response[F]] = {
      val logger = TestingLogger.impl[F]()
      val byteStream: Stream[F, Byte] = Stream
        .emit(s)
        .covary[F]
        .map(httpifyString)
        .through(fs2.text.utf8Encode[F])

      Parser.Response.parser[F](Int.MaxValue)(byteStream)(logger)
    }

    def forceScopedParsing[F[_]: Sync](s: String): Stream[F, Byte] = {
      val pivotPoint = s.trim().length - 1
      val firstChunk = s.substring(0, pivotPoint).replace("\n", "\r\n")
      val secondChunk = s.substring(pivotPoint, s.length).replace("\n", "\r\n")

      sealed trait StreamState
      case object FirstChunk extends StreamState
      case object SecondChunk extends StreamState
      case object Completed extends StreamState

      def unfoldStream(closed: Ref[F, Boolean]): Stream[F, Byte] = {
        val scope = Resource(((), closed.set(true)).pure[F])
        val noneChunk = OptionT.none[F, (Chunk[Byte], StreamState)].value

        Stream.resource(scope) >>
          Stream.unfoldChunkEval[F, StreamState, Byte](FirstChunk) {
            case FirstChunk =>
              Option((Chunk.array(firstChunk.getBytes()), SecondChunk: StreamState)).pure[F]
            case SecondChunk =>
              closed.get.ifM(
                noneChunk, // simulates stream closing before we've read the entire body
                Option((Chunk.array(secondChunk.getBytes()), Completed: StreamState)).pure[F]
              )
            case Completed => noneChunk
          }
      }

      Stream.eval(Ref.of[F, Boolean](false)) >>= unfoldStream
    }
  }

  "Parser.Request.parser" should {
    "Parse a request with no body correctly" in {
      val raw =
        """GET / HTTP/1.1
      |Host: www.google.com
      |
      |""".stripMargin
      val expected = Request[IO](Method.GET, Uri.unsafeFromString("www.google.com"))

      val result = Helpers.parseRequestRig[IO](raw).unsafeRunSync

      result.method must_=== expected.method
      result.uri.scheme must_=== expected.uri.scheme
      // result.uri.authority must_=== expected.uri.authority
      // result.uri.path must_=== expected.uri.path
      // result.uri.query must_=== expected.uri.query
      result.uri.fragment must_=== expected.uri.fragment
      result.headers must_=== expected.headers
      result.body.compile.toVector.unsafeRunSync must_=== expected.body.compile.toVector.unsafeRunSync
    }

    "Parse a request with a body correctly" in {
      val raw =
        """POST /foo HTTP/1.1
      |Content-Type: text/plain; charset=UTF-8
      |Content-Length: 11
      |
      |Entity Here""".stripMargin
      val expected = Request[IO](Method.POST, Uri.unsafeFromString("/foo"))
        .withEntity("Entity Here")

      val result = Helpers.parseRequestRig[IO](raw).unsafeRunSync

      result.method must_=== expected.method
      result.uri.scheme must_=== expected.uri.scheme
      // result.uri.authority must_=== expected.uri.authority
      // result.uri.path must_=== expected.uri.path
      // result.uri.query must_=== expected.uri.query
      result.uri.fragment must_=== expected.uri.fragment
      result.headers must_=== expected.headers
      result.body.compile.toVector.unsafeRunSync must_=== expected.body.compile.toVector.unsafeRunSync
    }

    "handle a response that requires multiple chunks to be read" in {
      val logger = TestingLogger.impl[IO]()
      val defaultMaxHeaderLength = 4096
      val raw =
        """HTTP/1.1 200 OK
          |content-type: application/json
          |Content-Length: 2
          |
          |{}
          |""".stripMargin

      (for {
        parsed <- Parser.Response
          .parser[IO](defaultMaxHeaderLength)(Helpers.forceScopedParsing[IO](raw))(logger)
          .use { resp =>
            resp.body.through(text.utf8Decode).compile.string
          }
      } yield {
        parsed must_== "{}"
      }).unsafeRunSync()
    }

  }
}
