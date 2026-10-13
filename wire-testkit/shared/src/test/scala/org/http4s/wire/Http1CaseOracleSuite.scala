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
import cats.effect.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import fs2.Stream
import fs2.io.net.Network
import munit.CatsEffectSuite
import scodec.bits.ByteVector

class Http1CaseOracleSuite extends CatsEffectSuite {

  for {
    outcome <- Vector(
      "success",
      "native-acquire",
      "native-body",
      "assertion",
      "adapter",
      "decoder",
      "observation",
      "timeout",
    )
    releaseFails <- Vector(false, true)
  } test(s"smoke rejection classifies before release: $outcome/releaseFails=$releaseFails") {
    val failure: Throwable = outcome match {
      case "assertion" => new ProtocolFailure("smoke assertion")
      case "adapter" => new AdapterFailure("smoke fixture", new RuntimeException("fixture"))
      case "decoder" => new WireDecodingFailure("smoke decoder")
      case "observation" => new WireObservationLimit("smoke observation")
      case "timeout" => new java.util.concurrent.TimeoutException("smoke native timeout")
      case _ => new RuntimeException("smoke native rejection")
    }
    val releaseFailure = new RuntimeException("smoke response release")
    for {
      releases <- cats.effect.Ref.of[IO, Int](0)
      consumed <- cats.effect.Ref.of[IO, Boolean](false)
      result <- runClient(
        "h1.client.reject-conflicting-content-length",
        releases.update(_ + 1) *> (if (releaseFails) IO.raiseError[Unit](releaseFailure)
                                   else IO.unit),
      ) { (peer, request) =>
        for {
          _ <- sendRequest(peer, request)
          head <- peer.readUntil(Http1Wire.ascii("\r\n\r\n"))
          _ <- IO(
            assertEquals(
              head,
              Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nContent-Length: 3"),
            )
          )
          bytes <- peer.readExact(3)
          _ <- IO(assertEquals(bytes, Http1Wire.ascii("abc")))
          response <-
            if (outcome == "native-acquire") IO.raiseError[ResponseData[IO]](failure)
            else
              IO.pure(
                ResponseData[IO](
                  200,
                  Vector.empty,
                  Stream.eval(consumed.set(true)).drain ++
                    (if (outcome == "success") Stream.chunk(Chunk.byteVector(bytes))
                     else Stream.raiseError[IO](failure)),
                  IO.pure(Vector.empty),
                )
              )
        } yield response
      }.attempt
      _ <- releases.get.assertEquals(if (outcome == "native-acquire") 0 else 1)
      _ <- consumed.get.assertEquals(outcome != "native-acquire")
      _ <- IO {
        if (outcome == "success")
          assert(
            result.swap.exists(e =>
              e.isInstanceOf[ProtocolFailure] && e.getMessage.contains("successfully consumed")
            ),
            result.toString,
          )
        else if (outcome == "native-acquire" || outcome == "native-body")
          assertEquals(
            result,
            if (releaseFails && outcome == "native-body") Left(releaseFailure) else Right(()),
          )
        else assertEquals(result, Left(failure))
      }
    } yield ()
  }

  test("smoke rejection cancellation releases an acquired response exactly once") {
    for {
      consumed <- Deferred[IO, Unit]
      releases <- cats.effect.Ref.of[IO, Int](0)
      run = runClient("h1.client.reject-conflicting-content-length", releases.update(_ + 1)) {
        (peer, request) =>
          sendRequest(peer, request) *> peer.readUntil(Http1Wire.ascii("\r\n\r\n")) *> peer
            .readExact(3) *>
            IO.pure(
              ResponseData[IO](
                200,
                Vector.empty,
                Stream.eval(consumed.complete(()).void *> IO.never[Byte]),
                IO.pure(Vector.empty),
              )
            )
      }
      _ <- Resource.make(run.start)(_.cancel).use { fiber =>
        consumed.get *> releases.get.assertEquals(0) *> fiber.cancel *>
          fiber.join.flatMap {
            case cats.effect.Outcome.Canceled() => IO.unit
            case other => IO(fail(s"expected cancellation, got $other"))
          }
      }
      _ <- releases.get.assertEquals(1)
    } yield ()
  }

  private val foldCases = Vector(
    "h1.server.obsolete-fold-space" -> "one\r\n two",
    "h1.server.obsolete-fold-tab" -> "one\r\n\ttwo",
    "h1.client.obsolete-fold-space" -> "one\r\n two",
    "h1.client.obsolete-fold-tab" -> "one\r\n\ttwo",
    "h1.client.obsolete-fold-multiple" -> "one\r\n two\r\n\tthree",
  )
  for {
    (id, raw) <- foldCases
    observation <- Vector(
      "normalized",
      "multiple-spaces",
      "raw-only",
      "duplicate-raw",
      "duplicate-lf",
      "duplicate-tab",
      "duplicate-wrong",
      "duplicate-normalized",
      "missing",
      "wrong-value",
    )
  } test(s"$id checks every exposed fold occurrence: $observation") {
    val normalized = raw.replaceAll("\\r\\n[ \\t]+", " ")
    val values = observation match {
      case "normalized" => Vector(normalized)
      case "multiple-spaces" => Vector(normalized.replace(" ", "   "))
      case "raw-only" => Vector(raw)
      case "duplicate-raw" => Vector(normalized, raw)
      case "duplicate-lf" => Vector(normalized, raw.replace("\r", ""))
      case "duplicate-tab" => Vector(normalized, normalized.replace(" ", "\t"))
      case "duplicate-wrong" => Vector(normalized, "wrong")
      case "duplicate-normalized" => Vector(normalized, normalized.replace(" ", "  "))
      case "missing" => Vector.empty
      case _ => Vector("wrong")
    }
    val headers = values.map("X-Wire" -> _)
    val run =
      if (id.startsWith("h1.server."))
        runServer(id) { (peer, publish) =>
          peer.readUntil(Http1Wire.ascii("\r\n\r\n")).flatMap { head =>
            IO(assert(head.toArray.iterator.map(b => (b & 0xff).toChar).mkString.contains(raw))) *>
              publish(RequestData(headers = headers)) *>
              peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
          }
        }
      else
        runClient(id) { (peer, request) =>
          for {
            _ <- sendRequest(peer, request)
            head <- peer.readUntil(Http1Wire.ascii("\r\n\r\n"))
            _ <- IO(
              assert(head.toArray.iterator.map(b => (b & 0xff).toChar).mkString.contains(raw))
            )
            body <- peer.readExact(2)
          } yield response(200, body).copy(headers = headers)
        }
    if (Set("normalized", "multiple-spaces", "duplicate-normalized")(observation)) run
    else run.intercept[ProtocolFailure].map(e => assert(e.getMessage.contains("fold")))
  }

  for ((id, _) <- foldCases if id.startsWith("h1.server."))
    test(s"$id still permits 400 without application dispatch") {
      runServer(id) { (peer, _) =>
        peer.readUntil(Http1Wire.ascii("\r\n\r\n")) *>
          peer.writeAscii("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n")
      }
    }

  for (
    (location, legal) <- Vector(
      "/two%20words" -> true,
      "two%20words" -> true,
      "./two%20words" -> true,
      "http://wire.test/two%20words" -> true,
      "HTTP://WIRE.TEST:80/two%20words" -> true,
      "http://wire.test:/two%20words" -> true,
      "//wire.test/two%20words" -> true,
      "//wire.test:/two%20words" -> true,
      "http://%77ire.test:/two%20words" -> true,
      "/%74wo%20%77ords" -> true,
      "http://%77ire.test/two%20words" -> true,
      "/unused/../two%20words" -> true,
      "/%E9/../two%20words" -> true,
      "/%e9/../two%20words" -> true,
      "/\u00e9/../two%20words" -> false,
      "/unused//../../two%20words" -> true,
      "" -> false,
      "http://wire.test" -> false,
      "http:/two%20words" -> false,
      "///two%20words" -> false,
      "/unrelated%20resource" -> false,
      "/unrelated" -> false,
      "/two words" -> false,
      "/two%2520words" -> false,
      "/two+words" -> false,
      "/%2Ftwo%20words" -> false,
      "/unused//../two%20words" -> false,
      "/two%20words/" -> false,
      "/two%20words?other" -> false,
      "/two%20words#other" -> false,
      "http://other.test/two%20words" -> false,
      "https://wire.test/two%20words" -> false,
      "http://wire.test:81/two%20words" -> false,
      "http://wire.test::/two%20words" -> false,
      "http://user@wire.test/two%20words" -> false,
      "/two%20words%" -> false,
    )
  ) test(s"encoded-target redirect checks resolved components: $location") {
    val run = runServer("h1.server.space-in-request-target") { (peer, _) =>
      peer.readUntil(Http1Wire.ascii("\r\n\r\n")).flatMap { head =>
        IO(
          assert(
            head.toArray.iterator
              .map(b => (b & 0xff).toChar)
              .mkString
              .startsWith("GET /two words HTTP/1.1\r\n")
          )
        ) *>
          peer.write(
            ByteVector.view(
              ("HTTP/1.1 301 Moved Permanently\r\nLocation: " + location +
                "\r\nContent-Length: 0\r\n\r\n").map(_.toByte).toArray
            )
          )
      }
    }
    if (legal) run
    else run.intercept[ProtocolFailure].map(e => assert(e.getMessage.contains("redirect")))
  }

  test("invalid-target oracle retains the 400 alternative") {
    runServer("h1.server.space-in-request-target") { (peer, _) =>
      peer.readUntil(Http1Wire.ascii("\r\n\r\n")) *>
        peer.writeAscii("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n")
    }
  }

  for {
    role <- Vector("server", "client")
    preserve <- Vector(false, true)
  } test(s"$role opaque-content recommendation retains exact octets: preserve=$preserve") {
    val id = s"h1.$role.field-obs-text-octets"
    val value = if (preserve) "\u0080\u00ff\u00c3(" else "\ufffd\ufffd\ufffd("
    val headers = Vector("X-Wire" -> value, "X-After" -> "intact")
    val run =
      if (role == "server")
        runServer(id) { (peer, publish) =>
          peer.readUntil(Http1Wire.ascii("\r\n\r\n")) *>
            publish(RequestData(path = "/octets", headers = headers)) *>
            peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
        }
      else
        runClient(id) { (peer, request) =>
          sendRequest(peer, request) *> peer.readUntil(Http1Wire.ascii("\r\n\r\n")) *>
            peer.readExact(2).map(body => response(200, body).copy(headers = headers))
        }
    val checkResult = if (preserve) run else run.intercept[ProtocolFailure].void
    checkResult *> IO {
      assertEquals(
        selected(id).references.map(r => (r.number, r.section, r.strength)),
        Vector((9110, "5.5", "SHOULD")),
      )
    }
  }

  for (
    (path, legal) <- Vector(
      "/a%20b/%2F?q=%23%3F" -> true,
      "/a%20b/%2f?q=%23%3f" -> true,
      "/a%20b/%2f?q=%23%3F" -> true,
      "/a%20b//?q=%23%3F" -> false,
      "/a%20b/%2F?q=#?" -> false,
      "/A%20b/%2F?q=%23%3F" -> false,
    )
  ) test(s"origin percent escape equivalence: $path") {
    val run = runServer("h1.server.origin-percent-encoded") { (peer, publish) =>
      Http1Wire.readRequest(peer).flatMap { request =>
        IO(assertEquals(request.target, "/a%20b/%2F?q=%23%3F")) *>
          publish(RequestData(path = path)) *>
          peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
      }
    }
    if (legal) run else run.intercept[ProtocolFailure].void
  }

  for (second <- Vector("/unrelated", "/two%20words"))
    test(s"encoded-target redirect requires one Location: $second") {
      runServer("h1.server.space-in-request-target") { (peer, _) =>
        peer.readUntil(Http1Wire.crlf ++ Http1Wire.crlf) *>
          peer.write(
            Http1Wire.message(
              "HTTP/1.1 301 Moved Permanently",
              Vector("Location" -> "/two%20words", "Location" -> second, "Content-Length" -> "0"),
              ByteVector.empty,
            )
          )
      }.intercept[ProtocolFailure].void
    }

