/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.ember.core

import org.specs2.mutable.Specification
import cats.effect._
import org.http4s._
import scodec.bits.ByteVector
import io.chrisdavenport.log4cats.testing.TestingLogger
import org.specs2.concurrent.ExecutionEnv
import fs2._
import cats.effect.concurrent._
import cats.data.OptionT
import cats.implicits._
import fs2.Chunk.ByteVectorChunk
import org.http4s.headers.Expires

class ParsingSpec(implicit ee: ExecutionEnv) extends Specification {
  implicit val cs: ContextShift[IO] = IO.contextShift(ee.ec)

  object Helpers {
    def stripLines(s: String): String = s.replace("\r\n", "\n")
    def httpifyString(s: String): String = s.replace("\n", "\r\n")

    // Only for Use with Text Requests
    def parseRequestRig[F[_]: Concurrent](s: String): F[Request[F]] = {
      val logger = TestingLogger.impl[F]()
      val byteStream: Stream[F, Byte] = Stream
        .emit(s)
        .covary[F]
        .map(httpifyString)
        .through(fs2.text.utf8Encode[F])

      Parser.Request.parser[F](Int.MaxValue)(byteStream)(logger)
    }

    def parseResponseRig[F[_]: Concurrent](s: String): Resource[F, Response[F]] = {
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

      val result = Helpers.parseRequestRig[IO](raw).unsafeRunSync()

      result.method must_=== expected.method
      result.uri.scheme must_=== expected.uri.scheme
      // result.uri.authority must_=== expected.uri.authority
      // result.uri.path must_=== expected.uri.path
      // result.uri.query must_=== expected.uri.query
      result.uri.fragment must_=== expected.uri.fragment
      result.headers must_=== expected.headers
      result.body.compile.toVector.unsafeRunSync() must_=== expected.body.compile.toVector
        .unsafeRunSync()
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

      val result = Helpers.parseRequestRig[IO](raw).unsafeRunSync()

      result.method must_=== expected.method
      result.uri.scheme must_=== expected.uri.scheme
      // result.uri.authority must_=== expected.uri.authority
      // result.uri.path must_=== expected.uri.path
      // result.uri.query must_=== expected.uri.query
      result.uri.fragment must_=== expected.uri.fragment
      result.headers must_=== expected.headers
      result.body.compile.toVector.unsafeRunSync() must_=== expected.body.compile.toVector
        .unsafeRunSync()
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
        parsed <-
          Parser.Response
            .parser[IO](defaultMaxHeaderLength)(Helpers.forceScopedParsing[IO](raw))(logger)
            .use { resp =>
              resp.body.through(text.utf8Decode).compile.string
            }
      } yield parsed must_== "{}").unsafeRunSync()
    }

  }

  "Parser.Response.parser" should {
    "handle a chunked response" in {
      val logger = TestingLogger.impl[IO]()
      val defaultMaxHeaderLength = 4096
      val base =
        "SFRUUC8xLjEgMjAwIE9LDQpBcGktVmVyc2lvbjogMS40MA0KQ29udGVudC1UeXBlOiBhcHBsaWNhdGlvbi9qc29uDQpEb2NrZXItRXhwZXJpbWVudGFsOiBmYWxzZQ0KT3N0eXBlOiBsaW51eA0KU2VydmVyOiBEb2NrZXIvMTkuMDMuMTEtY2UgKGxpbnV4KQ0KRGF0ZTogRnJpLCAyNiBKdW4gMjAyMCAyMjozNTo0MiBHTVQNClRyYW5zZmVyLUVuY29kaW5nOiBjaHVua2VkDQoNCjhjMw0KeyJJRCI6IllNS0U6MkZZMzpTUUc3OjZSSFo6TFlTVDpRUk9JOkU1NEU6UTdXRjpERElLOlNOSUE6Rk5UTzpJVllSIiwiQ29udGFpbmVycyI6MjUsIkNvbnRhaW5lcnNSdW5uaW5nIjowLCJDb250YWluZXJzUGF1c2VkIjowLCJDb250YWluZXJzU3RvcHBlZCI6MjUsIkltYWdlcyI6ODMsIkRyaXZlciI6Im92ZXJsYXkyIiwiRHJpdmVyU3RhdHVzIjpbWyJCYWNraW5nIEZpbGVzeXN0ZW0iLCJleHRmcyJdLFsiU3VwcG9ydHMgZF90eXBlIiwidHJ1ZSJdLFsiTmF0aXZlIE92ZXJsYXkgRGlmZiIsImZhbHNlIl1dLCJTeXN0ZW1TdGF0dXMiOm51bGwsIlBsdWdpbnMiOnsiVm9sdW1lIjpbImxvY2FsIl0sIk5ldHdvcmsiOlsiYnJpZGdlIiwiaG9zdCIsImlwdmxhbiIsIm1hY3ZsYW4iLCJudWxsIiwib3ZlcmxheSJdLCJBdXRob3JpemF0aW9uIjpudWxsLCJMb2ciOlsiYXdzbG9ncyIsImZsdWVudGQiLCJnY3Bsb2dzIiwiZ2VsZiIsImpvdXJuYWxkIiwianNvbi1maWxlIiwibG9jYWwiLCJsb2dlbnRyaWVzIiwic3BsdW5rIiwic3lzbG9nIl19LCJNZW1vcnlMaW1pdCI6dHJ1ZSwiU3dhcExpbWl0Ijp0cnVlLCJLZXJuZWxNZW1vcnkiOnRydWUsIktlcm5lbE1lbW9yeVRDUCI6dHJ1ZSwiQ3B1Q2ZzUGVyaW9kIjp0cnVlLCJDcHVDZnNRdW90YSI6dHJ1ZSwiQ1BVU2hhcmVzIjp0cnVlLCJDUFVTZXQiOnRydWUsIlBpZHNMaW1pdCI6dHJ1ZSwiSVB2NEZvcndhcmRpbmciOnRydWUsIkJyaWRnZU5mSXB0YWJsZXMiOnRydWUsIkJyaWRnZU5mSXA2dGFibGVzIjp0cnVlLCJEZWJ1ZyI6ZmFsc2UsIk5GZCI6MjQsIk9vbUtpbGxEaXNhYmxlIjp0cnVlLCJOR29yb3V0aW5lcyI6NDAsIlN5c3RlbVRpbWUiOiIyMDIwLTA2LTI2VDE1OjM1OjQyLjU1MjUzMzQzMS0wNzowMCIsIkxvZ2dpbmdEcml2ZXIiOiJqc29uLWZpbGUiLCJDZ3JvdXBEcml2ZXIiOiJjZ3JvdXBmcyIsIk5FdmVudHNMaXN0ZW5lciI6MCwiS2VybmVsVmVyc2lvbiI6IjUuNy42LWFyY2gxLTEiLCJPcGVyYXRpbmdTeXN0ZW0iOiJBcmNoIExpbnV4IiwiT1NUeXBlIjoibGludXgiLCJBcmNoaXRlY3R1cmUiOiJ4ODZfNjQiLCJJbmRleFNlcnZlckFkZHJlc3MiOiJodHRwczovL2luZGV4LmRvY2tlci5pby92MS8iLCJSZWdpc3RyeUNvbmZpZyI6eyJBbGxvd05vbmRpc3RyaWJ1dGFibGVBcnRpZmFjdHNDSURScyI6W10sIkFsbG93Tm9uZGlzdHJpYnV0YWJsZUFydGlmYWN0c0hvc3RuYW1lcyI6W10sIkluc2VjdXJlUmVnaXN0cnlDSURScyI6WyIxMjcuMC4wLjAvOCJdLCJJbmRleENvbmZpZ3MiOnsiZG9ja2VyLmlvIjp7Ik5hbWUiOiJkb2NrZXIuaW8iLCJNaXJyb3JzIjpbXSwiU2VjdXJlIjp0cnVlLCJPZmZpY2lhbCI6dHJ1ZX19LCJNaXJyb3JzIjpbXX0sIk5DUFUiOjQsIk1lbVRvdGFsIjo4MjIwOTgzMjk2LCJHZW5lcmljUmVzb3VyY2VzIjpudWxsLCJEb2NrZXJSb290RGlyIjoiL3Zhci9saWIvZG9ja2VyIiwiSHR0cFByb3h5IjoiIiwiSHR0cHNQcm94eSI6IiIsIk5vUHJveHkiOiIiLCJOYW1lIjoiZGF2ZW5wb3J0LWxhcHRvcCIsIkxhYmVscyI6W10sIkV4cGVyaW1lbnRhbEJ1aWxkIjpmYWxzZSwiU2VydmVyVmVyc2lvbiI6IjE5LjAzLjExLWNlIiwiQ2x1c3RlclN0b3JlIjoiIiwiQ2x1c3RlckFkdmVydGlzZSI6IiIsIlJ1bnRpbWVzIjp7InJ1bmMiOnsicGF0aCI6InJ1bmMifX0sIkRlZmF1bHRSdW50aW1lIjoicnVuYyIsIlN3YXJtIjp7Ik5vZGVJRCI6IiIsIk5vZGVBZGRyIjoiIiwiTG9jYWxOb2RlU3RhdGUiOiJpbmFjdGl2ZSIsIkNvbnRyb2xBdmFpbGFibGUiOmZhbHNlLCJFcnJvciI6IiIsIlJlbW90ZU1hbmFnZXJzIjpudWxsfSwiTGl2ZVJlc3RvcmVFbmFibGVkIjpmYWxzZSwiSXNvbGF0aW9uIjoiIiwiSW5pdEJpbmFyeSI6ImRvY2tlci1pbml0IiwiQ29udGFpbmVyZENvbW1pdCI6eyJJRCI6ImQ3NmMxMjFmNzZhNWZjOGE0NjJkYzY0NTk0YWVhNzJmZTE4ZTExNzgubSIsIkV4cGVjdGVkIjoiZDc2YzEyMWY3NmE1ZmM4YTQ2MmRjNjQ1OTRhZWE3MmZlMThlMTE3OC5tIn0sIlJ1bmNDb21taXQiOnsiSUQiOiJkYzkyMDhhMzMwM2ZlZWY1YjM4MzlmNDMyM2Q5YmViMzZkZjBhOWRkIiwiRXhwZWN0ZWQiOiJkYzkyMDhhMzMwM2ZlZWY1YjM4MzlmNDMyM2Q5YmViMzZkZjBhOWRkIn0sIkluaXRDb21taXQiOnsiSUQiOiJmZWMzNjgzIiwiRXhwZWN0ZWQiOiJmZWMzNjgzIn0sIlNlY3VyaXR5T3B0aW9ucyI6WyJuYW1lPXNlY2NvbXAscHJvZmlsZT1kZWZhdWx0Il0sIldhcm5pbmdzIjpudWxsfQoNCjANCg0K"
      val baseBv = ByteVector.fromBase64(base).get

      Parser.Response
        .parser[IO](defaultMaxHeaderLength)(Stream.chunk(ByteVectorChunk(baseBv)))(logger)
        .use { resp =>
          resp.body.through(text.utf8Decode).compile.string

        }
        .map {

          _.size must beGreaterThan(0)
        }
        .unsafeRunSync()
    }

    "parse a chunked simple" in {
      val logger = TestingLogger.impl[IO]()
      val defaultMaxHeaderLength = 4096
      val respS =
        Stream(
          "HTTP/1.1 200 OK\r\n",
          "Content-Type: text/plain\r\n",
          "Transfer-Encoding: chunked\r\n\r\n",
          // "Trailer: Expires\r\n\r\n",
          "7\r\n",
          "Mozilla\r\n",
          "9\r\n",
          "Developer\r\n",
          "7\r\n",
          "Network\r\n",
          "0\r\n",
          "\r\n"
          // "Expires: Wed, 21 Oct 2015 07:28:00 GMT\r\n\r\n"
        )
      val byteStream: Stream[IO, Byte] = respS
        .flatMap(s =>
          Stream.chunk(Chunk.array(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII))))

      Parser.Response
        .parser[IO](defaultMaxHeaderLength)(byteStream)(logger)
        .use { resp =>
          resp.body.through(text.utf8Decode).compile.string.map { body =>
            body must beEqualTo("MozillaDeveloperNetwork")
          }
        }
        .unsafeRunSync()
    }

    "parse a chunked with trailer headers" in {
      val logger = TestingLogger.impl[IO]()
      val defaultMaxHeaderLength = 4096
      val respS =
        Stream(
          "HTTP/1.1 200 OK\r\n",
          "Content-Type: text/plain\r\n",
          "Transfer-Encoding: chunked\r\n",
          "Trailer: Expires\r\n\r\n",
          "7\r\n",
          "Mozilla\r\n",
          "9\r\n",
          "Developer\r\n",
          "7\r\n",
          "Network\r\n",
          "0\r\n",
          "Expires: Wed, 21 Oct 2015 07:28:00 GMT\r\n",
          "\r\n"
        )
      val byteStream: Stream[IO, Byte] = respS
        .flatMap(s =>
          Stream.chunk(Chunk.array(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII))))

      Parser.Response
        .parser[IO](defaultMaxHeaderLength)(byteStream)(logger)
        .use { resp =>
          for {
            body <- resp.body.through(text.utf8Decode).compile.string
            trailers <- resp.trailerHeaders
          } yield (body must beEqualTo("MozillaDeveloperNetwork")).and(
            trailers.get(Expires) must beSome
          )
        }
        .unsafeRunSync()
    }
  }

}
