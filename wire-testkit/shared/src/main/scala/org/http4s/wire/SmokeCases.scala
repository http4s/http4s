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
      scriptedClient(context, Protocol.Http1)(driver => expectRejection(driver, IO.unit)) { peer =>
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
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2).use { case (peer, _) =>
          Http2Wire.clientHandshake(peer) *> pingRoundTrip(peer)
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.server.reject-ping-length",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.7", "A PING payload of seven octets causes a connection error."),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2).use { case (peer, _) =>
          for {
            _ <- Http2Wire.clientHandshake(peer)
            // Establish a healthy connection before changing only the next PING's payload length.
            _ <- pingRoundTrip(peer)
            _ <- Http2Wire
              .writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, PingPayload.take(7)))
            // Section 6.8 permits closing without GOAWAY. Require actual EOF in either case.
            // Timeout and codec errors remain failures, never evidence of rejection.
            bytes <- peer.readToEnd(65536, allowReset = true).timeout(5.seconds)
            frames <- IO.fromEither(Http2Wire.decodeAll(bytes))
            _ <- checkConnectionRejection(peer, frames)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.headers-and-data",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.1", "A response consists of a field block followed by DATA through END_STREAM."),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(3L)
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
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1
    WireCase(
      "h2.client.reject-content-length-mismatch",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.1.1", "Clients reject a response whose content-length disagrees with its DATA."),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(3L)
        Deferred[IO, Unit].flatMap { observed =>
          context.client(Protocol.Http2)(driver => expectRejection(driver, observed.get)) { peer =>
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
                _ <- requireStreamRejection(peer, streamId)
                _ <- observed.complete(()).void
              } yield ()
            }
          }
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
    driver
      .exchange(EmptyRequest)
      .use { response =>
        for {
          body <- response.body.compile.to(Chunk)
          _ <- Check.equal(response.status, 200)
          _ <- Check.equal(body.toByteVector, Body)
        } yield ()
      }
      .handleErrorWith { error =>
        RawPeer.raiseAdapterOrTimeout[IO](error) *> (error match {
          case error: ProtocolFailure => IO.raiseError(error)
          case error: WireDecodingFailure => IO.raiseError(error)
          case error: WireObservationLimit => IO.raiseError(error)
          case _ => Check(false, s"target rejected a valid response: $error")
        })
      }

  private def expectRejection(driver: ClientDriver[IO], observed: IO[Unit]): IO[Unit] =
    // Classify acquisition/consumption and await independent wire evidence before release.
    // Cleanup, script, handshake, oracle, and deadline errors cannot prove target rejection.
    ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
      result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *> (result match {
        case Left(error: ProtocolFailure) => IO.raiseError(error)
        case Left(error: WireDecodingFailure) => IO.raiseError(error)
        case Left(error: WireObservationLimit) => IO.raiseError(error)
        case Left(error) => RawPeer.raiseAdapterOrTimeout[IO](error)
        case Right(_) => Check(false, "the target successfully consumed a malformed response")
      }) *> observed
    }

  private def readHttp1Request(peer: RawPeer[IO]): IO[Unit] =
    Http1Wire.readRequest(peer).flatMap { request =>
      Check.equal(request.method, "GET") *> Check.equal(request.target, "/") *>
        Check.equal(request.body, ByteVector.empty)
    }

  private def readHttp2Request(
      peer: RawPeer[IO]
  )(implicit peerSettings: Http2PeerSettings[IO], dataBudget: Http2Wire.DataBudget): IO[Int] =
    for {
      _ <- Http2Wire.serverHandshake(peer)
      first <- Http2PositiveObservation.readUntil(peer, fromServer = false)(
        _.frameType == Http2Wire.Headers
      )
      decoder <- HpackWire.decoder[IO]()
      fields <- Http2Wire.readHeaders(peer, first, decoder)
      _ <- Check(
        first.streamId > 0 && (first.streamId & 1) == 1,
        "client requests require a positive odd stream identifier",
      )
      _ <- Check.equal(fields.collect { case (":method", value) => value }, Vector("GET"))
      _ <- Check.equal(fields.collect { case (":path", value) => value }, Vector("/"))
    } yield first.streamId

  private def pingRoundTrip(
      peer: RawPeer[IO]
  )(implicit peerSettings: Http2PeerSettings[IO], dataBudget: Http2Wire.DataBudget): IO[Unit] =
    for {
      _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, PingPayload))
      response <- Http2PositiveObservation.readUntil(peer, fromServer = true)(frame =>
        frame.frameType == Http2Wire.Ping && frame.hasFlag(Http2Wire.Ack)
      )
      _ <- Check.equal(response.streamId, 0)
      _ <- Check.equal(response.payload, PingPayload)
    } yield ()

  private def requireStreamRejection(peer: RawPeer[IO], streamId: Int)(implicit
      peerSettings: Http2PeerSettings[IO]
  ): IO[Unit] = peerSettings.positive(peer).flatMap { observation =>
    def checked(
        frame: Http2Wire.Frame
    ): IO[Http2RejectionChecks.State] =
      observation
        .observeControl(frame, fromServer = false)
        .flatTap(_ => checkStreamRejection(frame, streamId))
    def loop(remaining: Int, closing: Boolean): IO[Unit] =
      observationBound(remaining > 0, "too many frames without required protocol rejection") *>
        Http2Wire.readFrameOrClose(peer).flatMap {
          case None => IO.unit // A stream error may be escalated to transport termination.
          case Some(frame) =>
            checked(frame).flatMap { _ =>
              frame.frameType match {
                case Http2Wire.RstStream =>
                  if (closing) loop(remaining - 1, closing) else IO.unit
                case Http2Wire.GoAway if Http2Wire.readUint32(frame.payload.slice(4, 8)) != 0L =>
                  peer
                    .readToEnd(65536, allowReset = true)
                    .flatMap(bytes => IO.fromEither(Http2Wire.decodeAll(bytes)))
                    .flatMap(_.traverse_(checked))
                    .void
                case _ =>
                  (if (closing) IO.pure(false)
                   else if (frame.frameType == Http2Wire.Settings && !frame.hasFlag(Http2Wire.Ack))
                     Http2RejectionWrite.reply(
                       Http2Wire.writeFrame(
                         peer,
                         Http2Wire.settings(ack = true),
                       )
                     )
                   else if (frame.frameType == Http2Wire.Ping && !frame.hasFlag(Http2Wire.Ack))
                     Http2RejectionWrite.reply(
                       Http2Wire.writeFrame(peer, frame.copy(flags = Http2Wire.Ack))
                     )
                   else IO.pure(true))
                    .flatMap(written => loop(remaining - 1, closing || !written))
              }
            }
        }
    loop(1024, closing = false)
  }

  // Both callers validate the complete control envelope before these verdict-specific checks.
  private def checkStreamRejection(frame: Http2Wire.Frame, streamId: Int): IO[Unit] =
    frame.frameType match {
      case Http2Wire.RstStream =>
        Check.equal(frame.streamId, streamId) *> IO.defer {
          val code = Http2Wire.readUint32(frame.payload)
          Check(code == 1L || code == 2L, s"unexpected RST_STREAM error code: $code")
        }
      case Http2Wire.GoAway =>
        IO.defer {
          val code = Http2Wire.readUint32(frame.payload.slice(4, 8))
          Check(code == 0L || code == 1L || code == 2L, s"unexpected GOAWAY error code: $code")
        }
      case _ => IO.unit
    }

  private def checkConnectionRejection(peer: RawPeer[IO], frames: Vector[Http2Wire.Frame])(implicit
      peerSettings: Http2PeerSettings[IO]
  ): IO[Unit] = peerSettings.positive(peer).flatMap { observation =>
    frames.traverse_ { frame =>
      observation.observeControl(frame, fromServer = true).flatTap { _ =>
        frame.frameType match {
          case Http2Wire.GoAway =>
            val code = Http2Wire.readUint32(frame.payload.slice(4, 8))
            // Section 5.4 permits generic codes in place of the specific size error.
            Check(
              code == 0L || code == 1L || code == 2L || code == 6L,
              s"unexpected GOAWAY error code: $code",
            )
          case Http2Wire.Ping =>
            Check(
              !frame.hasFlag(Http2Wire.Ack),
              "an invalid-length PING must not be acknowledged",
            )
          case Http2Wire.Settings | Http2Wire.WindowUpdate | Http2Wire.Priority => IO.unit
          case kind if kind > Http2Wire.Continuation => IO.unit
          case _ => Check(false, s"unexpected frame while rejecting malformed PING: $frame")
        }
      }
    }.void
  }

  private def observationBound(condition: Boolean, detail: String): IO[Unit] =
    if (condition) IO.unit else IO.raiseError(new WireObservationLimit(detail))

}
