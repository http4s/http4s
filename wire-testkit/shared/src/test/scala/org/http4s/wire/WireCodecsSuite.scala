/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import fs2.io.net.Socket
import fs2.io.net.SocketOption
import munit.CatsEffectSuite
import scodec.bits.ByteVector

class WireCodecsSuite extends CatsEffectSuite {
  private def ascii(value: String): ByteVector = Http1Wire.ascii(value)
  private def hex(value: String): ByteVector = ByteVector.fromValidHex(value)

  private def peer(
      bytes: ByteVector,
      fragment: Int = 8192,
      whenEmpty: IO[Option[Chunk[Byte]]] = IO.pure(None),
  ): IO[(RawPeer[IO], IO[ByteVector])] =
    for {
      input <- Ref.of[IO, ByteVector](bytes)
      output <- Ref.of[IO, ByteVector](ByteVector.empty)
      transcript <- Transcript.create[IO]()
      socket = new Socket[IO] {
        private val addr = SocketAddress(ip"127.0.0.1", port"1")
        def read(maxBytes: Int): IO[Option[Chunk[Byte]]] =
          input
            .modify { remaining =>
              if (remaining.isEmpty) (remaining, None)
              else {
                val (head, tail) = remaining.splitAt(math.min(maxBytes, fragment).toLong)
                (tail, Some(Chunk.byteVector(head)))
              }
            }
            .flatMap(_.fold(whenEmpty)(chunk => IO.pure(Some(chunk))))
        def reads: Stream[IO, Byte] =
          Stream.repeatEval(read(8192)).unNoneTerminate.flatMap(Stream.chunk)
        def readN(numBytes: Int): IO[Chunk[Byte]] =
          reads.take(numBytes.toLong).compile.toVector.map(Chunk.from)
        def write(chunk: Chunk[Byte]): IO[Unit] = output.update(_ ++ ByteVector.view(chunk.toArray))
        def writes: Pipe[IO, Byte, Nothing] = _.chunks.evalMap(write).drain
        def endOfInput: IO[Unit] = IO.unit
        def endOfOutput: IO[Unit] = IO.unit
        def isOpen: IO[Boolean] = IO.pure(true)
        def localAddress: IO[SocketAddress[IpAddress]] = IO.pure(addr)
        def remoteAddress: IO[SocketAddress[IpAddress]] = IO.pure(addr)
        def address: GenSocketAddress = addr
        def peerAddress: GenSocketAddress = addr
        def getOption[A](key: SocketOption.Key[A]): IO[Option[A]] = IO.pure(None)
        def setOption[A](key: SocketOption.Key[A], value: A): IO[Unit] = IO.unit
        def supportedOptions: IO[Set[SocketOption.Key[_]]] = IO.pure(Set.empty)
      }
      raw <- RawPeer.create(socket, transcript)
    } yield (raw, output.get)

  test("raw delimiter reads preserve following bytes across every fragmentation") {
    (1 to 10).toList.traverse_ { size =>
      peer(ascii("abc\r\ndef"), size).flatMap { case (raw, _) =>
        (raw.readUntil(Http1Wire.crlf, 3), raw.readExact(3), raw.readToEnd()).tupled.map {
          case (line, rest, eof) =>
            assertEquals(line, ascii("abc"))
            assertEquals(rest, ascii("def"))
            assertEquals(eof, ByteVector.empty)
        }
      }
    }
  }

  test("raw reads reject EOF and enforce their limits") {
    for {
      exact <- peer(ascii("ab")).flatMap(_._1.readExact(3).attempt)
      delimiter <- peer(ascii("abc\r\n")).flatMap(_._1.readUntil(Http1Wire.crlf, 2).attempt)
      eof <- peer(ascii("abc")).flatMap(_._1.readToEnd(2).attempt)
    } yield {
      assert(exact.isLeft)
      assert(delimiter.isLeft)
      assert(eof.isLeft)
    }
  }

  Vector[(String, RawPeer[IO] => IO[ByteVector])](
    "exact" -> ((raw: RawPeer[IO]) => raw.readExact(5)),
    "delimiter" -> ((raw: RawPeer[IO]) => raw.readUntil(Http1Wire.crlf)),
    "EOF" -> ((raw: RawPeer[IO]) => raw.readToEnd()),
  ).foreach { case (name, read) =>
    test(s"canceling a blocked $name read preserves its received prefix and releases the reader") {
      for {
        blocked <- Deferred[IO, Unit]
        p <- peer(ascii("abc\r"), whenEmpty = blocked.complete(()).void *> IO.never)
        fiber <- read(p._1).start
        _ <- blocked.get
        _ <- fiber.cancel
        prefix <- p._1.readExact(4)
      } yield assertEquals(prefix, ascii("abc\r"))
    }
  }

  test("transcript reports omitted bytes without retaining them") {
    for {
      transcript <- Transcript.create[IO](3)
      _ <- transcript.record("sent", ascii("abcde"))
      _ <- transcript.record("received", ascii("fgh"))
      rendered <- transcript.render
    } yield assertEquals(rendered, "sent 616263 |abc|\n[5 bytes omitted]")
  }

