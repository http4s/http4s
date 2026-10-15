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
import cats.effect.Resource
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import scodec.bits.ByteVector

private[wire] object WireCodecsSuite {

  def runH2ServerCase(id: String, peer: Resource[IO, RawPeer[IO]]): IO[Unit] = {
    val target = ScriptedServer[IO](
      "frame-boundary-fixture",
      Resource.pure(
        ServerHandle[IO](SocketAddress(ip"127.0.0.1", port"1"), IO.never, IO.pure(None))
      ),
    )
    Transcript.create[IO]().flatMap { transcript =>
      Http2ControlCases.all
        .find(_.id == id)
        .get
        .run(CaseContext(target, transcript, (_, _) => peer))
    }
  }

}

class WireCodecsSuite extends RuntimeDiagnostics.Suite {
  // Scripted peers intentionally emit invalid traffic; conformance cases supply their own scope.
  implicit private def codecSettings[F[_]: cats.ApplicativeThrow]: Http2PeerSettings[F] =
    Http2PeerSettings.untracked[F]

  import ScriptedPeer.peer

  test("diagnostic text cannot impersonate a typed wire boundary") {
    val error = new IllegalArgumentException("unexpected EOF: needed 1 bytes, received 0")
    peer(ByteVector.empty, whenEmpty = IO.raiseError(error)).flatMap { case (raw, _) =>
      Http2Wire.readFrameOrClose(raw).attempt.assertEquals(Left(error))
    }
  }

  test("untracked codecs cannot silently create a positive observer") {
    peer(ByteVector.empty).flatMap { case (raw, _) =>
      Http2PeerSettings.untracked[IO].positive(raw).intercept[IllegalStateException]
    }
  }

  test("peer settings scope preserves arbitrary result types") {
    Http2PeerSettings.scoped[IO, Int](initiatesRequests = true)(_ => IO.pure(42)).assertEquals(42)
  }

  private def ascii(value: String): ByteVector = Http1Wire.ascii(value)
  private def hex(value: String): ByteVector = ByteVector.fromValidHex(value)

  for (
    (label, block, maxBytes) <- Vector(
      ("encoded block", ByteVector.fill(1025)(0x88.toByte), 0),
      ("literal string", HpackWire.encodeLiteral(Vector("x" -> "value")), 33),
      ("indexed fields", ByteVector(0x88.toByte, 0x88.toByte), 42),
      ("Huffman string", ByteVector.fromValidHex("00810700"), 32),
    )
  ) test(s"HPACK $label observation limit remains an infrastructure outcome") {
    for {
      decoder <- HpackWire.decoder[IO](maxHeaderBytes = maxBytes)
      error <- decoder.decode(block).intercept[WireObservationLimit]
      _ <- IO(assertEquals(WireSuite.outcomeStatus(Left(error)), "error"))
      _ <- IO(assert(error.getMessage.contains("observation incomplete")))
    } yield ()
  }

  for (
    (label, block) <- Vector(
      "zero index" -> ByteVector(0x80.toByte),
      "table above advertised maximum" -> HpackWire.encodeInteger(4097, 5, 0x20),
      "invalid Huffman padding" -> ByteVector.fromValidHex("00810000"),
    )
  ) test(s"HTTP/2 $label remains a malformed-wire failure") {
    for {
      raw <- peer(ByteVector.empty)
      decoder <- HpackWire.decoder[IO]()
      error <- Http2Wire
        .readHeaders(
          raw._1,
          Http2Wire.Frame(Http2Wire.Headers, Http2Wire.EndHeaders, 1, block),
          decoder,
        )
        .intercept[WireDecodingFailure]
      _ <- IO(assertEquals(WireSuite.outcomeStatus(Left(error)), "fail"))
    } yield ()
  }

  test("HTTP/2 header observation preserves the typed HPACK limit") {
    for {
      raw <- peer(ByteVector.empty)
      decoder <- HpackWire.decoder[IO](maxHeaderBytes = 0)
      error <- Http2Wire
        .readHeaders(
          raw._1,
          Http2Wire.headers(1, Vector(":status" -> "200")),
          decoder,
        )
        .intercept[WireObservationLimit]
      _ <- IO(assertEquals(WireSuite.outcomeStatus(Left(error)), "error"))
    } yield ()
  }

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

