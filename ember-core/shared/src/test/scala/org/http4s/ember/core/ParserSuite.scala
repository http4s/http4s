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

import cats.data.OptionT
import cats.effect._
import cats.effect.std.Queue
import cats.syntax.all._
import fs2._
import org.http4s._
import org.http4s.headers.Expires
import org.http4s.implicits._
import org.typelevel.ci._
import scodec.bits.ByteVector

class ParsingSuite extends Http4sSuite {
  object Helpers {
    def stripLines(s: String): String = s.replace("\r\n", "\n")
    def httpifyString(s: String): String = s.replace("\n", "\r\n")

    def taking[F[_]: Concurrent, A](stream: Stream[F, A]): F[F[Option[Chunk[A]]]] =
      for {
        q <- Queue.unbounded[F, Option[Chunk[A]]]
        _ <- stream.chunks.map(Some(_)).foreach(q.offer(_)).compile.drain
        _ <- q.offer(None)
      } yield q.take

    // Only for Use with Text Requests
    def parseRequestRig[F[_]: Concurrent](s: String): F[Request[F]] = {
      val byteStream: Stream[F, Byte] = Stream
        .emit(s)
        .map(httpifyString)
        .through(fs2.text.utf8.encode[F])
        .rechunkRandomly(0.1, 0.5)

      taking(byteStream).flatMap { read =>
        Parser.Request.parser[F](Int.MaxValue)(Array.emptyByteArray, read).map(_._1)
      }
    }

    def parseResponseRig[F[_]: Concurrent](s: String): Resource[F, Response[F]] = {
      val byteStream: Stream[F, Byte] = Stream
        .emit(s)
        .map(httpifyString)
        .through(fs2.text.utf8.encode[F])
        .rechunkRandomly(0.1, 0.5)

      Resource.eval(
        taking(byteStream).flatMap { read =>
          Parser.Response.parser[F](Int.MaxValue)(Array.emptyByteArray, read).map(_._1)
        }
      )
    }

