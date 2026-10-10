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
import cats.syntax.all._
import fs2.Chunk
import scodec.bits.ByteVector

import java.util.concurrent.TimeoutException
import scala.concurrent.duration._

/** A small end-to-end check of each protocol and target role, using independent peers. */
object SmokeCases {
  private val Body = Http1Wire.ascii("abc")
  private val PingPayload = ByteVector(0, 17, 34, 51, 68, 85, 102, 127)
  private val EmptyRequest = RequestData(headers = Vector("Content-Length" -> "0"))

  val all: Vector[WireCase] = Vector(
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-2.1
    WireCase(
      "h1.server.get",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "2.1", "A complete origin-form HTTP/1.1 request reaches the application."),
    ) { context =>
      context.server(Protocol.Http1).use { case (peer, server) =>
        for {
          // RFC 9112 Sections 2.1 and 3.2.1: an ordinary origin-form request, no body.
          _ <- peer.writeAscii(
            "GET /smoke?q=1 HTTP/1.1\r\nHost: wire.test\r\nX-Wire: token\r\n\r\n"
          )
          response <- Http1Wire.readFinalResponse(peer)
          received <- server.received
          _ <- Check.equal(response.status, 200)
          _ <- Check.equal(response.body, Http1Wire.ascii("accepted"))
          _ <- Check.equal(received.method, "GET")
          _ <- Check.equal(received.path, "/smoke?q=1")
          _ <- Check(
            received.headers.exists { case (name, value) =>
              name.equalsIgnoreCase("x-wire") && value == "token"
            },
            "the application did not receive the request header",
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
    WireCase(
      "h1.server.reject-field-name-whitespace",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "5.1", "Whitespace before a request field's colon requires a 400 response."),
    ) { context =>
      context.server(Protocol.Http1).use { case (peer, _) =>
        for {
          // RFC 9112 Section 5.1: the single invalid octet is the SP before this colon.
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nX-Wire : token\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.fixed-length",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "6.3", "A valid Content-Length delimits the response body in octets."),
    ) { context =>
      scriptedClient(context, Protocol.Http1)(expectBody) { peer =>
        readHttp1Request(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabc"
        )
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.reject-conflicting-content-length",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "6.3", "Conflicting Content-Length values require discarding the response."),
    ) { context =>
      scriptedClient(context, Protocol.Http1)(expectRejection) { peer =>
        // Both declared lengths are fully available, so a premature EOF cannot cause rejection.
        readHttp1Request(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nContent-Length: 3\r\n\r\nabc"
        )
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.server.ping",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.7", "A non-ACK PING requires an ACK PING carrying the identical payload."),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        Http2Wire.clientHandshake(peer) *> pingRoundTrip(peer)
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.server.reject-ping-length",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.7", "A PING payload of seven octets causes a connection error."),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- Http2Wire.clientHandshake(peer)
          // Establish a healthy connection before changing only the next PING's payload length.
          _ <- pingRoundTrip(peer)
          _ <- Http2Wire
            .writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, PingPayload.take(7)))
          // Section 6.8 permits closing without GOAWAY. Require actual EOF in either case.
          // Timeout and codec errors remain failures, never evidence of rejection.
          bytes <- peer.readToEnd(65536).timeout(5.seconds)
          frames <- IO.fromEither(Http2Wire.decodeAll(bytes))
          _ <- frames.traverse_(checkConnectionRejection)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.headers-and-data",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.1", "A response consists of a field block followed by DATA through END_STREAM."),
    ) { context =>
      scriptedClient(context, Protocol.Http2)(expectBody) { peer =>
        readHttp2Request(peer).flatMap { streamId =>
          Http2Wire.writeFrame(
            peer,
            Http2Wire.headers(
              streamId,
              Vector(":status" -> "200", "content-length" -> "3"),
              endStream = false,
            ),
          ) *>
            Http2Wire.writeFrame(peer, Http2Wire.data(streamId, Body))
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1
    WireCase(
      "h2.client.reject-content-length-mismatch",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.1.1", "Clients reject a response whose content-length disagrees with its DATA."),
    ) { context =>
      scriptedClient(context, Protocol.Http2)(expectRejection) { peer =>
        readHttp2Request(peer).flatMap { streamId =>
          // A complete response stream with three DATA octets, but a declared length of four.
          for {
            _ <- Http2Wire.writeFrame(
              peer,
              Http2Wire.headers(
                streamId,
                Vector(":status" -> "200", "content-length" -> "4"),
                endStream = false,
              ),
            )
            _ <- Http2Wire.writeFrame(peer, Http2Wire.data(streamId, Body))
            bytes <- peer.readToEnd(65536)
            frames <- IO.fromEither(Http2Wire.decodeAll(bytes))
            _ <- frames.traverse_(checkStreamRejection(_, streamId))
          } yield ()
        }
      }
    },
  )

  /** Keep the scripted socket open until the target has completed its observable operation. */
  private def scriptedClient(
      context: CaseContext,
      protocol: Protocol,
  )(exercise: ClientDriver[IO] => IO[Unit])(serve: RawPeer[IO] => IO[Unit]): IO[Unit] =
    Deferred[IO, Unit]
      .flatMap { completed =>
        context.client(protocol)(driver => exercise(driver).guarantee(completed.complete(()).void))(
          peer => serve(peer) *> completed.get
        )
      }
      .timeout(5.seconds)

  private def expectBody(driver: ClientDriver[IO]): IO[Unit] =
    driver.exchange(EmptyRequest).use { response =>
      for {
        body <- response.body.compile.to(Chunk)
        _ <- Check.equal(response.status, 200)
        _ <- Check.equal(body.toByteVector, Body)
      } yield ()
    }

  private def expectRejection(driver: ClientDriver[IO]): IO[Unit] =
    // Only the target operation is attempted. Script, handshake, oracle, and deadline errors
    // cannot be mistaken for a target rejecting the deliberately malformed response.
    driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap {
      case Left(error) =>
        Check(!causedByTimeout(error), s"a timeout is not protocol rejection: $error")
      case Right(_) => Check(false, "the target successfully consumed a malformed response")
    }

  private def causedByTimeout(error: Throwable): Boolean = {
    @annotation.tailrec
    def loop(current: Throwable, remaining: Int): Boolean =
      if (current == null || remaining == 0) false
      else if (current.isInstanceOf[TimeoutException]) true
      else loop(current.getCause, remaining - 1)
    loop(error, 16)
  }

  private def readHttp1Request(peer: RawPeer[IO]): IO[Unit] =
    Http1Wire.readRequest(peer).flatMap { request =>
      Check.equal(request.method, "GET") *> Check.equal(request.target, "/") *>
        Check.equal(request.body, ByteVector.empty)
    }

  private def readHttp2Request(peer: RawPeer[IO]): IO[Int] =
    for {
      _ <- Http2Wire.serverHandshake(peer)
      first <- Http2Wire.readUntil(peer)(_.frameType == Http2Wire.Headers)
      decoder <- HpackWire.decoder[IO]()
      fields <- Http2Wire.readHeaders(peer, first, decoder)
      _ <- Check(
        first.streamId > 0 && (first.streamId & 1) == 1,
        "client requests require a positive odd stream identifier",
      )
      _ <- Check.equal(fields.collect { case (":method", value) => value }, Vector("GET"))
      _ <- Check.equal(fields.collect { case (":path", value) => value }, Vector("/"))
    } yield first.streamId

  private def pingRoundTrip(peer: RawPeer[IO]): IO[Unit] =
    for {
      _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, PingPayload))
      response <- Http2Wire.readUntil(peer)(frame =>
        frame.frameType == Http2Wire.Ping && frame.hasFlag(Http2Wire.Ack)
      )
      _ <- Check.equal(response.streamId, 0)
      _ <- Check.equal(response.payload, PingPayload)
    } yield ()

  private def checkStreamRejection(frame: Http2Wire.Frame, streamId: Int): IO[Unit] =
    frame.frameType match {
      case Http2Wire.RstStream =>
        for {
          _ <- Check.equal(frame.streamId, streamId)
          _ <- Check.equal(frame.payload.size, 4L)
          code = Http2Wire.readUint32(frame.payload)
          _ <- Check(Set(1L, 2L).contains(code), s"unexpected RST_STREAM error code: $code")
        } yield ()
      case Http2Wire.GoAway =>
        for {
          _ <- Check.equal(frame.streamId, 0)
          _ <- Check(frame.payload.size >= 8L, "GOAWAY payload must contain its two fixed fields")
          code = Http2Wire.readUint32(frame.payload.slice(4, 8))
          _ <- Check(Set(0L, 1L, 2L).contains(code), s"unexpected GOAWAY error code: $code")
        } yield ()
      // Section 5.4 allows escalating a stream error to connection closure. The target's
      // failed exchange proves rejection; optional error frames provide independent evidence.
      // A NO_ERROR GOAWAY can accompany the subsequent, deliberately initiated client cleanup.
      case _ => IO.unit
    }

  private def checkConnectionRejection(frame: Http2Wire.Frame): IO[Unit] =
    frame.frameType match {
      case Http2Wire.GoAway =>
        for {
          _ <- Check.equal(frame.streamId, 0)
          _ <- Check(frame.payload.size >= 8L, "GOAWAY payload must contain its two fixed fields")
          code = Http2Wire.readUint32(frame.payload.slice(4, 8))
          // RFC 9113 Section 5.4 permits generic codes in place of the specific size error.
          _ <- Check(Set(1L, 2L, 6L).contains(code), s"unexpected GOAWAY error code: $code")
        } yield ()
      case Http2Wire.Ping =>
        Check(!frame.hasFlag(Http2Wire.Ack), "an invalid-length PING must not be acknowledged")
      case Http2Wire.Settings | Http2Wire.WindowUpdate => IO.unit
      case kind if kind > Http2Wire.Continuation => IO.unit
      case _ => Check(false, s"unexpected frame while rejecting malformed PING: $frame")
    }
}