  for (
    (label, bytes) <- Vector(
      "line" -> ascii("HTTP/1.1 200 OK\r\nX-Large: " + ("a" * 8193) + "\r\n\r\n"),
      "header count" -> ascii("HTTP/1.1 200 OK\r\n" + ("X: a\r\n" * 257) + "\r\n"),
      "header bytes" -> ascii(
        "HTTP/1.1 200 OK\r\n" + (("X: " + ("a" * 4096) + "\r\n") * 17) + "\r\n"
      ),
      "content length" -> ascii("HTTP/1.1 200 OK\r\nContent-Length: 16777217\r\n\r\n"),
      "chunk size" -> ascii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n1000001\r\n"),
      "interim count" -> ascii("HTTP/1.1 100 Continue\r\n\r\n" * 17),
    )
  ) test(s"HTTP/1 $label ceiling is an observation limit") {
    peer(bytes).flatMap { case (raw, _) =>
      Http1Wire
        .readFinalResponse(raw)
        .intercept[WireObservationLimit]
        .map(e => assertEquals(WireSuite.outcomeStatus(Left(e)), "error"))
    }
  }

  test("raw retention ceilings are typed independently of invalid arguments and EOF") {
    for {
      _ <- peer(ascii("abc\r\n"))
        .flatMap(_._1.readUntil(Http1Wire.crlf, 2))
        .intercept[WireObservationLimit]
      _ <- peer(ascii("abc")).flatMap(_._1.readToEnd(2)).intercept[WireObservationLimit]
      _ <- peer(ByteVector.empty).flatMap(_._1.readToEnd(-1)).intercept[IllegalArgumentException]
      _ <- peer(ascii("abc"))
        .flatMap(_._1.readUntil(Http1Wire.crlf))
        .intercept[IllegalArgumentException]
    } yield ()
  }