  for {
    role <- Vector("client", "server")
    streamed <- Vector(false, true)
    variant <- Vector("single", "leading-zero-ows", "equal-list", "duplicate")
  } test(s"generated sender Content-Length $role streamed=$streamed $variant") {
    val suffix = if (streamed) {
      if (role == "client") "streamed-binary-content" else "streamed-content"
    } else if (role == "client") "known-binary-length"
    else "known-binary-content"
    val body = ByteVector.fromValidHex(
      if (!streamed) "00ff80" else if (role == "client") "00ff80fe" else "6100ff6263"
    )
    val size = body.size.toString
    val fields = variant match {
      case "single" => Vector("Content-Length" -> size)
      case "leading-zero-ows" => Vector("Content-Length" -> s" \t00$size \t")
      case "equal-list" => Vector("Content-Length" -> s"$size, $size")
      case _ => Vector("Content-Length" -> size, "Content-Length" -> size)
    }
    val id = s"h1.$role.sender-$suffix"
    val run =
      if (role == "client")
        runClient(id) { (peer, request) =>
          peer.write(
            Http1Wire.message(s"${request.method} ${request.path} HTTP/1.1", fields, body)
          ) *>
            Http1Wire.readFinalResponse(peer).map(r => response(r.status, r.body))
        }
      else
        runServerBytes(id, Http1Wire.message("HTTP/1.1 200 OK", fields, body), IO.pure(None))
    if (variant == "single" || variant == "leading-zero-ows") run
    else
      run
        .intercept[WireDecodingFailure]
        .map(e =>
          assert(e.getMessage.contains("generated content-length must be one decimal field"))
        )
  }

  for {
    streamed <- Vector(false, true)
    variant <- Vector("plain", "gzip", "gzip-bad-chunk", "gzip-bad-trailer")
  } test(s"generated sender transfer content streamed=$streamed $variant") {
    val suffix = if (streamed) "streamed-binary-content" else "known-binary-length"
    val plain = ByteVector.fromValidHex(if (streamed) "00ff80fe" else "00ff80")
    // Independently decoded with Python gzip, including the gzip CRC and length checks.
    val gzip = ByteVector.fromValidHex(
      if (streamed) "1f8b08000000000000ff63f8dff00f00a1ac5afe04000000"
      else "1f8b08000000000000ff63f8df000040a7dd8103000000"
    )
    val body = if (variant == "plain") plain else gzip
    val ending = variant match {
      case "gzip-bad-chunk" => "XX0\r\n\r\n"
      case "gzip-bad-trailer" => "\r\n0\r\nContent-Length: 0\r\n\r\n"
      case _ => "\r\n0\r\n\r\n"
    }
    val encoded = Http1Wire.ascii(body.size.toHexString + "\r\n") ++ body ++ Http1Wire.ascii(ending)
    val run = runClient(s"h1.client.sender-$suffix") { (peer, request) =>
      peer.write(
        Http1Wire.message(
          s"${request.method} ${request.path} HTTP/1.1",
          Vector("Transfer-Encoding" -> (if (variant == "plain") "chunked" else "gzip, chunked")),
          encoded,
        )
      ) *> Http1Wire.readFinalResponse(peer).map(r => response(r.status, r.body))
    }
    variant match {
      case "plain" => run
      case "gzip" =>
        run
          .intercept[WireObservationLimit]
          .map(e =>
            assertEquals(
              e.getMessage,
              "cannot observe request content: unsupported transfer decoding: gzip; observation incomplete, scenario not fully exercised",
            )
          )
      case "gzip-bad-chunk" =>
        run
          .intercept[WireDecodingFailure]
          .map(e => assertEquals(e.getMessage, "missing chunk CRLF"))
      case _ =>
        run
          .intercept[WireDecodingFailure]
          .map(e => assertEquals(e.getMessage, "framing field in trailers"))
    }
  }

  for {
    streamed <- Vector(false, true)
    whitespace <- Vector("", " ", "\t")
    last <- Vector(false, true)
  } test(
    s"generated sender bare extension streamed=$streamed whitespace='$whitespace' last=$last"
  ) {
    val suffix = if (streamed) "streamed-binary-content" else "known-binary-length"
    val body = ByteVector.fromValidHex(if (streamed) "00ff80fe" else "00ff80")
    val extension = s";flag$whitespace"
    val encoded = Http1Wire.ascii(body.size.toHexString + (if (last) "" else extension) + "\r\n") ++
      body ++ Http1Wire.ascii("\r\n0" + (if (last) extension else "") + "\r\n\r\n")
    val run = runClient(s"h1.client.sender-$suffix") { (peer, request) =>
      peer.write(
        Http1Wire.message(
          s"${request.method} ${request.path} HTTP/1.1",
          Vector("Transfer-Encoding" -> "chunked"),
          encoded,
        )
      ) *> Http1Wire.readFinalResponse(peer).map(r => response(r.status, r.body))
    }
    if (whitespace.isEmpty) run
    else
      run
        .intercept[WireDecodingFailure]
        .map(e => assertEquals(e.getMessage, "trailing whitespace after bare chunk extension"))
  }

  for {
    suffix <- Vector("known-binary-content", "streamed-content")
    transfer <- Vector(
      "",
      " \t",
      ",",
      "chunked,",
      ",chunked",
      "chunked,,chunked",
      "gzip",
      "chunked",
    )
  } test(s"sender $suffix validates zero or negotiated transfer codings: '$transfer'") {
    val body =
      if (suffix == "known-binary-content") ByteVector.fromValidHex("00ff80")
      else ByteVector.fromValidHex("6100ff6263")
    val encoded =
      if (transfer.contains("chunked"))
        Http1Wire.ascii(body.size.toHexString + "\r\n") ++ body ++ Http1Wire.ascii("\r\n0\r\n\r\n")
      else body
    val run = runServer(s"h1.server.sender-$suffix") { (peer, _) =>
      Http1Wire.readRequest(peer) *>
        peer.write(
          Http1Wire.message(
            "HTTP/1.1 200 OK",
            Vector("Transfer-Encoding" -> transfer, "Connection" -> "close"),
            encoded,
          )
        ) *>
        peer.endOfOutput
    }
    if (transfer.trim.isEmpty || transfer == "chunked") run
    else
      run.attempt.map(result =>
        assert(
          result.swap
            .exists(e => e.isInstanceOf[WireDecodingFailure] || e.isInstanceOf[ProtocolFailure]),
          result.toString,
        )
      )
  }