  test("HTTP/1 parses a fragmented chunked response, extensions, and trailers") {
    val input = ascii(
      "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n" +
        "2;name=\"a;\\\"b\"\r\nab\r\n1\r\nc\r\n0\r\nChecksum: yes\r\n\r\n"
    )
    peer(input, 1).flatMap { case (raw, _) =>
      Http1Wire.readResponse(raw).map { response =>
        assertEquals(response.status, 200)
        assertEquals(response.body, ascii("abc"))
        assertEquals(response.trailers, Vector("Checksum" -> "yes"))
      }
    }
  }

  test("HTTP/1 does not consume a HEAD or informational response's following message") {
    val input = ascii(
      "HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 200 OK\r\nContent-Length: 99\r\n\r\n" +
        "HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabc"
    )
    peer(input).flatMap { case (raw, _) =>
      for {
        interim <- Http1Wire.readResponse(raw)
        head <- Http1Wire.readResponse(raw, "HEAD")
        get <- Http1Wire.readResponse(raw)
      } yield {
        assertEquals(interim.status, 100)
        assertEquals(interim.body, ByteVector.empty)
        assertEquals(head.body, ByteVector.empty)
        assertEquals(get.body, ascii("abc"))
      }
    }
  }

  test("HTTP/1 reads EOF-delimited responses and unframed requests have no body") {
    for {
      response <- peer(ascii("HTTP/1.0 200 OK\r\n\r\nabc"))
        .flatMap(p => Http1Wire.readResponse(p._1))
      request <- peer(ascii("GET /a?b=c HTTP/1.1\r\nHost: example.com\r\n\r\n"))
        .flatMap(p => Http1Wire.readRequest(p._1))
    } yield {
      assertEquals(response.body, ascii("abc"))
      assertEquals(request.method, "GET")
      assertEquals(request.target, "/a?b=c")
      assertEquals(request.body, ByteVector.empty)
    }
  }

  test("HTTP/1 accepts identical repeated content lengths") {
    peer(ascii("HTTP/1.1 200 OK\r\nContent-Length: 3, 3\r\nContent-Length: 3\r\n\r\nabc"))
      .flatMap(p => Http1Wire.readResponse(p._1))
      .map(r => assertEquals(r.body, ascii("abc")))
  }

  Vector(
    "conflicting lengths" -> "Content-Length: 2\r\nContent-Length: 3\r\n\r\nabc",
    "transfer encoding plus length" -> "Content-Length: 3\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n",
    "truncated fixed body" -> "Content-Length: 3\r\n\r\nab",
    "missing chunk terminator" -> "Transfer-Encoding: chunked\r\n\r\n1\r\naXX0\r\n\r\n",
    "invalid chunk length" -> "Transfer-Encoding: chunked\r\n\r\n+1\r\na\r\n0\r\n\r\n",
    "repeated chunking" -> "Transfer-Encoding: chunked, chunked\r\n\r\n0\r\n\r\n",
    "nonfinal chunking" -> "Transfer-Encoding: chunked, gzip\r\n\r\n",
    "whitespace before field colon" -> "Content-Length : 0\r\n\r\n",
    "obsolete line folding" -> "A: first\r\n second\r\n\r\n",
    "framing trailers" -> "Transfer-Encoding: chunked\r\n\r\n0\r\nContent-Length: 0\r\n\r\n",
  ).foreach { case (name, tail) =>
    test(s"HTTP/1 rejects $name") {
      peer(ascii("HTTP/1.1 200 OK\r\n" + tail))
        .flatMap(p => Http1Wire.readResponse(p._1).attempt)
        .map(result => assert(result.isLeft))
    }
  }

  test("HTTP/2 wire frame matches its independent byte fixture") {
    val frame = Http2Wire.Frame(Http2Wire.Ping, Http2Wire.Ack, 0, ascii("12345678"))
    val fixture = hex("0000080601000000003132333435363738")
    assertEquals(Http2Wire.encode(frame), fixture)
    assertEquals(Http2Wire.decodeAll(fixture), Right(Vector(frame)))
    peer(fixture, 1)
      .flatMap(p => Http2Wire.readFrame(p._1))
      .map(actual => assertEquals(actual, frame))
  }

  test("HTTP/2 preserves intentional declared-length overrides and rejects truncated captures") {
    val bytes = Http2Wire.encode(Http2Wire.data(1, ascii("a")), declaredLength = Some(2))
    assertEquals(bytes.take(3), hex("000002"))
    assert(Http2Wire.decodeAll(bytes).isLeft)
    assert(Http2Wire.decodeAll(hex("00000100")).isLeft)
    peer(bytes).flatMap(p => Http2Wire.readFrame(p._1).attempt).map(r => assert(r.isLeft))
  }