  test("raw reads accepting resets preserve the prefix when unrelated I/O fails") {
    val error = new java.io.IOException("unrelated I/O failure")
    for {
      p <- peer(ascii("abc"), fragment = 1, whenEmpty = IO.raiseError(error))
      result <- p._1.readToEnd(allowReset = true).attempt
      prefix <- p._1.readExact(3)
    } yield {
      assertEquals(result, Left(error))
      assertEquals(prefix, ascii("abc"))
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

  for (transfer <- Vector("", " \t", ",", "chunked,", ",chunked", "chunked,,gzip")) {
    test(s"HTTP/1 empty transfer list response: '$transfer'") {
      val run = peer(ascii(s"HTTP/1.1 200 OK\r\nTransfer-Encoding: $transfer\r\n\r\nabc"))
        .flatMap(p => Http1Wire.readResponse(p._1))
      if (transfer.trim.isEmpty) run.map(r => assertEquals(r.body, ascii("abc")))
      else run.intercept[WireDecodingFailure].void
    }
    test(s"HTTP/1 empty transfer list cannot delimit request: '$transfer'") {
      peer(ascii(s"POST / HTTP/1.1\r\nTransfer-Encoding: $transfer\r\n\r\nabc"))
        .flatMap(p => Http1Wire.readRequest(p._1))
        .intercept[WireDecodingFailure]
        .void
    }
  }

  test("HTTP/1 empty transfer list is still a framing field conflicting with Content-Length") {
    peer(ascii("HTTP/1.1 200 OK\r\nTransfer-Encoding:\r\nContent-Length: 3\r\n\r\nabc"))
      .flatMap(p => Http1Wire.readResponse(p._1))
      .intercept[WireDecodingFailure]
      .map(e => assert(e.getMessage.contains("both transfer-encoding and content-length")))
  }

  for {
    extension <- Vector(
      ";flag",
      " ; flag",
      ";flag \t; next",
      ";flag \t= \tvalue",
      ";flag ",
      ";flag\t",
    )
    last <- Vector(false, true)
  } test(s"HTTP/1 bare extension boundary '$extension' last=$last") {
    val input = ascii(
      "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n" +
        "3" + (if (last) "" else extension) + "\r\nabc\r\n0" +
        (if (last) extension else "") + "\r\n\r\n"
    )
    val run = peer(input, 1).flatMap(p => Http1Wire.readResponse(p._1))
    if (extension.last == ' ' || extension.last == '\t')
      run
        .intercept[WireDecodingFailure]
        .map(e => assertEquals(e.getMessage, "trailing whitespace after bare chunk extension"))
    else run.map(r => assertEquals(r.body, ascii("abc")))
  }

  test("HTTP/1 recipient request observation retains compressed message bytes") {
    val gzip = hex("1f8b08000000000000ff63f8df000040a7dd8103000000")
    peer(
      ascii("POST / HTTP/1.1\r\nTransfer-Encoding: gzip, chunked\r\n\r\n17\r\n") ++
        gzip ++ ascii("\r\n0\r\n\r\n"),
      1,
    )
      .flatMap(p => Http1Wire.readRequest(p._1))
      .map(r => assertEquals(r.body, gzip))
  }

  test("HTTP/1 generated request content limit follows complete framing observation") {
    val gzip = hex("1f8b08000000000000ff63f8df000040a7dd8103000000")
    peer(
      ascii("POST / HTTP/1.1\r\nTransfer-Encoding: GZip, ChUnKeD\r\n\r\n17\r\n") ++
        gzip ++ ascii("\r\n0\r\nX-Trace: done\r\n\r\nnext"),
      1,
    ).flatMap { case (raw, _) =>
      for {
        error <- Http1Wire.readGeneratedRequest(raw).intercept[WireObservationLimit]
        remaining <- raw.readExact(4)
      } yield {
        assert(
          error.getMessage.startsWith(
            "cannot observe request content: unsupported transfer decoding: gzip;"
          )
        )
        assertEquals(remaining, ascii("next"))
      }
    }
  }

  for (generated <- Vector(false, true))
    test(s"HTTP/1 Content-Length recovery is recipient-only: generated=$generated") {
      peer(ascii("POST / HTTP/1.1\r\nContent-Length: 3, 03\r\nContent-Length: 003\r\n\r\nabc"))
        .flatMap { case (raw, _) =>
          if (generated)
            Http1Wire
              .readGeneratedRequest(raw)
              .intercept[WireDecodingFailure]
              .map(e =>
                assertEquals(e.getMessage, "generated content-length must be one decimal field")
              )
          else Http1Wire.readRequest(raw).map(r => assertEquals(r.body, ascii("abc")))
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
    "whitespace before field colon" -> "Content-Length : 0\r\n\r\n",
    "obsolete line folding" -> "A: first\r\n second\r\n\r\n",
    "framing trailers" -> "Transfer-Encoding: chunked\r\n\r\n0\r\nContent-Length: 0\r\n\r\n",
  ).foreach { case (name, tail) =>
    test(s"HTTP/1 rejects $name") {
      peer(ascii("HTTP/1.1 200 OK\r\n" + tail))
        .flatMap(p => Http1Wire.readResponse(p._1).attempt)
        .map(result => assert(result.left.exists(_.isInstanceOf[WireDecodingFailure])))
    }
  }

  test("HTTP/1 nonfinal chunked response uses EOF framing and retains outer gzip bytes") {
    val body =
      ByteVector.fromValidHex("1f8b08000000000002ff33e6e54a4c4ae6e532e0e5e2e5020045cff8100d000000")
    peer(ascii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked, gzip\r\n\r\n") ++ body)
      .flatMap(p => Http1Wire.readResponse(p._1))
      .map(r => assertEquals(r.body, body))
  }

  test("HTTP/1 request still requires final chunked coding") {
    peer(ascii("POST / HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked, gzip\r\n\r\n"))
      .flatMap(p => Http1Wire.readRequest(p._1))
      .intercept[WireDecodingFailure]
      .void
  }

  test("decoder failures distinguish malformed wire from infrastructure and invalid arguments") {
    val ioError = new java.io.IOException("read failed")
    val argumentError = new IllegalArgumentException("invalid harness argument")
    for {
      malformed <- peer(ascii("HTTP/1.1 200 OK\r\nContent-Length: eight\r\n\r\n"))
        .flatMap(p => Http1Wire.readResponse(p._1).attempt)
      ioFailure <- peer(ByteVector.empty, whenEmpty = IO.raiseError(ioError))
        .flatMap(p => Http1Wire.readResponse(p._1).attempt)
      argumentFailure <- peer(ByteVector.empty, whenEmpty = IO.raiseError(argumentError))
        .flatMap(p => Http2Wire.readFrame(p._1).attempt)
      invalidLimit <- peer(
        ByteVector.empty,
        whenEmpty = IO.raiseError(new AssertionError("must not read")),
      )
        .flatMap(p => Http2Wire.readFrame(p._1, -1).attempt)
    } yield {
      assert(malformed.left.exists(_.isInstanceOf[WireDecodingFailure]))
      assertEquals(WireSuite.outcomeStatus(malformed.void), "fail")
      assertEquals(ioFailure, Left(ioError))
      assertEquals(argumentFailure, Left(argumentError))
      assertEquals(WireSuite.outcomeStatus(ioFailure.void), "error")
      assertEquals(WireSuite.outcomeStatus(invalidLimit.void), "error")
      assert(invalidLimit.left.exists(_.isInstanceOf[IllegalArgumentException]))
      intercept[IllegalArgumentException](
        Http2Wire.encode(Http2Wire.Frame(-1, 0, 0, ByteVector.empty))
      )
    }
  }

  test("RFC 9113 ignores self-dependent PRIORITY and HEADERS priority information") {
    val priority = Http2Wire.Frame(Http2Wire.Priority, 0, 1, hex("8000000100"))
    val headers = Http2Wire.Frame(Http2Wire.Headers, 0x24, 1, hex("800000010088"))
    for {
      p <- peer(Http2Wire.encode(priority) ++ Http2Wire.encode(headers))
      first <- Http2Wire.readUntil(p._1, fromServer = true)(_.frameType == Http2Wire.Headers)
      decoder <- HpackWire.decoder[IO]()
      decoded <- Http2Wire.readHeaders(p._1, first, decoder)
    } yield assertEquals(decoded, Vector(":status" -> "200"))
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

  for (
    shape <- Vector(
      "reset",
      "ordinary",
      "timeout-cause",
      "timeout-suppressed",
      "timeout-cycle",
      "adapter-cause",
      "adapter-suppressed",
      "adapter-over-timeout",
      "eof-timeout",
      "eof-adapter",
    )
  ) {
    def failure(): (Throwable, Option[Throwable], String) = {
      val reset = Http1ResetFixture.reset
      val timeout = new java.util.concurrent.TimeoutException("read deadline")
      val adapter = new AdapterFailure("fixture read", reset)
      val error = shape match {
        case "reset" => reset
        case "ordinary" => new java.io.IOException("unrelated read error")
        case "timeout-cause" => timeout.initCause(reset)
        case "timeout-suppressed" => reset.addSuppressed(timeout); reset
        case "timeout-cycle" =>
          val outer = new java.io.IOException("wrapper", reset)
          reset.initCause(outer)
          reset.addSuppressed(timeout)
          outer
        case "adapter-cause" => adapter
        case "adapter-suppressed" => reset.addSuppressed(adapter); reset
        case "adapter-over-timeout" =>
          timeout.initCause(reset)
          timeout.addSuppressed(adapter)
          timeout
        case _ =>
          val eof = new IllegalArgumentException("unexpected EOF: needed 1 bytes, received 0")
          eof.addSuppressed(if (shape == "eof-timeout") timeout else adapter)
          eof
      }
      val expected =
        if (shape.contains("adapter")) Some(adapter)
        else if (shape == "reset") None
        else Some(error)
      val status =
        if (shape.contains("adapter") || shape == "ordinary") "error"
        else if (shape == "reset") "pass"
        else "inconclusive"
      (error, expected, status)
    }

    test(s"HTTP/2 frame boundary preserves failure provenance: $shape") {
      val (error, expected, status) = failure()
      peer(ByteVector.empty, whenEmpty = IO.raiseError(error))
        .flatMap(p => Http2Wire.readFrameOrClose(p._1).attempt)
        .map { result =>
          assertEquals(WireSuite.outcomeStatus(result.void), status)
          if (status == "inconclusive") {
            val normalized = result.fold(identity, value => fail(s"expected failure, got $value"))
            assert(normalized.isInstanceOf[java.util.concurrent.TimeoutException])
            assert((normalized eq error) || (normalized.getCause eq error))
          } else
            expected match {
              case Some(value) => assertEquals(result, Left(value))
              case None => assertEquals(result, Right(None))
            }
        }
    }

    test(s"h2.server.reject-preface-magic preserves boundary outcome: $shape") {
      val (error, expected, status) = failure()
      WireCodecsSuite
        .runH2ServerCase(
          "h2.server.reject-preface-magic",
          Resource.eval(peer(ByteVector.empty, whenEmpty = IO.raiseError(error)).map(_._1)),
        )
        .attempt
        .map { result =>
          assertEquals(WireSuite.outcomeStatus(result), status)
          if (status == "error") assertEquals(result, Left(expected.get))
        }
    }

    if (!shape.startsWith("eof-"))
      test(s"HTTP/2 partial frames retain the read error: $shape") {
        val header = hex("000009000000000001")
        ((1 to 9).map(n => header.take(n.toLong)).toVector :+ (header ++ ascii("abc")))
          .traverse_ { prefix =>
            val (error, expected, status) = failure()
            peer(prefix, fragment = 1, whenEmpty = IO.raiseError(error))
              .flatMap(p => Http2Wire.readFrameOrClose(p._1).attempt)
              .map { result =>
                assert(result.isLeft)
                assertEquals(
                  WireSuite.outcomeStatus(result.void),
                  if (shape == "reset") "error" else status,
                )
                if (status == "inconclusive") {
                  val normalized =
                    result.fold(identity, value => fail(s"expected failure, got $value"))
                  assert((normalized eq error) || (normalized.getCause eq error))
                } else assertEquals(result, Left(expected.getOrElse(error)))
              }
          }
      }
  }

  test("HTTP/2 accepts an empty connection closure before a frame") {
    peer(ByteVector.empty)
      .flatMap(p => Http2Wire.readFrameOrClose(p._1))
      .map(frame => assertEquals(frame, None))
  }

  test("HTTP/2 reads a complete frame before observing closure") {
    val frame = Http2Wire.data(1, ascii("abc"))
    for {
      p <- peer(Http2Wire.encode(frame), fragment = 1)
      received <- Http2Wire.readFrameOrClose(p._1)
      closed <- Http2Wire.readFrameOrClose(p._1)
    } yield {
      assertEquals(received, Some(frame))
      assertEquals(closed, None)
    }
  }

  test("HTTP/2 rejects every partial header before closure") {
    val header = hex("000009000000000001")
    (1 to 8).toList.traverse_ { size =>
      peer(header.take(size.toLong), fragment = 1)
        .flatMap(p => Http2Wire.readFrameOrClose(p._1).attempt)
        .map(result =>
          assertEquals(
            result.left.map(_.getMessage),
            Left(s"unexpected EOF: needed 8 bytes, received ${size - 1}"),
          )
        )
    }
  }

  Vector("absent" -> ByteVector.empty, "partial" -> ascii("abc")).foreach { case (name, payload) =>
    test(s"HTTP/2 rejects closure with $name nine-byte payload") {
      val bytes = Http2Wire.encode(Http2Wire.data(1, payload), declaredLength = Some(9))
      peer(bytes, fragment = 1)
        .flatMap(p => Http2Wire.readFrameOrClose(p._1).attempt)
        .map(result =>
          assertEquals(
            result.left.map(_.getMessage),
            Left(s"unexpected EOF: needed 9 bytes, received ${payload.size}"),
          )
        )
    }
  }

  test("HTTP/2 does not treat an unrelated I/O error as connection closure") {
    val error = new java.io.IOException("unrelated I/O failure")
    peer(ByteVector.empty, whenEmpty = IO.raiseError(error))
      .flatMap(p => Http2Wire.readFrameOrClose(p._1).attempt)
      .map(result => assertEquals(result, Left(error)))
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
        frame <- Http2Wire.readUntil(raw, fromServer = true)(_.frameType == Http2Wire.Headers)
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

  for {
    fromServer <- Vector(false, true)
    value <- Vector(0L, 1L, 2L)
    placement <- Vector("initial", "before-headers", "content")
  } test(s"generic SETTINGS sender role: $fromServer/$value/$placement") {
    import Http2Wire._
    val update = settings(Vector(2 -> value))
    val head = headers(
      1,
      if (fromServer) Vector(":status" -> "200") else Vector(":method" -> "GET"),
      endStream = false,
    )
    val content = data(1, ascii("body"))
    val input = placement match {
      case "initial" => (if (fromServer) ByteVector.empty else clientPreface) ++ encode(update)
      case "before-headers" => encode(update) ++ encode(head)
      case _ => encode(head) ++ encode(update) ++ encode(content)
    }
    val run = peer(input, 1).flatMap { case (raw, written) =>
      val observe = placement match {
        case "initial" => if (fromServer) clientHandshake(raw) else serverHandshake(raw)
        case "before-headers" =>
          readUntil(raw, fromServer = fromServer)(_.frameType == Headers)
            .flatMap(Check.equal(_, head))
        case _ =>
          readUntil(raw, fromServer = fromServer)(_.frameType == Headers)
            .flatMap(Check.equal(_, head)) *>
            readUntil(raw, fromServer = fromServer)(_.frameType == Data)
              .flatMap(Check.equal(_, content))
      }
      observe *> written.flatMap { output =>
        val frames =
          if (placement == "initial" && fromServer) output.drop(clientPreface.size) else output
        IO.fromEither(decodeAll(frames)).flatMap { decoded =>
          Check.equal(decoded.count(f => f.frameType == Settings && f.hasFlag(Ack)), 1)
        }
      }
    }
    if (value > (if (fromServer) 0L else 1L))
      run.intercept[ProtocolFailure].flatMap(error => IO(assert(error.getMessage.contains("PUSH"))))
    else run
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
      assert(invalid.left.exists(_.isInstanceOf[ProtocolFailure]))
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
      assert(result.left.exists(_.isInstanceOf[ProtocolFailure]))
      assertEquals(output, ByteVector.empty)
    }
  }
}