    def forceScopedParsing[F[_]: Concurrent](s: String): Stream[F, Byte] = {
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
                Option((Chunk.array(secondChunk.getBytes()), Completed: StreamState)).pure[F],
              )
            case Completed => noneChunk
          }
      }

      Stream.eval(Ref.of[F, Boolean](false)) >>= unfoldStream
    }
  }

  test("Parser.Request.parser should Parse a request with no body correctly") {
    val raw =
      """GET / HTTP/1.1
      |Host: www.google.com
      |
      |""".stripMargin
    val expected = Request[IO](
      Method.GET,
      uri"www.google.com",
      headers = Headers(org.http4s.headers.Host("www.google.com")),
    )

    val result = Helpers.parseRequestRig[IO](raw)

    result.map(_.method).assertEquals(expected.method)
    result.map(_.uri.scheme).assertEquals(expected.uri.scheme)
    // result.map(_.uri.authority).assertEquals(expected.uri.authority)
    // result.map(_.uri.path).assertEquals(expected.uri.path)
    // result.map(_.uri.query).assertEquals(expected.uri.query)
    result.map(_.uri.fragment).assertEquals(expected.uri.fragment)
    result.map(_.headers).assertEquals(expected.headers)
    for {
      r <- result
      a <- r.body.compile.toVector
      b <- expected.body.compile.toVector
    } yield assertEquals(a, b)
  }

  test("Parser.Request.parser should Parse a request with a body correctly") {
    val raw =
      """POST /foo HTTP/1.1
      |Content-Length: 11
      |Content-Type: text/plain; charset=UTF-8
      |
      |Entity Here""".stripMargin
    val expected = Request[IO](Method.POST, uri"/foo")
      .withEntity("Entity Here")

    val result = Helpers.parseRequestRig[IO](raw)

    for {
      _ <- result.map(_.method).assertEquals(expected.method)
      _ <- result.map(_.uri.scheme).assertEquals(expected.uri.scheme)
      // result.map(_.uri.authority).assertEquals(expected.uri.authority)
      // result.map(_.uri.path).assertEquals(expected.uri.path)
      // result.map(_.uri.query).assertEquals(expected.uri.query)
      _ <- result.map(_.uri.fragment).assertEquals(expected.uri.fragment)
      _ <- result.map(_.headers).assertEquals(expected.headers)
      r <- result
      a <- r.body.through(fs2.text.utf8.decode).compile.string
      b <- expected.body.through(fs2.text.utf8.decode).compile.string
    } yield assertEquals(a, b)
  }

  test("Parser.Request.parser should Parse a simple request") {
    val raw =
      """GET /foo HTTP/1.1
        |Host: localhost:8080
        |User-Agent: curl/7.64.1
        |Accept: */*
        |
        |""".stripMargin
    val expected = Request[IO](Method.GET, uri"/foo")

    val result = Helpers.parseRequestRig[IO](raw)

    result.map(_.method).assertEquals(expected.method)
    result.map(_.uri.scheme).assertEquals(expected.uri.scheme)
  }

  test("Parser.Request.parser should handle a response that requires multiple chunks to be read") {
    val defaultMaxHeaderLength = 4096
    val raw1 =
      """HTTP/1.1 200 OK
          |Content-type: application/json
          |Content-Length: 2
          |
          |{""".stripMargin

    val raw2 = """}
          |""".stripMargin

    val http1 = Helpers.httpifyString(raw1)

    val http2 = Helpers.httpifyString(raw2)
    val encoded = (Stream(http1) ++ Stream(http2)).through(fs2.text.utf8.encode)

    (for {
      take <- Helpers.taking[IO, Byte](encoded)
      parsed <-
        Parser.Response
          .parser[IO](defaultMaxHeaderLength)(
            Array.emptyByteArray,
            take,
            // Helpers.forceScopedParsing[IO](raw) // Cuts off `}` in current test. Why?
            // I don't follow what the rig is testing vs this.
          ) // (logger)
          .flatMap { case (resp, _) =>
            resp.body.through(text.utf8.decode).compile.string
          }
      _ <- IO.println(s"Parsed: $parsed")
    } yield parsed == "{}").assert
  }

  test(
    "Parser.Request.parser should parse a request with Content-Length and return the rest of the stream"
  ) {
    val raw =
      """POST /foo HTTP/1.1
          |Host: localhost:8080
          |User-Agent: curl/7.64.1
          |Accept: */*
          |Content-Length: 5
          |
          |helloeverything after the body""".stripMargin

    val byteStream = Stream
      .emit(raw)
      .map(Helpers.httpifyString)
      .through(text.utf8.encode)

    for {
      take <- Helpers.taking[IO, Byte](byteStream)
      result <- Parser.Request.parser[IO](Int.MaxValue)(Array.emptyByteArray, take)
      body <- result._1.body.through(text.utf8.decode).compile.string
      rest <- Stream
        .eval(result._2)
        .flatMap(chunk => Stream.chunk(Chunk.byteVector(ByteVector(chunk.get))))
        .through(text.utf8.decode)
        .compile
        .string
    } yield {
      assertEquals(body, "hello")
      assertEquals(rest, "everything after the body")
    }
  }

  test("Parser.Request.parser should parse two requests in a row") {
    val reqS =
      Stream(
        "GET /foo HTTP/1.1\r\n",
        "Accept: text/plain\r\n\r\nGET /foo HTTP/1.1\r\n",
        "Accept: text/plain\r\n\r\n",
      )
    val byteStream: Stream[IO, Byte] = reqS
      .flatMap(s =>
        Stream.chunk(Chunk.array(s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)))
      )

    (for {
      take <- Helpers.taking[IO, Byte](byteStream)
      req1 <- Parser.Request.parser[IO](Int.MaxValue)(Array.emptyByteArray, take)
      drained <- req1._2
      req2 <- Parser.Request.parser[IO](Int.MaxValue)(drained.get, take)
    } yield req1._1.method == Method.GET && req2._1.method == Method.GET).assert
  }

  test(" should handle a chunked response") {
    val defaultMaxHeaderLength = 4096
    val base =
      "SFRUUC8xLjEgMjAwIE9LDQpBcGktVmVyc2lvbjogMS40MA0KQ29udGVudC1UeXBlOiBhcHBsaWNhdGlvbi9qc29uDQpEb2NrZXItRXhwZXJpbWVudGFsOiBmYWxzZQ0KT3N0eXBlOiBsaW51eA0KU2VydmVyOiBEb2NrZXIvMTkuMDMuMTEtY2UgKGxpbnV4KQ0KRGF0ZTogRnJpLCAyNiBKdW4gMjAyMCAyMjozNTo0MiBHTVQNClRyYW5zZmVyLUVuY29kaW5nOiBjaHVua2VkDQoNCjhjMw0KeyJJRCI6IllNS0U6MkZZMzpTUUc3OjZSSFo6TFlTVDpRUk9JOkU1NEU6UTdXRjpERElLOlNOSUE6Rk5UTzpJVllSIiwiQ29udGFpbmVycyI6MjUsIkNvbnRhaW5lcnNSdW5uaW5nIjowLCJDb250YWluZXJzUGF1c2VkIjowLCJDb250YWluZXJzU3RvcHBlZCI6MjUsIkltYWdlcyI6ODMsIkRyaXZlciI6Im92ZXJsYXkyIiwiRHJpdmVyU3RhdHVzIjpbWyJCYWNraW5nIEZpbGVzeXN0ZW0iLCJleHRmcyJdLFsiU3VwcG9ydHMgZF90eXBlIiwidHJ1ZSJdLFsiTmF0aXZlIE92ZXJsYXkgRGlmZiIsImZhbHNlIl1dLCJTeXN0ZW1TdGF0dXMiOm51bGwsIlBsdWdpbnMiOnsiVm9sdW1lIjpbImxvY2FsIl0sIk5ldHdvcmsiOlsiYnJpZGdlIiwiaG9zdCIsImlwdmxhbiIsIm1hY3ZsYW4iLCJudWxsIiwib3ZlcmxheSJdLCJBdXRob3JpemF0aW9uIjpudWxsLCJMb2ciOlsiYXdzbG9ncyIsImZsdWVudGQiLCJnY3Bsb2dzIiwiZ2VsZiIsImpvdXJuYWxkIiwianNvbi1maWxlIiwibG9jYWwiLCJsb2dlbnRyaWVzIiwic3BsdW5rIiwic3lzbG9nIl19LCJNZW1vcnlMaW1pdCI6dHJ1ZSwiU3dhcExpbWl0Ijp0cnVlLCJLZXJuZWxNZW1vcnkiOnRydWUsIktlcm5lbE1lbW9yeVRDUCI6dHJ1ZSwiQ3B1Q2ZzUGVyaW9kIjp0cnVlLCJDcHVDZnNRdW90YSI6dHJ1ZSwiQ1BVU2hhcmVzIjp0cnVlLCJDUFVTZXQiOnRydWUsIlBpZHNMaW1pdCI6dHJ1ZSwiSVB2NEZvcndhcmRpbmciOnRydWUsIkJyaWRnZU5mSXB0YWJsZXMiOnRydWUsIkJyaWRnZU5mSXA2dGFibGVzIjp0cnVlLCJEZWJ1ZyI6ZmFsc2UsIk5GZCI6MjQsIk9vbUtpbGxEaXNhYmxlIjp0cnVlLCJOR29yb3V0aW5lcyI6NDAsIlN5c3RlbVRpbWUiOiIyMDIwLTA2LTI2VDE1OjM1OjQyLjU1MjUzMzQzMS0wNzowMCIsIkxvZ2dpbmdEcml2ZXIiOiJqc29uLWZpbGUiLCJDZ3JvdXBEcml2ZXIiOiJjZ3JvdXBmcyIsIk5FdmVudHNMaXN0ZW5lciI6MCwiS2VybmVsVmVyc2lvbiI6IjUuNy42LWFyY2gxLTEiLCJPcGVyYXRpbmdTeXN0ZW0iOiJBcmNoIExpbnV4IiwiT1NUeXBlIjoibGludXgiLCJBcmNoaXRlY3R1cmUiOiJ4ODZfNjQiLCJJbmRleFNlcnZlckFkZHJlc3MiOiJodHRwczovL2luZGV4LmRvY2tlci5pby92MS8iLCJSZWdpc3RyeUNvbmZpZyI6eyJBbGxvd05vbmRpc3RyaWJ1dGFibGVBcnRpZmFjdHNDSURScyI6W10sIkFsbG93Tm9uZGlzdHJpYnV0YWJsZUFydGlmYWN0c0hvc3RuYW1lcyI6W10sIkluc2VjdXJlUmVnaXN0cnlDSURScyI6WyIxMjcuMC4wLjAvOCJdLCJJbmRleENvbmZpZ3MiOnsiZG9ja2VyLmlvIjp7Ik5hbWUiOiJkb2NrZXIuaW8iLCJNaXJyb3JzIjpbXSwiU2VjdXJlIjp0cnVlLCJPZmZpY2lhbCI6dHJ1ZX19LCJNaXJyb3JzIjpbXX0sIk5DUFUiOjQsIk1lbVRvdGFsIjo4MjIwOTgzMjk2LCJHZW5lcmljUmVzb3VyY2VzIjpudWxsLCJEb2NrZXJSb290RGlyIjoiL3Zhci9saWIvZG9ja2VyIiwiSHR0cFByb3h5IjoiIiwiSHR0cHNQcm94eSI6IiIsIk5vUHJveHkiOiIiLCJOYW1lIjoiZGF2ZW5wb3J0LWxhcHRvcCIsIkxhYmVscyI6W10sIkV4cGVyaW1lbnRhbEJ1aWxkIjpmYWxzZSwiU2VydmVyVmVyc2lvbiI6IjE5LjAzLjExLWNlIiwiQ2x1c3RlclN0b3JlIjoiIiwiQ2x1c3RlckFkdmVydGlzZSI6IiIsIlJ1bnRpbWVzIjp7InJ1bmMiOnsicGF0aCI6InJ1bmMifX0sIkRlZmF1bHRSdW50aW1lIjoicnVuYyIsIlN3YXJtIjp7Ik5vZGVJRCI6IiIsIk5vZGVBZGRyIjoiIiwiTG9jYWxOb2RlU3RhdGUiOiJpbmFjdGl2ZSIsIkNvbnRyb2xBdmFpbGFibGUiOmZhbHNlLCJFcnJvciI6IiIsIlJlbW90ZU1hbmFnZXJzIjpudWxsfSwiTGl2ZVJlc3RvcmVFbmFibGVkIjpmYWxzZSwiSXNvbGF0aW9uIjoiIiwiSW5pdEJpbmFyeSI6ImRvY2tlci1pbml0IiwiQ29udGFpbmVyZENvbW1pdCI6eyJJRCI6ImQ3NmMxMjFmNzZhNWZjOGE0NjJkYzY0NTk0YWVhNzJmZTE4ZTExNzgubSIsIkV4cGVjdGVkIjoiZDc2YzEyMWY3NmE1ZmM4YTQ2MmRjNjQ1OTRhZWE3MmZlMThlMTE3OC5tIn0sIlJ1bmNDb21taXQiOnsiSUQiOiJkYzkyMDhhMzMwM2ZlZWY1YjM4MzlmNDMyM2Q5YmViMzZkZjBhOWRkIiwiRXhwZWN0ZWQiOiJkYzkyMDhhMzMwM2ZlZWY1YjM4MzlmNDMyM2Q5YmViMzZkZjBhOWRkIn0sIkluaXRDb21taXQiOnsiSUQiOiJmZWMzNjgzIiwiRXhwZWN0ZWQiOiJmZWMzNjgzIn0sIlNlY3VyaXR5T3B0aW9ucyI6WyJuYW1lPXNlY2NvbXAscHJvZmlsZT1kZWZhdWx0Il0sIldhcm5pbmdzIjpudWxsfQoNCjANCg0K"
    val baseBv = ByteVector.fromBase64(base).get

    (for {
      take <- Helpers.taking[IO, Byte](Stream.chunk(Chunk.byteVector(baseBv)))
      result <- Parser.Response
        .parser[IO](defaultMaxHeaderLength)(Array.emptyByteArray, take)
      body <- result._1.body.through(text.utf8.decode).compile.string
    } yield body.size > 0).assert
  }

  test(" should parse a chunked simple") {
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
        "\r\n",
        // "Expires: Wed, 21 Oct 2015 07:28:00 GMT\r\n\r\n"
      )
    val byteStream: Stream[IO, Byte] = respS
      .flatMap(s =>
        Stream.chunk(Chunk.array(s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)))
      )

    (for {
      take <- Helpers.taking[IO, Byte](byteStream)
      resp <- Parser.Response
        .parser[IO](defaultMaxHeaderLength)(Array.emptyByteArray, take)
      body <- resp._1.body.through(text.utf8.decode).compile.string
    } yield body == "MozillaDeveloperNetwork").assert
  }

  test(" should parse a chunked with trailer headers") {
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
        "Expires: Wed, 21 Oct 2015 07:28:00 GMT\r\n\r\n",
        // "\r\n"
      )

    val byteStream: Stream[IO, Byte] = respS
      .flatMap(s =>
        Stream.chunk(Chunk.array(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
      )

    (for {
      take <- Helpers.taking[IO, Byte](byteStream)
      result <- Parser.Response
        .parser[IO](defaultMaxHeaderLength)(Array.emptyByteArray, take)
      body <- result._1.body.through(text.utf8.decode).compile.string
      trailers <- result._1.trailerHeaders
    } yield body == "MozillaDeveloperNetwork" && trailers.get[Expires].isDefined).assert
  }

  test(
    "Parser.Response.parser should parse a response with Content-Length and return the rest of the stream"
  ) {
    val defaultMaxHeaderLength = 4096
    val raw =
      """HTTP/1.1 200 OK
        |Content-Length: 5
        |
        |helloeverything after the body""".stripMargin

    val byteStream = Stream
      .emit(raw)
      .map(Helpers.httpifyString)
      .through(text.utf8.encode)

    for {
      take <- Helpers.taking[IO, Byte](byteStream)
      result <- Parser.Response
        .parser[IO](defaultMaxHeaderLength)(Array.emptyByteArray, take)
      body <- result._1.body.through(text.utf8.decode).compile.string
      rest <- Stream
        .eval(result._2)
        .flatMap(chunk => Stream.chunk(Chunk.byteVector(ByteVector(chunk.get))))
        .through(text.utf8.decode)
        .compile
        .string
    } yield {
      assertEquals(body, "hello")
      assertEquals(rest, "everything after the body")
    }
  }

  test(
    "Parser.Response.parser return everything if the body is discarded"
  ) {
    val defaultMaxHeaderLength = 4096
    val raw =
      """HTTP/1.1 200 OK
        |Content-Length: 5
        |
        |helloeverything after the body""".stripMargin

    val byteStream = Stream
      .emit(raw)
      .map(Helpers.httpifyString)
      .through(text.utf8.encode)

    for {
      take <- Helpers.taking[IO, Byte](byteStream)
      result <- Parser.Response
        .parser2[IO](defaultMaxHeaderLength)(Array.emptyByteArray, take, true)
      body <- result._1.body.through(text.utf8.decode).compile.string
      rest <- Stream
        .eval(result._2)
        .flatMap(chunk => Stream.chunk(Chunk.byteVector(ByteVector(chunk.get))))
        .through(text.utf8.decode)
        .compile
        .string
    } yield {
      assertEquals(body, "")
      assertEquals(rest, "helloeverything after the body")
    }
  }

  test("Parser.Response.parser should Parse a response without Content-Length") {
    val defaultMaxHeaderLength = 4096
    val raw =
      """HTTP/1.0 200 OK
      |Content-Type: text/plain
      |
      |helloallthethings""".stripMargin

    val byteStream = Stream
      .emit(raw)
      .map(Helpers.httpifyString)
      .through(text.utf8.encode)

    for {
      take <- Helpers.taking[IO, Byte](byteStream)
      result <- Parser.Response
        .parser[IO](defaultMaxHeaderLength)(Array.emptyByteArray, take)
      body <- result._1.body.through(text.utf8.decode).compile.string
    } yield assertEquals(body, "helloallthethings")
  }

  test("Header Parser should handle headers in a section") {
    val base = """Content-Type: text/plain; charset=UTF-8
      |Content-Length: 11
      |
      |""".stripMargin
    val asHttp = Helpers.httpifyString(base)
    val bv = asHttp.getBytes()

    Parser.HeaderP.parse[IO](bv, 4096, Parser.HeaderP.ParserState.initial).map {
      case Right(headerP) =>
        assertEquals(
          headerP.headers.headers,
          List(
            Header.Raw(ci"Content-Type", "text/plain; charset=UTF-8"),
            Header.Raw(ci"Content-Length", "11"),
          ),
        )
        assert(!headerP.chunked)
        assertEquals(headerP.contentLength, Some(11L))
      case Left(_) => fail("Headers were not right")
    }
  }

  test("Message Parser should Handle weird chunking") {
    val defaultMaxHeaderLength = 4096
    val respS =
      Stream(
        "POST / HTTP/1.1\r\n",
        "Content-Type: text/plain\r\n",
        "Transfer-Encoding: chunked\r\n",
        "Trailer: Expires\r\n",
        "\r\n",
      )
    val byteStream: Stream[IO, Byte] = respS
      .flatMap(s =>
        Stream.chunk(Chunk.array(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
      )

    val result = for {
      take <- Helpers.taking[IO, Byte](byteStream)
      message <- Parser.Request
        .parser(defaultMaxHeaderLength)(Array.emptyByteArray, take)
    } yield message

    result
      .map { case (req, _) =>
        assertEquals(req.method, Method.POST)
        assertEquals(
          req.headers,
          Headers(
            "Content-Type" -> "text/plain",
            "Transfer-Encoding" -> "chunked",
            "Trailer" -> "Expires",
          ),
        )

      }
  }

  test("Request Prelude should parse an expected value") {
    val raw =
      """GET / HTTP/1.1
          |""".stripMargin
    val asHttp = Helpers.httpifyString(raw)
    val bv = asHttp.getBytes()

    Parser.Request.ReqPrelude
      .parse[IO](bv, 4096, Parser.Request.ReqPrelude.ParserState.initial)
      .map {
        case Right(prelude) =>
          assertEquals(prelude.method, Method.GET)
          assertEquals(prelude.uri, uri"/")
          assertEquals(prelude.version, HttpVersion.`HTTP/1.1`)
        case Left(_) => fail("Prelude was not right")
      }
  }

  test("Response Prelude should parse an expected value") {
    val raw =
      """HTTP/1.1 200 OK
        |""".stripMargin
    val asHttp = Helpers.httpifyString(raw)
    val bv = asHttp.getBytes()
    Parser.Response.RespPrelude
      .parse[IO](bv, 4096, Parser.Response.RespPrelude.ParserState.initial)
      .map {
        case Right(prelude) =>
          assertEquals(prelude.version, HttpVersion.`HTTP/1.1`)
          assertEquals(prelude.status, Status.Ok)
        case Left(_) => fail("Prelude was not right")
      }
  }

  test("Parser.Response.parser should parse two responses in a row") {
    val defaultMaxHeaderLength = 4096
    val respS =
      Stream(
        "HTTP/1.1 200 OK\r\n",
        "Content-Type: text/plain\r\n",
        "Content-Length: 5\r\n\r\n",
        "helloHTTP/1.1 200 OK\r\n", // this is the crucial part
        "Content-Type: text/plain\r\n",
        "Content-Length: 5\r\n\r\nworld",
      )
    val byteStream: Stream[IO, Byte] = respS
      .flatMap(s =>
        Stream.chunk(Chunk.array(s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)))
      )

    (for {
      take <- Helpers.taking[IO, Byte](byteStream)
      resp1 <- Parser.Response
        .parser[IO](defaultMaxHeaderLength)(Array.emptyByteArray, take)
      body1 <- resp1._1.body.through(fs2.text.utf8.decode).compile.string
      drained <- resp1._2
      _ <- IO(println(new String(drained.get)))
      resp2 <- Parser.Response
        .parser[IO](defaultMaxHeaderLength)(drained.get, take)
      body2 <- resp2._1.body.through(fs2.text.utf8.decode).compile.string
    } yield body1 == "hello" && body2 == "world").assert
  }

  test("Parser.Body should completely consume a body") {
    val bodyS = Stream("hello ", "world!")
    val byteStream: Stream[IO, Byte] = bodyS
      .flatMap(s =>
        Stream.chunk(Chunk.array(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
      )

    for {
      take <- Helpers.taking(byteStream)
      body <- Parser.Body.parseFixedBody(12L, Array.emptyByteArray, take)
      bodyString <- body._1.through(fs2.text.utf8.decode).compile.string
      drained <- body._2
    } yield {
      assertEquals(bodyString, "hello world!")
      assertEquals(drained.map(_.toList), Some(Nil))
    }
  }

  test("Parser.Body should partially consume a body") {
    val bodyS = Stream("hello ", "world!")
    val byteStream: Stream[IO, Byte] = bodyS
      .flatMap(s =>
        Stream.chunk(Chunk.array(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
      )

    for {
      take <- Helpers.taking(byteStream)
      body <- Parser.Body.parseFixedBody(12L, Array.emptyByteArray, take)
      bodyString <- body._1.take(2).through(fs2.text.utf8.decode).compile.string
      drained <- body._2
    } yield {
      assertEquals(bodyString, "he")
      assertEquals(drained, None)
    }
  }

  test("Parser.Body should consume previous bytes") {
    val bodyS = Stream("world!")
    val byteStream: Stream[IO, Byte] = bodyS
      .flatMap(s =>
        Stream.chunk(Chunk.array(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
      )

    for {
      take <- Helpers.taking(byteStream)
      body <- Parser.Body.parseFixedBody(
        12L,
        "hello ".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
        take,
      )
      bodyString <- body._1.through(fs2.text.utf8.decode).compile.string
      drained <- body._2
    } yield {
      assertEquals(bodyString, "hello world!")
      assertEquals(drained.map(_.toList), Some(Nil))
    }
  }

  // Compiling a stream is generally undefined behavior but that doesn't stop us from testing it
  test("Parser.Body should raise an error when compiling the body more than once") {
    val bodyS = Stream("hello world!")
    val byteStream: Stream[IO, Byte] = bodyS
      .flatMap(s =>
        Stream.chunk(Chunk.array(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
      )

    for {
      take <- Helpers.taking(byteStream)
      body <- Parser.Body.parseFixedBody(12L, Array.emptyByteArray, take)
      _ <- body._1.compile.drain
      _ <- body._1.compile.drain.intercept[Throwable]
    } yield ()
  }

  test("Parser.Message.parser should raise an error if prelude/header message is too long") {
    val raw =
      """POST /foo HTTP/1.1
          |Host: localhost:8080
          |User-Agent: curl/7.64.1
          |Accept: */*
          |Content-Length: 5
          |
          |helloeverything after the body""".stripMargin

    val byteStream = Stream
      .emit(raw)
      .map(Helpers.httpifyString)
      .through(text.utf8.encode)

    Helpers.taking[IO, Byte](byteStream).flatMap { take =>
      Parser.Request
        .parser[IO](10)(Array.emptyByteArray, take)
        .intercept[EmberException.MessageTooLong]
    }
  }
}