  for {
    framing <- Vector("content-length", "chunk-size")
    complete <- Vector(false, true)
    error <- Vector(false, true)
  } test(s"$framing overflow checks completed publication: complete=$complete/error=$error") {
    val run = runServer(s"h1.server.$framing-prevents-integer-wraparound") { (peer, publish) =>
      peer.readToEnd().flatMap { stimulus =>
        val framingBytes =
          if (framing == "content-length") "Content-Length: 18446744073709551619\r\n\r\nabc"
          else "Transfer-Encoding: chunked\r\n\r\n10000000000000003\r\nabc\r\n0\r\n\r\n"
        IO(
          assertEquals(
            stimulus,
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\n" + framingBytes
            ),
          )
        ) *>
          (if (complete)
             publish(RequestData(method = "POST", path = "/framing", body = Http1Wire.ascii("abc")))
           else IO.unit) *>
          (if (error) peer.writeAscii("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n")
           else IO.unit) *>
          peer.endOfOutput
      }
    }
    if (complete) run.intercept[ProtocolFailure].map(e => assert(e.getMessage.contains("complete")))
    else run
  }

  for {
    suffix <- Vector(
      "request-head",
      "fixed-length-empty",
      "fixed-length-prefix",
      "chunk-size",
      "chunk-data",
      "chunk-delimiter",
      "terminal-chunk",
      "trailer-terminator",
      "trailer-field",
    )
    field <- Vector(
      "X-Valid: one\t two",
      "INVALID FIELD WITHOUT COLON",
      "Bad Name: value",
      "X-Bad: a\u0000b",
      "X-Bad: a\u007fb",
    )
    informational <- Vector(false, true)
  } test(
    s"incomplete $suffix validates complete error fields: '$field'/informational=$informational"
  ) {
    val run = runServer(s"h1.server.incomplete-$suffix") { (peer, publish) =>
      Http1Wire.readRequest(peer).flatMap { ready =>
        publish(RequestData(path = ready.target, body = ready.body)) *>
          peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok")
      } *> peer.readToEnd() *>
        peer.writeAscii(
          if (informational)
            s"HTTP/1.1 100 Continue\r\n$field\r\n\r\nHTTP/1.1 400 Bad Request\r\nContent-Length: 99\r\n\r\nshort"
          else s"HTTP/1.1 400 Bad Request\r\n$field\r\nContent-Length: 99\r\n\r\nshort"
        ) *>
        peer.endOfOutput
    }
    if (field.startsWith("X-Valid")) run
    else run.intercept[WireDecodingFailure].map(e => assert(e.getMessage.contains("header field")))
  }

  private val recoverySuffixes = Vector(
    "explicit-plus-size",
    "hex-prefix-size",
    "bare-lf-size-terminator",
    "bare-lf-data-terminator",
  )

  for {
    suffix <- recoverySuffixes
    recovered <- Vector("abc", "repaired", "")
  } {
    test(s"chunk $suffix client permits unspecified recovered content ($recovered)") {
      val run = runClient(s"h1.client.reject-chunk-$suffix") { (peer, request) =>
        sendRequest(peer, request) *> peer
          .readUntil(Http1Wire.ascii("0\r\n\r\n"))
          .as(response(200, Http1Wire.ascii(recovered)))
      }
      run
    }
    test(s"chunk $suffix server permits unspecified recovered content ($recovered)") {
      val run = runServer(s"h1.server.reject-chunk-$suffix") { (peer, publish) =>
        peer.readUntil(Http1Wire.ascii("0\r\n\r\n")) *>
          publish(
            RequestData(
              method = "POST",
              path = "/framing",
              body = Http1Wire.ascii(recovered),
            )
          ) *>
          peer.write(
            Http1Wire.message(
              "HTTP/1.1 200 OK",
              Vector("Content-Length" -> recovered.length.toString),
              Http1Wire.ascii(recovered),
            )
          )
      }
      run
    }
  }

  for (
    trailers <- Vector(
      Vector.empty[(String, String)],
      Vector("X-Fence" -> "first", "X-Fence" -> "second"),
      Vector("X-Fence" -> "second", "X-Fence" -> "first"),
    )
  )
    test(s"unadvertised trailers may be discarded or retained in order: $trailers") {
      val run = runClient("h1.client.interaction-chunk-trailer-next-response") { (peer, request) =>
        sendRequest(peer, request) *> Http1Wire
          .readFinalResponse(peer)
          .map(r =>
            response(r.status, r.body, if (request.path == "/first") trailers else Vector.empty)
          )
      }
      if (trailers.headOption.exists(_._2 == "second")) run.intercept[ProtocolFailure].void else run
    }

  private val informational = Vector(
    "h1.client.informational-does-not-use-close-delimitation",
    "h1.client.multiple-informational-boundaries",
    "h1.client.informational-before-final-response",
  )
  for {
    id <- informational
    exposeInterim <- Vector(false, true)
    corrupt <- Vector(false, true)
  }
    test(
      s"$id checks visible framing without imposing final-only API (interim=$exposeInterim, corrupt=$corrupt)"
    ) {
      val run = runClient(id) { (peer, request) =>
        sendRequest(peer, request) *>
          (if (exposeInterim) Http1Wire.readResponse(peer) else Http1Wire.readFinalResponse(peer))
            .map(r => response(r.status, if (corrupt) Http1Wire.ascii("wrong") else r.body))
      }
      if (corrupt) run.intercept[ProtocolFailure].void else run
    }

  private val policyCases = Vector(
    "h1.server.complete-chunked-half-close",
    "h1.server.pipeline-chunked-then-fixed-length",
    "h1.server.pipeline-fixed-length-then-chunked",
    "h1.server.drain-unread-chunked-or-close",
    "h1.server.interaction-chunk-body-request-line",
  )
  for (id <- policyCases) test(s"$id permits a length-required policy before chunk decoding") {
    runServer(id) { (peer, publish) =>
      val first =
        if (id.endsWith("fixed-length-then-chunked"))
          Http1Wire
            .readRequest(peer)
            .flatMap(r =>
              publish(RequestData(method = r.method, path = r.target, body = r.body)) *>
                peer.write(
                  Http1Wire.message("HTTP/1.1 200 OK", Vector("Content-Length" -> "3"), r.body)
                )
            )
        else IO.unit
      first *> peer.readUntil(Http1Wire.crlf ++ Http1Wire.crlf) *>
        peer.writeAscii(
          "HTTP/1.1 411 Length Required\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        ) *>
        peer.endOfOutput
    }
  }

  for {
    id <- policyCases
    corrupt <- Vector(false, true)
  }
    test(s"$id still verifies accepted request boundaries (corrupt=$corrupt)") {
      val run = runServer(id) { (peer, publish) =>
        def respond: IO[Unit] = Http1Wire.readRequest(peer).flatMap { request =>
          val body =
            if (id.endsWith("drain-unread-chunked-or-close")) Http1Wire.ascii("accepted")
            else request.body
          publish(
            RequestData(method = request.method, path = request.target, body = request.body)
          ) *>
            peer.write(
              Http1Wire.message(
                "HTTP/1.1 200 OK",
                Vector("Content-Length" -> body.size.toString),
                if (corrupt) ByteVector.fill(body.size)(0.toByte) else body,
              )
            )
        }
        respond *> (if (id.endsWith("complete-chunked-half-close")) IO.unit
                    else respond) *> peer.endOfOutput
      }
      if (corrupt) run.intercept[ProtocolFailure].void else run
    }

  for (suffix <- recoverySuffixes) {
    test(s"chunk $suffix permits native client rejection") {
      runClient(s"h1.client.reject-chunk-$suffix") { (peer, request) =>
        sendRequest(peer, request) *> peer.readUntil(Http1Wire.ascii("0\r\n\r\n")) *>
          IO.raiseError(new IllegalArgumentException("native parser rejection"))
      }
    }
    for (rejectWithResponse <- Vector(false, true))
      test(s"chunk $suffix permits server rejection (response=$rejectWithResponse)") {
        runServer(s"h1.server.reject-chunk-$suffix") { (peer, _) =>
          peer.readUntil(Http1Wire.ascii("0\r\n\r\n")) *>
            (if (rejectWithResponse)
               peer.writeAscii("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n")
             else peer.endOfOutput)
        }
      }
  }

  test("chunk recovery does not accept a truncated target response head as rejection") {
    runServer("h1.server.reject-chunk-explicit-plus-size") { (peer, _) =>
      peer.readUntil(Http1Wire.ascii("0\r\n\r\n")) *>
        peer.writeAscii("HTTP/1.1 200 OK\r\n") *> peer.endOfOutput
    }.intercept[WireDecodingFailure].void
  }

  for (misassociate <- Vector(false, true))
    test(
      s"interim interaction still requires a real next request and its own response (misassociate=$misassociate)"
    ) {
      val run =
        runClient("h1.client.interaction-interim-next-request-association") { (peer, request) =>
          if (request.path == "/first")
            sendRequest(peer, request) *>
              Http1Wire.readResponse(peer).map(r => response(r.status, r.body))
          else
            sendRequest(peer, request) *> Http1Wire.readFinalResponse(peer).flatMap { previous =>
              Http1Wire
                .readFinalResponse(peer)
                .map(next =>
                  if (misassociate) response(previous.status, previous.body)
                  else response(next.status, next.body)
                )
            }
        }
      if (misassociate) run.intercept[ProtocolFailure].void else run
    }

  private val failureCases = Vector(
    "h1.client.reject-chunk-explicit-plus-size",
    "h1.client.incomplete-fixed-length-prefix",
    "h1.client.field-nul",
    "h1.client.content-length-zero",
    "h1.client.interaction-head-length-next-response",
    "h1.client.obsolete-fold-space",
  )
  for {
    id <- failureCases
    finalizer <- Vector(false, true)
    suppressed <- Vector(false, true)
  }
    test(s"$id preserves adapter failures (finalizer=$finalizer, suppressed=$suppressed)") {
      val failure = new AdapterFailure("fixture failure", new IllegalStateException("injected"))
      val wrapper = if (suppressed) {
        val assertion = new ProtocolFailure("outer assertion")
        assertion.addSuppressed(failure)
        assertion
      } else new RuntimeException("native operation wrapper", failure)
      val run = runClient(id, if (finalizer) IO.raiseError(wrapper) else IO.unit) { (peer, request) =>
        sendRequest(peer, request) *> peer.readUntil(Http1Wire.crlf ++ Http1Wire.crlf) *>
          (if (finalizer)
             IO.pure(
               if (id == "h1.client.obsolete-fold-space")
                 response(200, Http1Wire.ascii("ok")).copy(headers = Vector("X-Wire" -> "one two"))
               else response(200, ByteVector.empty)
             )
           else IO.raiseError(wrapper))
      }
      run.intercept[AdapterFailure].flatMap(e => IO(assert(e eq failure)))
    }

  for (id <- failureCases.take(3)) test(s"$id never accepts an unrelated assertion as rejection") {
    val failure = new AssertionError("injected assertion")
    runClient(id) { (peer, request) =>
      sendRequest(peer, request) *> peer.readUntil(Http1Wire.crlf ++ Http1Wire.crlf) *> IO
        .raiseError(failure)
    }.intercept[AssertionError].flatMap(e => IO(assert(e eq failure)))
  }

  for (id <- failureCases) test(s"$id preserves an independent received-wire failure") {
    val failure = new WireDecodingFailure("injected malformed wire")
    runClient(id) { (peer, request) =>
      sendRequest(peer, request) *> peer.readUntil(Http1Wire.crlf ++ Http1Wire.crlf) *> IO
        .raiseError(failure)
    }.intercept[WireDecodingFailure].flatMap(e => IO(assert(e eq failure)))
  }

  private val remainingRecovery = Vector(
    "nonhex-size",
    "negative-size",
    "empty-size",
    "wrong-data-terminator",
  )
  for (suffix <- remainingRecovery) {
    test(s"chunk $suffix permits recipient recovery without prescribing a repair algorithm") {
      runClient(s"h1.client.reject-chunk-$suffix") { (peer, request) =>
        sendRequest(peer, request) *> peer.readUntil(Http1Wire.ascii("0\r\n\r\n")) *>
          IO.pure(response(200, Http1Wire.ascii("recovered")))
      }
    }
    test(s"chunk $suffix server may recover usable content") {
      runServer(s"h1.server.reject-chunk-$suffix") { (peer, publish) =>
        peer.readUntil(Http1Wire.ascii("0\r\n\r\n")) *>
          publish(
            RequestData(method = "POST", path = "/framing", body = Http1Wire.ascii("recovered"))
          ) *>
          peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 9\r\n\r\nrecovered")
      }
    }
  }

  for {
    suffix <- remainingRecovery
    reject <- Vector(false, true)
  } test(s"chunk $suffix still permits rejection and rejects unrelated status (reject=$reject)") {
    val run = runClient(s"h1.client.reject-chunk-$suffix") { (peer, request) =>
      sendRequest(peer, request) *> peer.readUntil(Http1Wire.ascii("0\r\n\r\n")) *>
        (if (reject) IO.raiseError(new IllegalArgumentException("native parser rejection"))
         else IO.pure(response(201, Http1Wire.ascii("recovered"))))
    }
    if (reject) run else run.intercept[ProtocolFailure].void
  }

  for (
    id <- Vector(
      "h1.server.drain-unread-chunked-or-close",
      "h1.server.interaction-chunk-body-request-line",
    )
  )
    test(s"$id permits 411 with drainage and a correctly delimited following request") {
      runServer(id) { (peer, publish) =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 411 Length Required\r\nContent-Length: 0\r\n\r\n"
        ) *>
          Http1Wire.readRequest(peer).flatMap { request =>
            val body =
              if (id.endsWith("drain-unread-chunked-or-close")) Http1Wire.ascii("accepted")
              else request.body
            publish(RequestData(path = request.target, body = request.body)) *>
              peer.write(
                Http1Wire
                  .message("HTTP/1.1 200 OK", Vector("Content-Length" -> body.size.toString), body)
              )
          } *> peer.endOfOutput
      }
    }

  for (
    id <- Vector(
      "h1.server.drain-unread-chunked-or-close",
      "h1.server.interaction-chunk-body-request-line",
    )
  )
    test(s"$id does not let 411 hide a body dispatched as a request") {
      runServer(id) { (peer, publish) =>
        peer.readUntil(Http1Wire.crlf ++ Http1Wire.crlf) *>
          publish(RequestData(path = "/forbidden")) *>
          peer.writeAscii(
            "HTTP/1.1 411 Length Required\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
          ) *>
          peer.endOfOutput
      }.intercept[ProtocolFailure].void
    }

  for {
    suffix <- Vector(
      "fixed-length-requests",
      "chunked-then-fixed-length",
      "fixed-length-then-chunked",
    )
    corrupt <- Vector(false, true)
  } test(
    s"pipeline $suffix checks first response before reporting lost persistence (corrupt=$corrupt)"
  ) {
    val run = runServer(s"h1.server.pipeline-$suffix") { (peer, publish) =>
      Http1Wire.readRequest(peer).flatMap { request =>
        publish(RequestData(path = request.target, body = request.body)) *>
          peer.writeAscii(
            "HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\n" +
              (if (corrupt) "bad" else "one")
          ) *> peer.endOfOutput
      }
    }
    if (corrupt) run.intercept[ProtocolFailure].void
    else
      run
        .intercept[IllegalStateException]
        .map(e => assert(e.getMessage.contains("coverage prerequisite")))
  }

  test("early upload crosses write coalescing before the peer rejects the incomplete body") {
    coalescedUpload().map { observed =>
      assertEquals(observed.result, Right(()))
      assertUploadStopped(observed)
      assert(observed.writes.nonEmpty)
      assert(observed.writes.forall(_ >= 32 * 1024), observed.writes.toString)
      assert(observed.events.indexOf("response:413") < observed.events.indexOf("body-finalized"))
      assert(observed.events.indexOf("body-finalized") < observed.events.indexOf("output-ended"))
      assert(
        observed.events.indexOf("body-finalized") < observed.events.indexOf("response-released")
      )
    }
  }

  for (octet <- Vector(0x80, 0xff))
    test(s"early upload accepts legal User-Agent comment octet $octet before its error response") {
      val field = Http1Wire.ascii("User-Agent: wire (") ++ ByteVector(octet.toByte) ++
        Http1Wire.ascii(")\r\n")
      coalescedUpload(rawFields = field).map { observed =>
        assertEquals(observed.result, Right(()))
        assertUploadStopped(observed)
        assert(observed.writes.nonEmpty)
        assert(observed.writes.forall(_ >= 32 * 1024), observed.writes.toString)
        assert(observed.events.indexOf("response:413") < observed.events.indexOf("body-finalized"))
        assert(observed.events.indexOf("body-finalized") < observed.events.indexOf("output-ended"))
        assert(
          observed.events.indexOf("body-finalized") < observed.events.indexOf("response-released")
        )
      }
    }

  test("early upload checks the exact coalesced prefix before sending its error") {
    coalescedUpload(corruptPrefix = true).map { observed =>
      assert(observed.result.swap.exists(_.isInstanceOf[ProtocolFailure]), observed.result.toString)
      assertUploadStopped(observed)
      assert(!observed.events.exists(_.startsWith("response:")), observed.events.toString)
    }
  }

  test("early upload still rejects the wrong application response status") {
    coalescedUpload(status = 200).map { observed =>
      assert(observed.result.swap.exists(_.isInstanceOf[ProtocolFailure]), observed.result.toString)
      assertUploadStopped(observed)
      assert(observed.events.contains("response:413"), observed.events.toString)
    }
  }

  for (kind <- Vector("adapter", "timeout"))
    test(s"early upload preserves $kind failure after the peer response") {
      val failure =
        if (kind == "adapter")
          new AdapterFailure("upload fixture", new RuntimeException("injected"))
        else new java.util.concurrent.TimeoutException("injected native timeout")
      coalescedUpload(responseFailure = Some(failure)).map { observed =>
        assertEquals(observed.result, Left(failure))
        assertUploadStopped(observed)
        assert(observed.events.contains("response:413"), observed.events.toString)
      }
    }

  test("early upload prioritizes canceled body cleanup over a response failure") {
    val cleanup = new AdapterFailure("upload cleanup", new RuntimeException("injected cleanup"))
    val primary = new java.util.concurrent.TimeoutException("injected response timeout")
    coalescedUpload(responseFailure = Some(primary), cleanupFailure = Some(cleanup)).map {
      observed =>
        assertUploadStopped(observed)
        observed.result match {
          case Left(error: AdapterFailure) =>
            assert((error eq cleanup) || (error.getCause eq cleanup), error.toString)
            assert(primary.getSuppressed.exists(_ eq cleanup), primary.toString)
          case other => fail(s"expected retained cleanup failure, got $other")
        }
    }
  }

  private val validClientCases = Vector(
    "h1.client.generated-origin-form",
    "h1.client.generated-empty-path",
    "h1.client.generated-asterisk-form",
    "h1.client.generated-method-case",
    "h1.client.generated-method-token",
    "h1.client.generated-host",
    "h1.client.generated-field-token",
    "h1.client.generated-repeated-field-order",
    "h1.client.generated-uri-8000-octets",
    "h1.client.empty-reason-phrase",
    "h1.client.ignore-reason-phrase",
    "h1.client.reason-phrase-horizontal-tab",
    "h1.client.reason-phrase-obs-text",
    "h1.client.unrecognized-status-code",
    "h1.client.field-no-ows",
    "h1.client.field-boundary-ows",
    "h1.client.field-empty-value",
    "h1.client.field-only-ows",
    "h1.client.field-interior-whitespace",
    "h1.client.field-name-token-punctuation",
    "h1.client.field-obs-text-octets",
    "h1.client.repeated-field-order",
    "h1.client.complete-fixed-length-at-eof",
    "h1.client.complete-zero-length-at-eof",
    "h1.client.complete-chunked-at-eof",
    "h1.client.close-delimited-empty-response",
    "h1.client.http10-close-delimited-response",
    "h1.client.close-delimited-binary-response",
    "h1.client.response-close-fixed-length",
    "h1.client.response-close-chunked",
    "h1.client.response-close-case-insensitive",
    "h1.client.response-close-option-list",
    "h1.client.response-close-repeated-field",
    "h1.client.request-close-final-response",
    "h1.client.informational-before-final-response",
    "h1.client.sequential-fixed-length-responses",
    "h1.client.sequential-chunked-then-fixed-length",
    "h1.client.error-response-during-upload",
  )
  for (id <- validClientCases) test(s"$id classifies native rejection as conformance failure") {
    runClient(id) { (_, _) =>
      IO.raiseError(new IllegalArgumentException("native parser rejection"))
    }.intercept[ProtocolFailure].map(e => assert(e.getMessage.contains("target rejected")))
  }

  for {
    id <- Vector(
      "h1.client.generated-origin-form",
      "h1.client.complete-fixed-length-at-eof",
      "h1.client.error-response-during-upload",
    )
    kind <- Vector("adapter", "timeout", "assertion", "wire")
  } test(s"$id preserves $kind failure provenance") {
    val failure: Throwable = kind match {
      case "adapter" => new AdapterFailure("fixture", new IllegalArgumentException("bad fixture"))
      case "timeout" => new java.util.concurrent.TimeoutException("deadline")
      case "assertion" => new AssertionError("independent assertion")
      case _ => new WireDecodingFailure("independent wire assertion")
    }
    runClient(id)((_, _) => IO.raiseError(failure)).attempt.map {
      case Left(error) => assert((error eq failure) || (error.getCause eq failure))
      case Right(_) => fail("expected the original error")
    }
  }

  for (id <- Vector("h1.server.reject-chunk-explicit-plus-size", "h1.server.field-nul"))
    test(s"$id accepts informational prefix before error response") {
      runServer(id) { (peer, _) =>
        peer.readUntil(Http1Wire.crlf ++ Http1Wire.crlf) *>
          peer.writeAscii(
            "HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n"
          ) *>
          peer.endOfOutput
      }
    }

  for (
    reason <- Vector(
      ByteVector(0x80.toByte, 0xff.toByte),
      ByteVector(0.toByte),
      ByteVector(127.toByte),
    )
  )
    test(s"incomplete request reason phrase validates octets: ${reason.toHex}") {
      val run = runServer("h1.server.incomplete-fixed-length-prefix") { (peer, publish) =>
        Http1Wire.readRequest(peer).flatMap { ready =>
          publish(RequestData(path = ready.target, body = ready.body)) *>
            peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok")
        } *> peer.readToEnd() *> peer.write(
          Http1Wire.ascii("HTTP/1.1 400 ") ++ reason ++
            Http1Wire.ascii("\r\nContent-Length: 0\r\n\r\n")
        ) *> peer.endOfOutput
      }
      if (reason.size == 2) run else run.intercept[ProtocolFailure].void
    }

  for (extra <- Vector(false, true))
    test(s"recognized reset after complete response retains extra bytes (extra=$extra)") {
      val reset = Http1ResetFixture.reset
      WireCodecsSuite
        .peer(
          Http1Wire.ascii(
            "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n" + (if (extra) "x" else "")
          ),
          whenEmpty = IO.raiseError(reset),
        )
        .flatMap { case (peer, _) =>
          val run =
            Http1Wire.readFinalResponse(peer) *> Http1FramingCases.expectClosedAfterResponse(peer)
          assert(RuntimeInfo.isConnectionReset(reset))
          if (extra) run.intercept[ProtocolFailure].void else run
        }
    }

  test("closure observation preserves adapter provenance wrapping a reset") {
    val error = new AdapterFailure("fixture", Http1ResetFixture.reset)
    WireCodecsSuite.peer(ByteVector.empty, whenEmpty = IO.raiseError(error)).flatMap {
      case (peer, _) =>
        Http1FramingCases
          .expectClosedAfterResponse(peer)
          .intercept[AdapterFailure]
          .map(e => assert(e eq error))
    }
  }

  for {
    suffix <- Vector("fields", "list", "numeric-values")
    ending <- Vector("eof", "reset", "extra", "partial", "adapter", "unrelated")
  } test(s"equal Content-Length $suffix observes actual 400 closure branch ($ending)") {
    val reset = Http1ResetFixture.reset
    val failure = ending match {
      case "adapter" => new AdapterFailure("reset fixture", reset)
      case "unrelated" => new java.io.IOException("unrelated read failure")
      case _ => reset
    }
    val bytes = Http1Wire.ascii(
      "HTTP/1.1 400 Bad Request\r\nContent-Length: " + (if (ending == "partial") "1" else "0") +
        "\r\n\r\n" + (if (ending == "extra") "x" else "")
    )
    val run = runServerBytes(
      s"h1.server.equal-content-length-$suffix",
      bytes,
      if (ending == "eof") IO.pure(None) else IO.raiseError(failure),
    )
    ending match {
      case "eof" | "reset" => run
      case "extra" => run.intercept[ProtocolFailure].void
      case _ =>
        run.attempt.map {
          case Left(error) => assert(error eq failure)
          case Right(_) =>
            fail("incomplete or unrelated failure cannot prove complete response and closure")
        }
    }
  }

  private val serverInteractions = Vector(
    "h1.server.interaction-length-body-request-line",
    "h1.server.interaction-chunk-body-request-line",
    "h1.server.interaction-unframed-post-next-request",
  )
  for {
    id <- serverInteractions
    ending <- Vector("eof", "partial", "corrupt-first")
  } test(s"$id distinguishes lawful continuation loss from malformed output ($ending)") {
    val run = runServer(id) { (peer, publish) =>
      Http1Wire.readRequest(peer).flatMap { request =>
        val body = if (ending == "corrupt-first") Http1Wire.ascii("wrong") else request.body
        publish(RequestData(path = request.target, body = request.body)) *>
          peer.write(
            Http1Wire
              .message("HTTP/1.1 200 OK", Vector("Content-Length" -> body.size.toString), body)
          ) *>
          (if (ending == "partial") peer.writeAscii("HTTP/1.1 200") else IO.unit) *>
          peer.endOfOutput
      }
    }
    if (ending == "eof")
      run
        .intercept[IllegalStateException]
        .map(e => assert(e.getMessage.contains("coverage prerequisite")))
    else run.intercept[AssertionError].void
  }

  for (adapter <- Vector(false, true))
    test(
      s"interaction reset between messages preserves closure or adapter provenance (adapter=$adapter)"
    ) {
      val reset = Http1ResetFixture.reset
      val error = if (adapter) new AdapterFailure("closure fixture", reset) else reset
      val run = runServerBytes(
        "h1.server.interaction-unframed-post-next-request",
        Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"),
        IO.raiseError(error),
        IO.pure(RequestData(path = "/first")),
      )
      if (adapter) run.intercept[AdapterFailure].map(actual => assert(actual eq error))
      else
        run
          .intercept[IllegalStateException]
          .map(e => assert(e.getMessage.contains("coverage prerequisite")))
    }

  private val clientInteractions = Vector(
    "h1.client.interaction-head-length-next-response",
    "h1.client.interaction-head-chunked-next-response",
    "h1.client.interaction-not-modified-next-response",
    "h1.client.interaction-no-content-next-response",
    "h1.client.interaction-length-body-status-line",
    "h1.client.interaction-chunk-trailer-next-response",
    "h1.client.interaction-interim-next-request-association",
  )
  for {
    id <- clientInteractions
    partial <- Vector(false, true)
    nativeFailure <- Vector(false, true)
  } test(
    s"$id distinguishes a closed client from a truncated next request (partial=$partial, nativeFailure=$nativeFailure)"
  ) {
    val run = runClient(id) { (peer, request) =>
      if (request.path == "/first")
        sendRequest(peer, request) *> Http1Wire
          .readFinalResponse(peer, request.method)
          .map(r => response(r.status, r.body, r.trailers))
      else
        (if (partial) peer.writeAscii("GET /second") else IO.unit) *> peer.endOfOutput *>
          (if (nativeFailure) IO.raiseError(new IllegalStateException("native connection closed"))
           else IO.never)
    }
    if (partial) run.intercept[WireDecodingFailure].void
    else
      run
        .intercept[IllegalStateException]
        .map(e => assert(e.getMessage.contains("coverage prerequisite")))
  }

  for {
    id <- Vector(clientInteractions.head, clientInteractions.last)
    kind <- Vector("adapter", "limit", "timeout", "assertion")
  } test(s"$id preserves second-operation $kind without awaiting a next request") {
    val error: Throwable = kind match {
      case "adapter" => new AdapterFailure("second exchange", new IllegalStateException("fixture"))
      case "limit" => new WireObservationLimit("second exchange")
      case "timeout" => new java.util.concurrent.TimeoutException("second exchange")
      case _ => new AssertionError("second exchange")
    }
    runClient(id) { (peer, request) =>
      if (request.path == "/first")
        sendRequest(peer, request) *> Http1Wire
          .readFinalResponse(peer, request.method)
          .map(r => response(r.status, r.body, r.trailers))
      else IO.raiseError(error)
    }.attempt.map {
      case Left(actual) =>
        assert(
          actual eq error,
          s"expected original ${error.getClass.getName}: ${error.getMessage}; " +
            s"actual ${actual.getClass.getName}: ${actual.getMessage}; " +
            s"cause=${Option(actual.getCause)}; suppressed=${actual.getSuppressed.toVector}",
        )
      case Right(_) => fail("second-operation infrastructure or assertion failure was lost")
    }
  }

  test("concurrent interim/next-request cases retain the exact second-operation failure") {
    (1 to 128).toVector
      .parTraverseN(16) { _ =>
        val error = new WireObservationLimit("second exchange")
        runClient("h1.client.interaction-interim-next-request-association") { (peer, request) =>
          if (request.path == "/first")
            sendRequest(peer, request) *> Http1Wire
              .readFinalResponse(peer, request.method)
              .map(r => response(r.status, r.body, r.trailers))
          else IO.raiseError(error)
        }.attempt.map {
          case Left(actual) => assert(actual eq error)
          case Right(_) => fail("second-operation observation limit was lost")
        }
      }
      .void
  }

  for (id <- clientInteractions)
    test(s"$id still rejects native failure after observing the next wire request") {
      runClient(id) { (peer, request) =>
        sendRequest(peer, request) *> Http1Wire.readFinalResponse(peer, request.method).flatMap { r =>
          if (request.path == "/first") IO.pure(response(r.status, r.body, r.trailers))
          else
            IO.raiseError(
              new IllegalArgumentException("native parser rejected the second response")
            )
        }
      }.intercept[ProtocolFailure].void
    }

  for (malformed <- Vector(false, true))
    test(
      s"interaction header observation distinguishes legal large fields from invalid syntax ($malformed)"
    ) {
      val run = runServer(serverInteractions.head) { (peer, _) =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\n" + (if (malformed) "Bad Field: value"
                                   else
                                     "X-Extension: " + ("a" * 8193)) + "\r\nContent-Length: 0\r\n\r\n"
        ) *> peer.endOfOutput
      }
      if (malformed) run.intercept[WireDecodingFailure].void
      else run.intercept[WireObservationLimit].void
    }

  for (kind <- Vector("body", "interim", "malformed"))
    test(s"incomplete request error observation retains limit versus violation category ($kind)") {
      val run = runServer("h1.server.incomplete-fixed-length-prefix") { (peer, publish) =>
        Http1Wire.readRequest(peer).flatMap { ready =>
          publish(RequestData(path = ready.target, body = ready.body)) *>
            peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok")
        } *> peer.readToEnd() *> peer.writeAscii(
          if (kind == "body")
            "HTTP/1.1 400 Bad Request\r\nContent-Length: 65536\r\n\r\n" + ("a" * 65536)
          else if (kind == "interim")
            ("HTTP/1.1 100 Continue\r\n\r\n" * 17) + "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n"
          else "HTTP/1.1 100 Bad\u0000Reason\r\n\r\n"
        ) *> peer.endOfOutput
      }
      if (kind == "malformed") run.intercept[ProtocolFailure].void
      else run.intercept[WireObservationLimit].void
    }

  for (id <- failureCases)
    test(s"$id preserves a typed observation limit") {
      val limit = new WireObservationLimit("injected observer ceiling")
      runClient(id) { (peer, request) =>
        sendRequest(peer, request) *> peer.readUntil(Http1Wire.crlf ++ Http1Wire.crlf) *> IO
          .raiseError(limit)
      }.intercept[WireObservationLimit].map(e => assert(e eq limit))
    }

  for {
    id <- failureCases ++ Vector(
      "h1.client.generated-origin-form",
      "h1.client.complete-fixed-length-at-eof",
    )
    wrapping <- Vector("direct", "cause", "suppressed", "cycle")
    adapter <- Vector(false, true)
  } test(
    s"$id keeps timeouts inconclusive with adapter precedence (wrapping=$wrapping, adapter=$adapter)"
  ) {
    val timeout = new java.util.concurrent.TimeoutException("native deadline")
    val error = wrapping match {
      case "direct" => timeout
      case "cause" => new IllegalStateException("native wrapper", timeout)
      case other =>
        val outer = new IllegalStateException("native wrapper")
        val inner = new IllegalStateException("suppressed branch")
        outer.addSuppressed(inner)
        if (other == "cycle") inner.addSuppressed(outer)
        inner.addSuppressed(timeout)
        outer
    }
    val fixture = new AdapterFailure("cleanup", new IllegalStateException("fixture"))
    if (adapter) error.addSuppressed(fixture)
    val run = runClient(id)((_, _) => IO.raiseError(error))
    if (adapter) run.intercept[AdapterFailure].map(actual => assert(actual eq fixture))
    else
      run.intercept[java.util.concurrent.TimeoutException].map { actual =>
        assert((actual eq error) || (actual.getCause eq error))
      }
  }

  for {
    id <- Vector(
      "h1.server.reject-chunk-explicit-plus-size",
      "h1.server.absolute-form-empty-host",
    )
    partial <- Vector(false, true)
    ending <- Vector("eof", "reset", "unknown", "adapter", "wrapped-timeout")
  } test(s"$id preserves optional-head boundary evidence (partial=$partial, ending=$ending)") {
    val failure: Throwable = ending match {
      case "unknown" => new java.io.IOException("independent read failure")
      case "adapter" => new AdapterFailure("fixture", Http1ResetFixture.reset)
      case "wrapped-timeout" =>
        new IllegalStateException("read wrapper", new java.util.concurrent.TimeoutException("read"))
      case _ => Http1ResetFixture.reset
    }
    val run = runServerBytes(
      id,
      if (partial) Http1Wire.ascii("HTTP/1.1 400") else ByteVector.empty,
      if (ending == "eof") IO.pure(None) else IO.raiseError(failure),
    )
    ending match {
      case "eof" | "reset" => if (partial) run.intercept[WireDecodingFailure].void else run
      case "wrapped-timeout" => run.intercept[java.util.concurrent.TimeoutException].void
      case _ =>
        run.attempt.map {
          case Left(actual) => assert(actual eq failure)
          case Right(_) => fail("independent failure was lost")
        }
    }
  }

  for {
    id <- Vector(
      "h1.server.interaction-conflicting-length-stops-pipeline",
      "h1.server.interaction-length-body-request-line",
      "h2.server.interaction-coalesced-preface-request",
    )
    kind <- Vector("unknown", "adapter", "timeout")
  } test(s"$id preserves independent raw-read $kind failures") {
    val failure: Throwable = kind match {
      case "adapter" => new AdapterFailure("fixture", new java.io.IOException("raw read"))
      case "timeout" =>
        new IllegalStateException("wrapper", new java.util.concurrent.TimeoutException("raw read"))
      case _ => new java.io.IOException("independent raw read")
    }
    val run = runServerBytes(id, ByteVector.empty, IO.raiseError(failure))
    if (kind == "timeout") run.intercept[java.util.concurrent.TimeoutException].void
    else
      run.attempt.map {
        case Left(actual) => assert(actual eq failure)
        case Right(_) => fail("independent failure was lost")
      }
  }

  for {
    id <- Vector(
      "h1.client.http10-transfer-encoding-closes",
      "h1.client.http10-transfer-encoding-with-length-closes",
    )
    extra <- Vector(false, true)
  } test(s"$id observes closure and rejects trailing request bytes (extra=$extra)") {
    val run = runClient(id) { (peer, request) =>
      sendRequest(peer, request) *> peer.readUntil(Http1Wire.ascii("0\r\n\r\n")) *>
        (if (extra) peer.writeAscii("x") else IO.unit) *> peer.endOfOutput *>
        IO.pure(response(200, Http1Wire.ascii("abc")))
    }
    if (extra) run.intercept[ProtocolFailure].void else run
  }

  for {
    size <- Vector(0, 65536, 65537)
    ending <- Vector("eof", "reset", "adapter", "unknown")
  } test(
    s"incomplete request closure retains bounded bytes and provenance (size=$size, ending=$ending)"
  ) {
    val error: Throwable = ending match {
      case "adapter" => new AdapterFailure("fixture", Http1ResetFixture.reset)
      case "unknown" => new java.io.IOException("independent read")
      case _ => Http1ResetFixture.reset
    }
    val bytes = ByteVector.fill(size.toLong)(97.toByte)
    WireCodecsSuite
      .peer(bytes, whenEmpty = if (ending == "eof") IO.pure(None) else IO.raiseError(error))
      .flatMap { case (peer, _) =>
        val run = Http1LifecycleCases.readThroughClose(peer)
        if (size > 65536) run.intercept[WireObservationLimit].void
        else if (ending == "eof" || ending == "reset") run.assertEquals(bytes)
        else
          run.attempt.map {
            case Left(actual) => assert(actual eq error)
            case Right(_) => fail("independent failure was lost")
          }
      }
  }

  for (publishIncomplete <- Vector(false, true))
    test(
      s"interaction reset observes isolation and incomplete nonpublication only ($publishIncomplete)"
    ) {
      val run = runServer("h2.server.interaction-reset-other-upload-stream") { (peer, publish) =>
        def upload(body: ByteVector): IO[ByteVector] = Http2Wire.readFrame(peer).flatMap { frame =>
          if (frame.frameType == Http2Wire.Data && frame.streamId == 3) {
            val next = body ++ frame.payload
            if (frame.hasFlag(Http2Wire.EndStream)) IO.pure(next) else upload(next)
          } else
            upload(body) // Intentionally ignores RST_STREAM: closure is not this case's witness.
        }
        for {
          _ <- Http2Wire.serverHandshake(peer)
          body <- upload(ByteVector.empty)
          _ <- publish(RequestData(path = "/two", body = body))
          _ <- if (publishIncomplete) publish(RequestData(path = "/cancelled")) else IO.unit
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.headers(3, Vector(":status" -> "200"), endStream = false),
          )
          _ <- Http2Wire.writeFrame(peer, Http2Wire.data(3, body))
          ping <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Ping)
          _ <- Http2Wire.writeFrame(peer, ping.copy(flags = Http2Wire.Ack))
        } yield ()
      }
      if (publishIncomplete) run.intercept[ProtocolFailure].void else run
    }

  test("canceling an optional response-head read retains its partial bytes") {
    val partial = Http1Wire.ascii("HTTP/1.1 400")
    for {
      waiting <- Deferred[IO, Unit]
      fixture <- WireCodecsSuite.peer(partial, whenEmpty = waiting.complete(()).void *> IO.never)
      reader <- fixture._1.readUntilOrClosed(Http1Wire.crlf ++ Http1Wire.crlf, 65536).start
      _ <- waiting.get
      _ <- reader.cancel
      retained <- fixture._1.readExact(partial.size.toInt)
      _ <- IO(assertEquals(retained, partial))
    } yield ()
  }

  test("a cyclic native exception graph without timeout remains a native rejection") {
    val outer = new IllegalArgumentException("native rejection")
    val inner = new IllegalStateException("suppressed branch")
    outer.addSuppressed(inner)
    inner.addSuppressed(outer)
    RawPeer.raiseAdapterOrTimeout[IO](outer)
  }

  private val incompleteRequests = Vector(
    "request-head",
    "fixed-length-empty",
    "fixed-length-prefix",
    "chunk-size",
    "chunk-data",
    "chunk-delimiter",
    "terminal-chunk",
    "trailer-terminator",
    "trailer-field",
  )
  for {
    suffix <- incompleteRequests
    close <- Vector(false, true)
  } test(s"incomplete $suffix requires persistent warmup (close=$close)") {
    val run = runServer(s"h1.server.incomplete-$suffix") { (peer, publish) =>
      Http1Wire.readRequest(peer).flatMap { ready =>
        publish(RequestData(path = ready.target, body = ready.body)) *>
          peer.writeAscii(
            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n" +
              (if (close) "Connection: ClOsE\r\n" else "") + "\r\nok"
          )
      } *> (if (close) peer.endOfOutput
            else
              peer.readToEnd() *> peer.writeAscii(
                "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n"
              ) *> peer.endOfOutput)
    }
    if (close) run.intercept[WireObservationLimit].void else run
  }

  private val serverInteractionCases = Vector(
    "h2.server.interaction-coalesced-preface-request",
    "h2.server.interaction-interleaved-request-data",
    "h2.server.interaction-trailers-between-other-stream-data",
    "h2.server.interaction-reset-other-upload-stream",
    "h2.server.interaction-reset-retains-compression-context",
  )

  private def interactionServerControl(
      id: String,
      push: String = "if-enabled",
      settings: Vector[(Int, Long)] = Vector.empty,
      settingsPhase: String = "initial",
      informationals: Vector[String] = Vector.empty,
      checkInitial: Boolean = true,
  ): IO[Unit] = runServer(id) { (peer, publish) =>
    def sendSettings: IO[Unit] = Http2Wire.writeFrame(peer, Http2Wire.settings(settings))
    def promise(stream: Int): Http2Wire.Frame = Http2Wire.Frame(
      Http2Wire.PushPromise,
      Http2Wire.EndHeaders,
      stream,
      Http2Wire.uint32(2) ++ HpackWire.encodeLiteral(
        Vector(
          ":method" -> "GET",
          ":scheme" -> "http",
          ":authority" -> "wire.test",
          ":path" -> "/pushed",
        )
      ),
    )
    for {
      preface <- peer.readExact(Http2Wire.clientPreface.size.toInt)
      _ <- IO(assertEquals(preface, Http2Wire.clientPreface))
      initial <- Http2Wire.readFrame(peer)
      _ <-
        if (checkInitial) IO(assertEquals(initial, Http2Wire.settings(Vector(2 -> 0L))))
        else IO.unit
      enabled = !initial.payload
        .grouped(6)
        .exists(entry =>
          entry.take(2) == ByteVector(0, 2) && Http2Wire.readUint32(entry.drop(2)) == 0L
        )
      _ <-
        if (settingsPhase == "initial") sendSettings
        else Http2Wire.writeFrame(peer, Http2Wire.settings())
      _ <- if (settingsPhase == "pre-ack") sendSettings else IO.unit
      _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))
      decoder <- HpackWire.decoder[IO]()
      _ <- {
        val reset = id.contains("interaction-reset-")
        val expected = if (reset || id.endsWith("coalesced-preface-request")) 1 else 2
        def loop(
            fields: Map[Int, Vector[(String, String)]],
            bodies: Map[Int, ByteVector],
            completed: Int,
        ): IO[Unit] =
          if (completed == expected) {
            if (reset)
              Http2Wire
                .readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Ping)
                .flatMap(ping => Http2Wire.writeFrame(peer, ping.copy(flags = Http2Wire.Ack)))
            else IO.unit
          } else
            Http2Wire.readFrame(peer).flatMap { frame =>
              def finish(
                  nextFields: Map[Int, Vector[(String, String)]],
                  nextBodies: Map[Int, ByteVector],
              ): IO[Unit] = {
                val stream = frame.streamId
                val headers = nextFields(stream)
                val body = nextBodies.getOrElse(stream, ByteVector.empty)
                val path = headers.collectFirst { case (":path", value) => value }.get
                val emitPush = push == "always" || (push == "if-enabled" && enabled)
                publish(
                  RequestData(method = "POST", path = path, headers = headers, body = body)
                ) *>
                  (if (settingsPhase == "later") sendSettings else IO.unit) *>
                  (if (emitPush) Http2Wire.writeFrame(peer, promise(stream)) else IO.unit) *>
                  informationals.foldLeft(IO.unit)((send, status) =>
                    send *>
                      Http2Wire.writeFrame(
                        peer,
                        Http2Wire.headers(stream, Vector(":status" -> status), endStream = false),
                      )
                  ) *>
                  Http2Wire.writeFrame(
                    peer,
                    Http2Wire.headers(stream, Vector(":status" -> "200"), endStream = false),
                  ) *>
                  Http2Wire.writeFrame(peer, Http2Wire.data(stream, body)) *>
                  loop(nextFields, nextBodies, completed + 1)
              }
              if (frame.frameType == Http2Wire.Headers)
                Http2Wire.readHeaders(peer, frame, decoder).flatMap { headers =>
                  val next =
                    if (fields.contains(frame.streamId)) fields
                    else fields.updated(frame.streamId, headers)
                  if (frame.hasFlag(Http2Wire.EndStream)) finish(next, bodies)
                  else loop(next, bodies, completed)
                }
              else if (frame.frameType == Http2Wire.Data) {
                val next = bodies.updated(
                  frame.streamId,
                  bodies.getOrElse(frame.streamId, ByteVector.empty) ++ Http2Wire.unpad(frame),
                )
                if (frame.hasFlag(Http2Wire.EndStream)) finish(fields, next)
                else loop(fields, next, completed)
              } else loop(fields, bodies, completed)
            }
        loop(Map.empty, Map.empty, 0)
      }
    } yield ()
  }

  for (id <- serverInteractionCases) {
    test(s"$id disables initially enabled optional push") {
      interactionServerControl(id)
    }
    test(s"$id accepts a push-if-enabled server") {
      interactionServerControl(id, checkInitial = false)
    }
    test(s"$id rejects push after acknowledged disablement") {
      interactionServerControl(id, push = "always")
        .intercept[ProtocolFailure]
        .map(error => assert(error.getMessage.contains("PUSH_PROMISE after push disablement")))
    }
    for {
      phase <- Vector("initial", "pre-ack", "later")
      value <- Vector(0L, 1L)
    } test(s"$id validates server ENABLE_PUSH=$value at $phase") {
      val run = interactionServerControl(
        id,
        push = "never",
        settings = Vector(2 -> value),
        settingsPhase = phase,
        checkInitial = false,
      )
      if (value == 1L)
        run
          .intercept[ProtocolFailure]
          .map(error => assert(error.getMessage.contains("invalid ENABLE_PUSH")))
      else run
    }
    for (
      statuses <- Vector(Vector("100"), Vector("103"), Vector("100", "103", "100"), Vector("101"))
    )
      test(s"$id validates informational responses $statuses") {
        val run = interactionServerControl(
          id,
          push = "never",
          informationals = statuses,
          checkInitial = false,
        )
        if (statuses.contains("101"))
          run
            .intercept[ProtocolFailure]
            .map(error => assert(error.getMessage.contains("invalid response status")))
        else run
      }
  }

  for (phase <- Vector("initial", "later"))
    test(s"interaction accepts client ENABLE_PUSH=1 at $phase") {
      runClient("h2.client.interaction-eager-server-settings") { (peer, request) =>
        for {
          _ <- peer.write(
            Http2Wire.clientPreface ++ Http2Wire.encode(
              Http2Wire.settings(if (phase == "initial") Vector(2 -> 1L) else Vector.empty)
            )
          )
          _ <- Http2Wire.readFrame(peer)
          _ <- Http2Wire.readFrame(peer)
          _ <-
            if (phase == "later") Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(2 -> 1L)))
            else IO.unit
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.headers(
              1,
              Vector(
                ":method" -> request.method,
                ":scheme" -> "http",
                ":authority" -> "wire.test",
                ":path" -> request.path,
              ),
            ),
          )
          decoder <- HpackWire.decoder[IO]()
          head <- Http2Wire.readUntil(peer, fromServer = true)(_.frameType == Http2Wire.Headers)
          fields <- Http2Wire.readHeaders(peer, head, decoder)
          data <- Http2Wire.readFrame(peer)
        } yield response(
          fields.collectFirst { case (":status", status) => status.toInt }.get,
          data.payload,
        )
      }
    }

  private val settingsServerCases = Vector(
    "h2.server.interaction-interleaved-request-data",
    "h2.server.interaction-trailers-between-other-stream-data",
    "h2.server.interaction-reset-other-upload-stream",
  )
  private val constrainedSettings = Vector(Vector(4 -> 0L), Vector(4 -> 3L))
  for {
    id <- settingsServerCases
    settings <- constrainedSettings
    later <- Vector(false, true)
  } test(
    s"$id reports unavailable peer settings $settings before positive stimulus (later=$later)"
  ) {
    runServerBytes(
      id,
      (if (later) Http2Wire.encode(Http2Wire.settings()) else ByteVector.empty) ++ Http2Wire.encode(
        Http2Wire.settings(settings)
      ) ++
        Http2Wire.encode(Http2Wire.settings(ack = true)),
      IO.never,
    )
      .intercept[WireObservationLimit]
      .void
  }

  private val settingsClientCases = Vector(
    "h2.client.interaction-eager-server-settings",
    "h2.client.interaction-interleaved-response-data",
    "h2.client.interaction-interim-next-stream-association",
    "h2.client.interaction-complete-data-response-no-error-reset",
  )
  for {
    id <- settingsClientCases
    settings <- constrainedSettings
  } test(s"$id reports unavailable client settings $settings before positive stimulus") {
    runClient(id) { (peer, _) =>
      peer.write(
        Http2Wire.clientPreface ++ Http2Wire.encode(Http2Wire.settings(settings))
      ) *> IO.never
    }.intercept[WireObservationLimit].void
  }
  for (size <- Vector(0L, 128L, 4096L)) {
    test(s"eager client response honors HPACK table $size") {
      runClient("h2.client.interaction-eager-server-settings") { (peer, request) =>
        for {
          _ <- peer.write(
            Http2Wire.clientPreface ++ Http2Wire.encode(
              Http2Wire.settings(Vector(1 -> size, 4 -> 9L))
            )
          )
          _ <- Http2Wire.readFrame(peer)
          _ <- Http2Wire.readFrame(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.headers(
              1,
              Vector(
                ":method" -> "GET",
                ":scheme" -> "http",
                ":authority" -> "wire.test",
                ":path" -> request.path,
              ),
            ),
          )
          decoder <- HpackWire.decoder[IO](size.toInt)
          head <- Http2Wire.readFrame(peer)
          _ <- IO(assertEquals(head.payload.head, 0x20.toByte))
          fields <- Http2Wire.readHeaders(peer, head, decoder)
          data <- Http2Wire.readFrame(peer)
        } yield response(
          fields.collectFirst { case (":status", status) => status.toInt }.get,
          data.payload,
        )
      }
    }
    for (id <- settingsServerCases) test(s"$id honors HPACK table $size and default DATA credit") {
      runServer(id) { (peer, publish) =>
        def collect(
            decoder: HpackWire.Decoder[IO],
            paths: Map[Int, String],
            bodies: Map[Int, ByteVector],
            completed: Int,
        ): IO[Unit] =
          if (completed == (if (id.endsWith("reset-other-upload-stream")) 1 else 2)) {
            if (id.endsWith("reset-other-upload-stream"))
              Http2Wire
                .readFrame(peer)
                .flatMap(ping => Http2Wire.writeFrame(peer, ping.copy(flags = Http2Wire.Ack)))
            else IO.unit
          } else
            Http2Wire.readFrame(peer).flatMap { frame =>
              def finish(path: String, body: ByteVector): IO[Unit] =
                publish(RequestData(method = "POST", path = path, body = body)) *>
                  peer.write(
                    Http2Wire.encode(
                      Http2Wire
                        .headers(frame.streamId, Vector(":status" -> "200"), endStream = false)
                    ) ++ Http2Wire.encode(Http2Wire.data(frame.streamId, body))
                  )
              if (frame.frameType == Http2Wire.Headers)
                for {
                  _ <- IO(assertEquals(frame.payload.head, 0x20.toByte))
                  fields <- Http2Wire.readHeaders(peer, frame, decoder)
                  path = fields
                    .collectFirst { case (":path", value) => value }
                    .getOrElse(paths(frame.streamId))
                  _ <-
                    if (frame.hasFlag(Http2Wire.EndStream)) finish(path, bodies(frame.streamId))
                    else IO.unit
                  _ <- collect(
                    decoder,
                    paths.updated(frame.streamId, path),
                    bodies,
                    completed + (if (frame.hasFlag(Http2Wire.EndStream)) 1 else 0),
                  )
                } yield ()
              else if (frame.frameType == Http2Wire.Data) {
                val body = bodies.getOrElse(frame.streamId, ByteVector.empty) ++ frame.payload
                (if (frame.hasFlag(Http2Wire.EndStream)) finish(paths(frame.streamId), body)
                 else IO.unit) *>
                  collect(
                    decoder,
                    paths,
                    bodies.updated(frame.streamId, body),
                    completed + (if (frame.hasFlag(Http2Wire.EndStream)) 1 else 0),
                  )
              } else collect(decoder, paths, bodies, completed)
            }
        for {
          _ <- peer.readExact(Http2Wire.clientPreface.size.toInt)
          _ <- Http2Wire.readFrame(peer)
          _ <- peer.write(
            Http2Wire.encode(Http2Wire.settings(Vector(1 -> size, 4 -> 9L))) ++ Http2Wire.encode(
              Http2Wire.settings(ack = true)
            )
          )
          _ <- Http2Wire.readFrame(peer)
          decoder <- HpackWire.decoder[IO](size.toInt)
          _ <- collect(decoder, Map.empty, Map.empty, 0)
        } yield ()
      }
    }
  }

  for (settings <- constrainedSettings)
    test(s"eager response honors later SETTINGS $settings before DATA") {
      runClient("h2.client.interaction-eager-server-settings") { (peer, _) =>
        peer.write(Http2Wire.clientPreface ++ Http2Wire.encode(Http2Wire.settings())) *>
          Http2Wire.readFrame(peer) *> Http2Wire.readFrame(peer) *>
          Http2Wire.writeFrame(peer, Http2Wire.settings(settings)) *> IO.never
      }.intercept[WireObservationLimit].void
    }

  for (
    (suffix, encoded) <- Vector(
      "nonchunked-request-transfer-coding" -> "1f8b08000000000002ff4b4c4a0600c241243503000000",
      "chunked-not-final-in-request" -> "1f8b08000000000002ff33e6e54a4c4ae6e532e0e5e2e5020045cff8100d000000",
    )
  ) test(s"$suffix isolates request framing with valid outer gzip bytes") {
    runServer(s"h1.server.reject-$suffix") { (peer, _) =>
      peer
        .readUntil(Http1Wire.crlf ++ Http1Wire.crlf) *> peer.readExact(encoded.length / 2).flatMap {
        body =>
          IO(assertEquals(body, ByteVector.fromValidHex(encoded)))
      } *> peer.writeAscii(
        "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n"
      ) *> peer.endOfOutput
    }
  }

  private val trailerClients = Vector(
    "h2.client.interaction-eager-server-settings",
    "h2.client.interaction-interleaved-response-data",
    "h2.client.interaction-interim-next-stream-association",
    "h2.client.interaction-eager-zero-window-upload",
  )
  private val interactionBinary = ByteVector(0, 13, 10, 127, 128, 255)

  for {
    id <- trailerClients
    mode <- Vector("none", "empty", "fragmented", "dynamic", "missing-end", "pseudo")
  } test(s"$id observes complete request trailers ($mode)") {
    val run = runClient(id) { (peer, request) =>
      // Only request observation is under test; consume the case's known response sequence.
      def receiveUntil(accept: Http2Wire.Frame => Boolean): IO[Unit] =
        Http2Wire.readFrame(peer).flatMap { frame =>
          if (accept(frame)) IO.unit
          else {
            val acknowledge =
              if (frame.frameType == Http2Wire.Ping && !frame.hasFlag(Http2Wire.Ack))
                Http2Wire.writeFrame(peer, frame.copy(flags = Http2Wire.Ack))
              else IO.unit
            acknowledge *> receiveUntil(accept)
          }
        }
      val second = request.path == "/two"
      val stream = if (second) 3 else 1
      val upload = id.endsWith("eager-zero-window-upload")
      val interleaved = id.endsWith("interleaved-response-data")
      val dynamic = mode == "dynamic"
      val insertion = ByteVector.fromValidHex("4007782d7374617465046b657074")
      val fields = Vector(
        ":method" -> request.method,
        ":scheme" -> "http",
        ":authority" -> "wire.test",
        ":path" -> request.path,
      ) ++ (if (upload) Vector("content-length" -> interactionBinary.size.toString)
            else Vector.empty)
      val initialBlock = HpackWire.encodeLiteral(fields) ++
        (if (dynamic && second) ByteVector(0xbf.toByte)
         else if (dynamic) insertion
         else ByteVector.empty)
      val trailerBlock =
        if (dynamic && !second) ByteVector(0xbe.toByte) ++ insertion
        else if (dynamic) ByteVector(0xbe.toByte)
        else if (mode == "pseudo") HpackWire.encodeLiteral(Vector(":path" -> "/trailer"))
        else ByteVector.empty
      for {
        _ <- if (second) IO.unit else Http2Wire.clientHandshake(peer)
        _ <- Http2Wire.writeFrame(
          peer,
          Http2Wire.Frame(
            Http2Wire.Headers,
            Http2Wire.EndHeaders | (if (mode == "none" && !upload) Http2Wire.EndStream else 0),
            stream,
            initialBlock,
          ),
        )
        _ <-
          if (upload)
            receiveUntil(_.frameType == Http2Wire.WindowUpdate) *>
              Http2Wire.writeFrame(
                peer,
                Http2Wire.data(stream, interactionBinary, endStream = mode == "none"),
              )
          else IO.unit
        _ <-
          if (mode == "none") IO.unit
          else
            Http2Wire.writeFrame(
              peer,
              Http2Wire.Frame(
                Http2Wire.Headers,
                (if (mode == "missing-end") 0 else Http2Wire.EndStream) |
                  (if (mode == "fragmented" || dynamic) 0 else Http2Wire.EndHeaders),
                stream,
                if (mode == "fragmented" || dynamic) trailerBlock.take(1) else trailerBlock,
              ),
            ) *> (if (mode == "fragmented" || dynamic)
                    Http2Wire.writeFrame(
                      peer,
                      Http2Wire.Frame(
                        Http2Wire.Continuation,
                        Http2Wire.EndHeaders,
                        stream,
                        trailerBlock.drop(1),
                      ),
                    )
                  else IO.unit)
        _ <-
          if (mode == "missing-end" || mode == "pseudo") IO.never
          else if (upload || (interleaved && !second))
            receiveUntil(_.frameType == Http2Wire.Headers).void
          else
            receiveUntil(f =>
              f.frameType == Http2Wire.Data && f.streamId == stream && f.hasFlag(
                Http2Wire.EndStream
              )
            ).void
      } yield ResponseData[IO](
        if (second) 201 else 200,
        if (interleaved) Vector("x-stream" -> (if (second) "two" else "one"))
        else Vector.empty,
        Stream.chunk(
          Chunk.byteVector(
            if (upload) ByteVector.empty
            else if (second) Http1Wire.ascii("following")
            else interactionBinary
          )
        ),
        IO.pure(Vector.empty),
      )
    }
    if (mode == "missing-end" || mode == "pseudo") run.intercept[ProtocolFailure].void
    else run
  }

  for (sizes <- Vector(Vector(64), Vector(0, 64)))
    test(s"reset compression witness honors acknowledged table history $sizes") {
      runServer("h2.server.interaction-reset-retains-compression-context") { (peer, publish) =>
        for {
          _ <- peer.readExact(Http2Wire.clientPreface.size.toInt)
          _ <- Http2Wire.readFrame(peer)
          _ <- sizes.foldLeft(IO.unit)((send, size) =>
            send *> Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(1 -> size.toLong)))
          )
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))
          first <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
          updates = sizes.foldLeft(ByteVector.empty)((bytes, size) =>
            bytes ++ HpackWire.encodeInteger(size, 5, 0x20)
          )
          _ <- IO(assertEquals(first.payload.take(updates.size), updates))
          decoder <- HpackWire.decoder[IO](64)
          _ <- Http2Wire.readHeaders(peer, first, decoder)
          _ <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.RstStream)
          second <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
          fields <- Http2Wire.readHeaders(peer, second, decoder)
          _ <- IO(assertEquals(fields.filter(_._1 == "x-state"), Vector("x-state" -> "kept")))
          _ <- publish(RequestData(method = "POST", path = "/after", headers = fields))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.headers(3, Vector(":status" -> "200")))
          ping <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Ping)
          _ <- Http2Wire.writeFrame(peer, ping.copy(flags = Http2Wire.Ack))
        } yield ()
      }
    }

  private val resetInteractionCases = Vector(
    "h2.server.interaction-reset-other-upload-stream",
    "h2.server.interaction-reset-retains-compression-context",
  )

  private def crossingResetFrames(kind: String, stream: Int): Vector[Http2Wire.Frame] = {
    val insertion = ByteVector.fromValidHex("4006782d72616365046b657074")
    val block = HpackWire.encodeLiteral(Vector(":status" -> "413")) ++ insertion
    kind match {
      case "none" => Vector.empty
      case "window" => Vector(Http2Wire.windowUpdate(stream, 1))
      case "priority" =>
        Vector(Http2Wire.Frame(Http2Wire.Priority, 0, stream, Http2Wire.uint32(0) ++ ByteVector(0)))
      case "reset" =>
        Vector(Http2Wire.Frame(Http2Wire.RstStream, 0, stream, Http2Wire.uint32(8)))
      case "headers" =>
        Vector(
          Http2Wire.Frame(
            Http2Wire.Headers,
            Http2Wire.EndHeaders | Http2Wire.EndStream,
            stream,
            block,
          )
        )
      case "continuation" =>
        Vector(
          Http2Wire.Frame(Http2Wire.Headers, Http2Wire.EndStream, stream, block.take(4)),
          Http2Wire.Frame(Http2Wire.Continuation, Http2Wire.EndHeaders, stream, block.drop(4)),
        )
      case "padded-data" =>
        Vector(
          Http2Wire.Frame(Http2Wire.Headers, Http2Wire.EndHeaders, stream, block),
          Http2Wire.Frame(
            Http2Wire.Data,
            Http2Wire.Padded | Http2Wire.EndStream,
            stream,
            ByteVector(2, 65, 0, 0),
          ),
        )
      case "trailers" =>
        Vector(
          Http2Wire.Frame(
            Http2Wire.Headers,
            Http2Wire.EndHeaders,
            stream,
            HpackWire.encodeLiteral(Vector(":status" -> "413")),
          ),
          Http2Wire.Frame(
            Http2Wire.Headers,
            Http2Wire.EndHeaders | Http2Wire.EndStream,
            stream,
            insertion,
          ),
        )
    }
  }

  private def resetInteraction(
      id: String,
      crossing: Vector[Http2Wire.Frame],
      timing: String = "early",
      indexedResponse: Boolean = false,
  ): IO[Unit] = runServer(id) { (peer, publish) =>
    HpackWire.decoder[IO]().flatMap { decoder =>
      def emit: IO[Unit] =
        crossing.foldLeft(IO.unit)((send, frame) => send *> Http2Wire.writeFrame(peer, frame))
      def respond(path: String, body: ByteVector, fields: Vector[(String, String)]): IO[Unit] = {
        val block =
          if (indexedResponse) ByteVector(0x88.toByte, 0xbe.toByte)
          else HpackWire.encodeLiteral(Vector(":status" -> "200"))
        publish(
          RequestData(path = path, headers = fields.filterNot(_._1.startsWith(":")), body = body)
        ) *>
          Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(Http2Wire.Headers, Http2Wire.EndHeaders, 3, block),
          ) *>
          (if (timing == "after-head") emit else IO.unit) *>
          Http2Wire.writeFrame(peer, Http2Wire.data(3, body.take(3), endStream = false)) *>
          (if (timing == "after-prefix") emit else IO.unit) *>
          Http2Wire.writeFrame(peer, Http2Wire.data(3, body.drop(3), endStream = false)) *>
          (if (timing == "after-suffix") emit else IO.unit) *>
          Http2Wire.writeFrame(peer, Http2Wire.data(3, ByteVector.empty)) *>
          (if (timing == "after-end") emit else IO.unit) *>
          Http2Wire
            .readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Ping)
            .flatMap(ping => Http2Wire.writeFrame(peer, ping.copy(flags = Http2Wire.Ack)))
      }
      def loop(path: String, body: ByteVector, fields: Vector[(String, String)]): IO[Unit] =
        Http2Wire.readFrame(peer).flatMap { frame =>
          if (frame.frameType == Http2Wire.Headers)
            Http2Wire.readHeaders(peer, frame, decoder).flatMap { next =>
              if (frame.streamId == 1)
                (if (timing == "early") emit else IO.unit) *> loop(path, body, fields)
              else {
                val nextPath = next.collectFirst { case (":path", value) => value }.get
                if (frame.hasFlag(Http2Wire.EndStream)) respond(nextPath, body, next)
                else loop(nextPath, body, next)
              }
            }
          else if (frame.frameType == Http2Wire.Data && frame.streamId == 3) {
            val next = body ++ Http2Wire.unpad(frame)
            if (frame.hasFlag(Http2Wire.EndStream)) respond(path, next, fields)
            else loop(path, next, fields)
          } else loop(path, body, fields)
        }
      Http2Wire.serverHandshake(peer) *> loop("", ByteVector.empty, Vector.empty)
    }
  }

  for {
    id <- resetInteractionCases
    kind <- Vector(
      "none",
      "window",
      "priority",
      "reset",
      "headers",
      "continuation",
      "padded-data",
      "trailers",
    )
  } test(s"$id accepts queued $kind and preserves connection HPACK") {
    val indexed = Set("headers", "continuation", "padded-data", "trailers").contains(kind)
    resetInteraction(id, crossingResetFrames(kind, 1), indexedResponse = indexed)
  }

  for {
    id <- resetInteractionCases
    kind <- Vector("reset", "headers", "continuation", "padded-data", "trailers")
    timing <- Vector("after-head", "after-prefix", "after-suffix", "after-end")
  } test(s"$id admits $kind only before reset confirmation ($timing)") {
    val run = resetInteraction(id, crossingResetFrames(kind, 1), timing)
    if (
      id.endsWith("reset-other-upload-stream") && Set("after-head", "after-prefix").contains(timing)
    ) run
    else run.intercept[ProtocolFailure].void
  }

  for {
    id <- resetInteractionCases
    kind <- Vector("reset", "headers", "padded-data")
  } test(s"$id does not discard $kind on an unrelated stream") {
    resetInteraction(id, crossingResetFrames(kind, 5)).intercept[ProtocolFailure].void
  }

  for (id <- resetInteractionCases) {
    test(s"$id rejects crossing PUSH_PROMISE after initial disablement") {
      val promise = Http2Wire.Frame(
        Http2Wire.PushPromise,
        Http2Wire.EndHeaders,
        1,
        Http2Wire.uint32(2) ++ HpackWire.encodeLiteral(
          Vector(
            ":method" -> "GET",
            ":scheme" -> "http",
            ":authority" -> "wire.test",
            ":path" -> "/pushed",
          )
        ),
      )
      resetInteraction(id, Vector(promise))
        .intercept[ProtocolFailure]
        .map(error => assert(error.getMessage.contains("PUSH_PROMISE after push disablement")))
    }
    test(s"$id requires a valid shared HPACK entry after discarded headers") {
      resetInteraction(id, Vector.empty, indexedResponse = true).intercept[WireDecodingFailure].void
    }
    test(s"$id bounds discarded reset frames as incomplete observation") {
      resetInteraction(id, Vector.fill(1024)(crossingResetFrames("reset", 1).head))
        .intercept[WireObservationLimit]
        .void
    }
    for (count <- Vector(255, 256))
      test(s"$id counts discarded DATA padding in its finite observation budget ($count frames)") {
        val padded = Http2Wire.Frame(
          Http2Wire.Data,
          Http2Wire.Padded,
          1,
          ByteVector(255.toByte) ++ ByteVector.fill(255)(0),
        )
        // Exercise the volume boundary with a deterministic byte source. Socket scheduling
        // is exercised by the crossing controls above, independently of this 64-KiB budget.
        val upload = id.endsWith("reset-other-upload-stream")
        val body = if (upload) Http1Wire.ascii("following") else ByteVector.empty
        val response = Vector(
          Http2Wire.settings(),
          Http2Wire.settings(ack = true),
          Http2Wire.headers(1, Vector(":status" -> "413"), endStream = false),
        ) ++ Vector.fill(count)(padded) ++ Vector(
          Http2Wire.headers(3, Vector(":status" -> "200"), endStream = false),
          Http2Wire.data(3, body),
          Http2Wire
            .Frame(Http2Wire.Ping, Http2Wire.Ack, 0, ByteVector(41, 43, 47, 53, 59, 61, 67, 71)),
        )
        val run = runServerBytes(
          id,
          response.foldLeft(ByteVector.empty)((bytes, frame) => bytes ++ Http2Wire.encode(frame)),
          IO.pure(None),
          IO.pure(
            RequestData(
              path = if (upload) "/two" else "/after",
              headers = Vector("x-state" -> "kept"),
              body = body,
            )
          ),
        )
        if (count == 255) run else run.intercept[WireObservationLimit].void
      }
    test(s"$id validates padding even on discarded DATA") {
      val padded = Http2Wire.Frame(Http2Wire.Data, Http2Wire.Padded, 1, ByteVector(2, 0))
      resetInteraction(id, Vector(padded)).intercept[WireDecodingFailure].void
    }
  }

  private final class UploadObservation(
      val result: Either[Throwable, Unit],
      val events: Vector[String],
      val writes: Vector[Int],
  )

  private def assertUploadStopped(observed: UploadObservation): Unit = {
    assertEquals(observed.events.count(_ == "body-finalized"), 1)
    assertEquals(observed.events.count(_ == "client-released"), 1)
    assert(!observed.events.contains("body-completed"), observed.events.toString)
    assert(observed.events.indexOf("body-finalized") < observed.events.indexOf("client-released"))
  }

  private def coalescedUpload(
      corruptPrefix: Boolean = false,
      status: Int = 413,
      responseFailure: Option[Throwable] = None,
      cleanupFailure: Option[Throwable] = None,
      rawFields: ByteVector = ByteVector.empty,
  ): IO[UploadObservation] = for {
    events <- cats.effect.Ref.of[IO, Vector[String]](Vector.empty)
    writes <- cats.effect.Ref.of[IO, Vector[Int]](Vector.empty)
    target = {
      def record(event: String): IO[Unit] = events.update(_ :+ event)
      final class BufferedClient(observer: Option[AdapterFailure => IO[Unit]])
          extends WireTarget[IO] {
        val name = "coalesced-h1-client"
        val version = "oracle-challenge"
        override def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] =
          new BufferedClient(Some(observe))
        def server(protocol: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] =
          Resource.eval(IO.raiseError(new UnsupportedOperationException("client only")))
        def client(
            protocol: Protocol,
            address: SocketAddress[IpAddress],
        ): Resource[IO, ClientDriver[IO]] =
          (for {
            socket <- Network[IO].connect(address)
            transcript <- Resource.eval(Transcript.create[IO]())
            peer <- Resource.eval(RawPeer.create(socket, transcript))
          } yield new ClientDriver[IO] {
            def exchange(request: RequestData): Resource[IO, ResponseData[IO]] =
              Resource.eval(IO.raiseError(new AssertionError("expected a streaming upload")))
            def exchangeStream(
                request: RequestData,
                body: FixtureCleanup[IO] => Stream[IO, Byte],
            ): Resource[IO, ResponseData[IO]] = for {
              failures <- AdapterFailure.recorder[IO](observer)
              head = Http1Wire
                .message(
                  s"${request.method} ${request.path} HTTP/1.1",
                  Vector("Host" -> "wire.test") ++ request.headers,
                  ByteVector.empty,
                )
                .dropRight(2) ++ rawFields ++ Http1Wire.ascii("\r\n")
              source = failures.source("reading buffered upload")(
                (body(failures.fixtureCleanup).map(b => if (corruptPrefix) (b ^ 1).toByte else b) ++
                  Stream.exec(record("body-completed"))).onFinalize(
                  record("body-finalized") *> cleanupFailure.fold(IO.unit)(IO.raiseError[Unit])
                )
              )
              // Coalesce the encoded head and body before allowing a socket write. The
              // reader runs concurrently: only the actual peer response stops the upload.
              upload = (Stream.chunk(Chunk.byteVector(head)) ++ source)
                .chunkMin(32 * 1024)
                .evalMap(chunk => writes.update(_ :+ chunk.size) *> peer.write(chunk.toByteVector))
                .compile
                .drain
              fiber <- Resource.make(upload.start)(_.cancel)
              response <- Resource.eval(
                Http1Wire.readFinalResponse(peer).flatMap { received =>
                  record(s"response:${received.status}") *>
                    responseFailure.fold(
                      fiber.cancel *> peer.endOfOutput *> record("output-ended") *>
                        IO.pure(Http1CaseOracleSuite.this.response(status, ByteVector.empty))
                    )(IO.raiseError[ResponseData[IO]])
                }
              )
              held <- Resource.make(IO.pure(response))(_ => record("response-released"))
            } yield held
          }).onFinalize(record("client-released"))
      }
      new BufferedClient(None)
    }
    transcript <- Transcript.create[IO]()
    result <- AdapterFailure
      .observe(target) { monitored =>
        selected("h1.client.error-response-during-upload").run(CaseContext(monitored, transcript))
      }
      .attempt
    recorded <- events.get
    written <- writes.get
  } yield new UploadObservation(result, recorded, written)

  private def response(
      status: Int,
      body: ByteVector,
      trailers: Vector[(String, String)] = Vector.empty,
  ): ResponseData[IO] =
    ResponseData(status, Vector.empty, Stream.chunk(Chunk.byteVector(body)), IO.pure(trailers))

  private def sendRequest(peer: RawPeer[IO], request: RequestData): IO[Unit] =
    peer.write(
      Http1Wire.message(
        s"${request.method} ${request.path} HTTP/1.1",
        Vector("Host" -> "wire.test", "Content-Length" -> request.body.size.toString),
        request.body,
      )
    )

  private def selected(id: String): WireCase =
    Catalog.all.find(_.id == id).getOrElse(throw new IllegalArgumentException(s"missing case $id"))

  private def runServer(
      id: String
  )(script: (RawPeer[IO], RequestData => IO[Unit]) => IO[Unit]): IO[Unit] = for {
    fixture <- ScriptedTarget.create[IO](script)
    transcript <- Transcript.create[IO]()
    _ <- selected(id).run(CaseContext(fixture._1, transcript))
  } yield ()

  private def runServerBytes(
      id: String,
      bytes: ByteVector,
      whenEmpty: IO[Option[Chunk[Byte]]],
      received: IO[RequestData] =
        IO.raiseError(new AssertionError("unexpected application observation")),
  ): IO[Unit] = {
    val target = new WireTarget[IO] {
      val name = "scripted-h1-reset"
      val version = "oracle-challenge"
      def server(protocol: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.pure(
          ServerHandle[IO](
            SocketAddress(ip"127.0.0.1", port"1"),
            received,
            IO.pure(None),
          )
        )
      def client(
          protocol: Protocol,
          address: SocketAddress[IpAddress],
      ): Resource[IO, ClientDriver[IO]] =
        Resource.eval(IO.raiseError(new UnsupportedOperationException("server only")))
    }
    for {
      raw <- WireCodecsSuite.peer(bytes, whenEmpty = whenEmpty)
      transcript <- Transcript.create[IO]()
      _ <- selected(id).run(CaseContext(target, transcript, (_, _) => Resource.pure(raw._1)))
      written <- raw._2
      _ <- IO(assert(written.nonEmpty, "actual case must send its request stimulus"))
    } yield ()
  }

  private def runClient(id: String, onRelease: IO[Unit] = IO.unit)(
      script: (RawPeer[IO], RequestData) => IO[ResponseData[IO]]
  ): IO[Unit] = {
    val target = new WireTarget[IO] {
      val name = "scripted-h1-client"
      val version = "oracle-challenge"
      def server(protocol: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.eval(IO.raiseError(new UnsupportedOperationException("client only")))
      def client(
          protocol: Protocol,
          address: SocketAddress[IpAddress],
      ): Resource[IO, ClientDriver[IO]] =
        for {
          socket <- Network[IO].connect(address)
          transcript <- Resource.eval(Transcript.create[IO]())
          peer <- Resource.eval(RawPeer.create(socket, transcript))
        } yield new ClientDriver[IO] {
          def exchange(request: RequestData): Resource[IO, ResponseData[IO]] =
            Resource.makeFull[IO, ResponseData[IO]](poll => poll(script(peer, request)))(_ =>
              onRelease
            )
          def exchangeStream(
              request: RequestData,
              body: FixtureCleanup[IO] => Stream[IO, Byte],
          ): Resource[IO, ResponseData[IO]] = exchange(request)
        }
    }
    Transcript.create[IO]().flatMap(t => selected(id).run(CaseContext(target, t)))
  }
}