  test("HTTP/2 rejects an oversized declaration before trying to buffer its payload") {
    val header = hex("004001000000000001")
    peer(header, whenEmpty = IO.raiseError(new AssertionError("payload must not be requested")))
      .flatMap(p => Http2Wire.readFrame(p._1, maxFrameBytes = 16384).attempt)
      .map(result =>
        assertEquals(result.left.map(_.getMessage), Left("frame length 16385 exceeds limit 16384"))
      )
  }

  test("HTTP/2 ignores the reserved stream identifier bit") {
    val bytes = hex("000000010480000001")
    peer(bytes).flatMap(p => Http2Wire.readFrame(p._1)).map(f => assertEquals(f.streamId, 1))
  }

  test("HTTP/2 control handling acknowledges SETTINGS and echoes PING") {
    val ping = Http2Wire.Frame(Http2Wire.Ping, 0, 0, ascii("12345678"))
    val headers = Http2Wire.headers(1, Vector(":status" -> "200"))
    val bytes =
      Http2Wire.encode(Http2Wire.settings()) ++ Http2Wire.encode(ping) ++ Http2Wire.encode(headers)
    peer(bytes, 3).flatMap { case (raw, written) =>
      for {
        frame <- Http2Wire.readUntil(raw)(_.frameType == Http2Wire.Headers)
        output <- written
      } yield {
        assertEquals(frame, headers)
        assertEquals(
          Http2Wire.decodeAll(output),
          Right(Vector(Http2Wire.settings(ack = true), ping.copy(flags = Http2Wire.Ack))),
        )
      }
    }
  }

  test("HTTP/2 decodes padded priority HEADERS followed by CONTINUATION") {
    val first = Http2Wire.Frame(Http2Wire.Headers, 0x28, 1, hex("02000000000088ffff"))
    val continuation = Http2Wire.Frame(
      Http2Wire.Continuation,
      Http2Wire.EndHeaders,
      1,
      HpackWire.encodeLiteral(Vector("a" -> "b")),
    )
    for {
      decoder <- HpackWire.decoder[IO]()
      p <- peer(Http2Wire.encode(continuation), 1)
      headers <- Http2Wire.readHeaders(p._1, first, decoder)
    } yield assertEquals(headers, Vector(":status" -> "200", "a" -> "b"))
  }

  test("HTTP/2 rejects an interleaved frame during CONTINUATION") {
    val first = Http2Wire.Frame(Http2Wire.Headers, 0, 1, hex("88"))
    for {
      decoder <- HpackWire.decoder[IO]()
      p <- peer(Http2Wire.encode(Http2Wire.settings()))
      decoded <- Http2Wire.readHeaders(p._1, first, decoder).attempt
    } yield assert(decoded.isLeft)
  }

  test("HTTP/2 handshake validates initial SETTINGS and records exact client preface") {
    for {
      p <- peer(Http2Wire.encode(Http2Wire.settings()))
      _ <- Http2Wire.clientHandshake(p._1)
      output <- p._2
      invalid <- peer(Http2Wire.encode(Http2Wire.settings(ack = true)))
        .flatMap(p => Http2Wire.clientHandshake(p._1).attempt)
    } yield {
      assertEquals(
        output,
        Http2Wire.clientPreface ++ Http2Wire.encode(Http2Wire.settings()) ++
          Http2Wire.encode(Http2Wire.settings(ack = true)),
      )
      assert(invalid.left.toOption.exists(_.isInstanceOf[ProtocolFailure]))
    }
  }

  test(
    "HTTP/2 server handshake receives complete client SETTINGS before sending SETTINGS then ACK"
  ) {
    for {
      blocked <- Deferred[IO, Unit]
      clientSettings <- Deferred[IO, Chunk[Byte]]
      p <- peer(
        Http2Wire.clientPreface,
        whenEmpty = blocked.complete(()).void *> clientSettings.get.map(Some(_)),
      )
      fiber <- Http2Wire.serverHandshake(p._1).start
      _ <- blocked.get
      before <- p._2
      _ <- clientSettings.complete(Chunk.byteVector(Http2Wire.encode(Http2Wire.settings())))
      _ <- fiber.joinWithNever
      after <- p._2
    } yield {
      assertEquals(before, ByteVector.empty)
      assertEquals(
        Http2Wire.decodeAll(after),
        Right(Vector(Http2Wire.settings(), Http2Wire.settings(ack = true))),
      )
    }
  }

  test("HTTP/2 malformed initial SETTINGS are a protocol failure, without a success ACK") {
    val invalid = Http2Wire.Frame(Http2Wire.Settings, 0, 0, hex("00"))
    for {
      p <- peer(Http2Wire.clientPreface ++ Http2Wire.encode(invalid))
      result <- Http2Wire.serverHandshake(p._1).attempt
      output <- p._2
    } yield {
      assert(result.left.toOption.exists(_.isInstanceOf[ProtocolFailure]))
      assertEquals(output, ByteVector.empty)
    }
  }
}
