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
import fs2.Stream
import scodec.bits.ByteVector

/** Stream-state and flow-control oracles. Credit counts complete DATA payloads, not frame headers. */
object Http2StateCases {
  import Http2Wire._

  private val SmallBody = Http1Wire.ascii("abcdefghijklmnop")
  private val LargeBody = ByteVector.fill(65536)(0x61.toByte)
  private val PingBytes = ByteVector(19, 37, 55, 73, 91, 109, 127, 17)
  private val CompletionFrameLimit = 4096

  // Each DATA frame can charge one Pad Length octet and at most 255 padding
  // octets in addition to content. Provision this before the bounded read;
  // credit already observed (including padding) remains additive.
  private def completionBudget(expected: ByteVector, progress: Progress): Int =
    (math.max(0L, expected.size - progress.body.size) + CompletionFrameLimit * 256L).toInt

  private def completionIncrement(expected: ByteVector, progress: Progress): Int =
    math.max(0L, completionBudget(expected, progress).toLong - progress.credit).toInt

  private val EmptyPost = RequestData(method = "POST", headers = Vector("content-length" -> "0"))

  val all: Vector[WireCase] = Vector(
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.server.idle-data",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "5.1", "DATA on an idle stream is a connection PROTOCOL_ERROR."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2).use { case (peer, _) =>
          connectServer(peer) *> writeFrame(peer, data(1, ByteVector.empty)) *>
            connectionError(peer, Set(1L, 2L), fromServer = true)
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.server.idle-window-update",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "5.1", "WINDOW_UPDATE on an idle stream is a connection PROTOCOL_ERROR."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2).use { case (peer, _) =>
          connectServer(peer) *> writeFrame(peer, windowUpdate(1, 1)) *>
            connectionError(peer, Set(1L, 2L), fromServer = true)
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1.1
    WireCase(
      "h2.server.even-request-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "5.1.1", "Client-initiated streams require odd identifiers."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2).use { case (peer, _) =>
          connectServer(peer) *> writeFrame(peer, headers(2, requestFields())) *>
            connectionError(peer, Set(1L, 2L), fromServer = true)
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1.1
    WireCase(
      "h2.server.skipped-stream-cannot-open",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "5.1.1",
        "Opening stream 3 closes skipped stream 1; stream 1 cannot then be opened.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2).use { case (peer, _) =>
          for {
            _ <- connectServer(peer, Vector(4 -> 0L))
            decoder <- HpackWire.decoder[IO]()
            _ <- writeFrame(peer, headers(3, requestFields()))
            _ <- responseHead(peer, 3, decoder)
            _ <- writeFrame(peer, headers(1, requestFields()))
            _ <- connectionError(peer, Set(1L, 2L), fromServer = true)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.client.idle-data",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "5.1", "A client treats DATA on an uncreated stream as a connection error."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = false) { implicit peerSettings =>
        rejectedClient(context) { peer =>
          for {
            _ <- acceptClient(peer)
            request <- requestHead(peer)
            _ <- writeFrame(peer, data(request.streamId + 2, ByteVector.empty))
            _ <- connectionError(peer, Set(1L, 2L), fromServer = false)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.client.idle-window-update",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "5.1", "A client treats WINDOW_UPDATE on an idle stream as a connection error."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = false) { implicit peerSettings =>
        rejectedClient(context) { peer =>
          for {
            _ <- acceptClient(peer)
            request <- requestHead(peer)
            _ <- writeFrame(peer, windowUpdate(request.streamId + 2, 1))
            _ <- connectionError(peer, Set(1L, 2L), fromServer = false)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.client.unpromised-server-headers",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "5.1", "HEADERS cannot open an unpromised server-initiated stream."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = false) { implicit peerSettings =>
        rejectedClient(context) { peer =>
          acceptClient(peer) *> requestHead(peer) *>
            writeFrame(peer, headers(2, Vector(":status" -> "200"))) *>
            connectionError(peer, Set(1L, 2L), fromServer = false)
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.server.half-closed-remote-data",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "5.1", "DATA after the peer's END_STREAM requires STREAM_CLOSED."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use {
          case (peer, _) =>
            for {
              // Zero response credit holds the target in half-closed (remote), not closed.
              _ <- connectServer(peer, Vector(4 -> 0L))
              decoder <- HpackWire.decoder[IO]()
              _ <- writeFrame(peer, headers(1, requestFields()))
              _ <- responseHead(peer, 1, decoder)
              _ <- writeFrame(peer, data(1, ByteVector.empty))
              _ <- streamError(peer, 1, Set(1L, 2L, 5L), fromServer = true)
            } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.server.half-closed-remote-headers",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "5.1", "HEADERS after the peer's END_STREAM requires STREAM_CLOSED."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use {
          case (peer, _) =>
            for {
              _ <- connectServer(peer, Vector(4 -> 0L))
              decoder <- HpackWire.decoder[IO]()
              _ <- writeFrame(peer, headers(1, requestFields()))
              _ <- responseHead(peer, 1, decoder)
              _ <- writeFrame(peer, headers(1, Vector("x-after-end" -> "invalid")))
              _ <- streamError(peer, 1, Set(1L, 2L, 5L), fromServer = true)
            } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9
    WireCase(
      "h2.server.window-update-after-peer-end",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.9", "WINDOW_UPDATE remains valid after the peer sends END_STREAM."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use {
          case (peer, _) =>
            for {
              _ <- connectServer(peer, Vector(4 -> 0L))
              decoder <- HpackWire.decoder[IO]()
              _ <- writeFrame(peer, headers(1, requestFields()))
              _ <- responseHead(peer, 1, decoder)
              stopped <- barrier(peer, true, decoder, 1, Progress(0))
              increment = completionIncrement(SmallBody, stopped)
              _ <- writeFrame(peer, windowUpdate(0, completionBudget(SmallBody, stopped)))
              _ <- if (increment > 0) writeFrame(peer, windowUpdate(1, increment)) else IO.unit
              complete <- readData(
                peer,
                true,
                decoder,
                1,
                stopped.copy(credit = stopped.credit + increment),
                boundary = false,
              )
              _ <- Check.equal(complete.body, SmallBody)
            } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1
    WireCase(
      "h2.server.stream-credit-boundary",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.9.1",
        "Each DATA payload fits the remaining stream credit and WINDOW_UPDATE restores credit.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use {
          case (peer, _) =>
            for {
              _ <- connectServer(peer, Vector(4 -> 3L))
              decoder <- HpackWire.decoder[IO]()
              _ <- writeFrame(peer, headers(1, requestFields()))
              _ <- responseHead(peer, 1, decoder)
              stopped <- barrier(peer, true, decoder, 1, Progress(3))
              increment = completionIncrement(SmallBody, stopped)
              _ <- writeFrame(peer, windowUpdate(0, completionBudget(SmallBody, stopped)))
              _ <- if (increment > 0) writeFrame(peer, windowUpdate(1, increment)) else IO.unit
              complete <- readData(
                peer,
                true,
                decoder,
                1,
                stopped.copy(credit = stopped.credit + increment),
                boundary = false,
              )
              _ <- Check.equal(complete.body, SmallBody)
            } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1
    WireCase(
      "h2.client.stream-credit-boundary",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.9.1",
        "An upload fits stream credit, processes PING while blocked, and resumes on WINDOW_UPDATE.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { ready =>
          context.client(Protocol.Http2)(driver => upload(driver, ready, SmallBody)) { peer =>
            for {
              decoder <- HpackWire.decoder[IO]()
              stream <- openUpload(peer, decoder, Vector(4 -> 3L))
              _ <- ready.complete(())
              stopped <- barrier(peer, false, decoder, stream, Progress(3))
              increment = completionIncrement(SmallBody, stopped)
              _ <- writeFrame(peer, windowUpdate(0, completionBudget(SmallBody, stopped)))
              _ <- if (increment > 0) writeFrame(peer, windowUpdate(stream, increment)) else IO.unit
              complete <- readData(
                peer,
                false,
                decoder,
                stream,
                stopped.copy(credit = stopped.credit + increment),
                boundary = false,
              )
              _ <- Check.equal(complete.body, SmallBody)
              _ <- writeFrame(peer, headers(stream, Vector(":status" -> "200")))
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2
    WireCase(
      "h2.server.connection-credit-independent",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.9.2",
        "Only connection WINDOW_UPDATE replenishes the initial 65535 connection octets.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.Reply(body = LargeBody)).use {
          case (peer, _) =>
            for {
              _ <- connectServer(peer, Vector(4 -> 131072L))
              decoder <- HpackWire.decoder[IO]()
              _ <- writeFrame(peer, headers(1, requestFields()))
              _ <- responseHead(peer, 1, decoder)
              stopped <- barrier(peer, true, decoder, 1, Progress(65535))
              // Neither a stream update nor SETTINGS may alter the connection window.
              _ <- Http2PeerSettings
                .write(peer, encode(windowUpdate(1, 1)) ++ encode(settings(Vector(4 -> 262144L))))
              unchanged <- settingsAck(peer, true, decoder, 1, stopped)
              confirmed <- barrier(peer, true, decoder, 1, unchanged)
              increment = completionIncrement(LargeBody, confirmed)
              _ <- writeFrame(peer, windowUpdate(1, completionBudget(LargeBody, confirmed)))
              _ <- if (increment > 0) writeFrame(peer, windowUpdate(0, increment)) else IO.unit
              complete <- readData(
                peer,
                true,
                decoder,
                1,
                confirmed.copy(credit = confirmed.credit + increment),
                boundary = false,
              )
              _ <- Check.equal(complete.body, LargeBody)
            } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2
    WireCase(
      "h2.client.connection-credit-independent",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.9.2",
        "SETTINGS and stream WINDOW_UPDATE do not replenish measured upload connection credit.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { ready =>
          context.client(Protocol.Http2)(driver => upload(driver, ready, LargeBody)) { peer =>
            for {
              decoder <- HpackWire.decoder[IO]()
              stream <- openUpload(peer, decoder, Vector(4 -> 131072L))
              _ <- ready.complete(())
              stopped <- barrier(peer, false, decoder, stream, Progress(65535))
              _ <- Http2PeerSettings.write(
                peer,
                encode(windowUpdate(stream, 1)) ++ encode(settings(Vector(4 -> 262144L))),
              )
              unchanged <- settingsAck(peer, false, decoder, stream, stopped)
              confirmed <- barrier(peer, false, decoder, stream, unchanged)
              increment = completionIncrement(LargeBody, confirmed)
              _ <- writeFrame(peer, windowUpdate(stream, completionBudget(LargeBody, confirmed)))
              _ <- if (increment > 0) writeFrame(peer, windowUpdate(0, increment)) else IO.unit
              complete <- readData(
                peer,
                false,
                decoder,
                stream,
                confirmed.copy(credit = confirmed.credit + increment),
                boundary = false,
              )
              _ <- Check.equal(complete.body, LargeBody)
              _ <- writeFrame(peer, headers(stream, Vector(":status" -> "200")))
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2
    WireCase(
      "h2.server.negative-stream-window",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.9.2",
        "A SETTINGS reduction preserves a negative stream window until credit becomes positive.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.Reply(body = LargeBody)).use {
          case (peer, _) =>
            for {
              _ <- connectServer(peer, Vector(4 -> 65535L))
              decoder <- HpackWire.decoder[IO]()
              _ <- writeFrame(peer, headers(1, requestFields()))
              _ <- responseHead(peer, 1, decoder)
              prefix <- firstPositiveData(peer, true, decoder, 1, Progress(65535))
              sampled <- barrier(peer, true, decoder, 1, prefix)
              _ <- Check(!sampled.ended, "body ended before its complete content")
              // Keep connection credit sufficient even for a padding-only prefix.
              _ <- writeFrame(peer, windowUpdate(0, 65536))
              _ <- writeFrame(peer, settings(Vector(4 -> 0L)))
              // RFC 9113 6.9.3 permits in-flight DATA under the old window.
              // Its ACK synchronizes application of the new-minus-old delta (6.5.3).
              acknowledged <- settingsAck(peer, true, decoder, 1, sampled)
              negative = acknowledged.copy(credit = acknowledged.credit - 65535)
              _ <- observationBound(negative.credit < 0, "negative stream credit was not observed")
              negativeStopped <- barrier(peer, true, decoder, 1, negative)
              _ <- writeFrame(peer, windowUpdate(1, (-negativeStopped.credit).toInt))
              zero <- barrier(peer, true, decoder, 1, negativeStopped.copy(credit = 0))
              _ <- writeFrame(peer, windowUpdate(1, 1))
              stopped <- barrier(peer, true, decoder, 1, zero.copy(credit = zero.credit + 1))
              increment = completionIncrement(LargeBody, stopped)
              _ <- writeFrame(peer, windowUpdate(0, completionBudget(LargeBody, stopped)))
              _ <- if (increment > 0) writeFrame(peer, windowUpdate(1, increment)) else IO.unit
              complete <- readData(
                peer,
                true,
                decoder,
                1,
                stopped.copy(credit = stopped.credit + increment),
                boundary = false,
              )
              _ <- Check.equal(complete.body, LargeBody)
            } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2
    WireCase(
      "h2.client.negative-stream-window",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.9.2",
        "A client retains negative upload credit across SETTINGS and partial WINDOW_UPDATE.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { ready =>
          context.client(Protocol.Http2)(driver => upload(driver, ready, LargeBody)) { peer =>
            for {
              decoder <- HpackWire.decoder[IO]()
              stream <- openUpload(peer, decoder, Vector(4 -> 65535L))
              _ <- ready.complete(())
              prefix <- firstPositiveData(peer, false, decoder, stream, Progress(65535))
              sampled <- barrier(peer, false, decoder, stream, prefix)
              _ <- Check(!sampled.ended, "body ended before its complete content")
              // Keep connection credit sufficient even for a padding-only prefix.
              _ <- writeFrame(peer, windowUpdate(0, 65536))
              _ <- writeFrame(peer, settings(Vector(4 -> 0L)))
              // RFC 9113 6.9.3 permits in-flight DATA under the old window.
              // Its ACK synchronizes application of the new-minus-old delta (6.5.3).
              acknowledged <- settingsAck(peer, false, decoder, stream, sampled)
              negative = acknowledged.copy(credit = acknowledged.credit - 65535)
              _ <- observationBound(negative.credit < 0, "negative stream credit was not observed")
              negativeStopped <- barrier(peer, false, decoder, stream, negative)
              _ <- writeFrame(peer, windowUpdate(stream, (-negativeStopped.credit).toInt))
              zero <- barrier(peer, false, decoder, stream, negativeStopped.copy(credit = 0))
              _ <- writeFrame(peer, windowUpdate(stream, 1))
              stopped <- barrier(peer, false, decoder, stream, zero.copy(credit = zero.credit + 1))
              increment = completionIncrement(LargeBody, stopped)
              _ <- writeFrame(peer, windowUpdate(0, completionBudget(LargeBody, stopped)))
              _ <- if (increment > 0) writeFrame(peer, windowUpdate(stream, increment)) else IO.unit
              complete <- readData(
                peer,
                false,
                decoder,
                stream,
                stopped.copy(credit = stopped.credit + increment),
                boundary = false,
              )
              _ <- Check.equal(complete.body, LargeBody)
              _ <- writeFrame(peer, headers(stream, Vector(":status" -> "200")))
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9
    WireCase(
      "h2.server.zero-connection-window-update",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.9",
        "A zero connection WINDOW_UPDATE increment requires a connection PROTOCOL_ERROR.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2).use { case (peer, _) =>
          connectServer(peer) *> writeFrame(peer, Frame(WindowUpdate, 0, 0, uint32(0))) *>
            connectionError(peer, Set(1L, 2L), fromServer = true)
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9
    WireCase(
      "h2.client.zero-connection-window-update",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.9",
        "A client rejects a zero connection WINDOW_UPDATE increment as a connection error.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = false) { implicit peerSettings =>
        rejectedClient(context) { peer =>
          acceptClient(peer) *> requestHead(peer) *>
            writeFrame(peer, Frame(WindowUpdate, 0, 0, uint32(0))) *>
            connectionError(peer, Set(1L, 2L), fromServer = false)
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9
    WireCase(
      "h2.server.zero-stream-window-update",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.9",
        "A zero WINDOW_UPDATE increment on an open stream requires a stream PROTOCOL_ERROR.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(1L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- connectServer(peer)
            decoder <- HpackWire.decoder[IO]()
            _ <- writeFrame(peer, headers(1, unfinishedPostFields, endStream = false))
            _ <- barrier(peer, true, decoder, 1, Progress(0))
            // Complete the request even on a nonconforming target; EchoBody must not be abandoned.
            _ <- peer
              .write(
                encode(Frame(WindowUpdate, 0, 1, uint32(0))) ++ encode(data(1, ByteVector(97)))
              )
            _ <- streamError(peer, 1, Set(1L, 2L), fromServer = true)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9
    WireCase(
      "h2.client.zero-stream-window-update",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.9",
        "A zero WINDOW_UPDATE increment on an open upload requires a stream PROTOCOL_ERROR.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = false) { implicit peerSettings =>
        rejectedUpload(context) { peer =>
          for {
            decoder <- HpackWire.decoder[IO]()
            stream <- openUpload(peer, decoder, Vector(4 -> 0L))
            _ <- writeFrame(peer, Frame(WindowUpdate, 0, stream, uint32(0)))
            _ <- streamError(peer, stream, Set(1L, 2L), fromServer = false)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1
    WireCase(
      "h2.server.connection-window-overflow",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.9.1", "WINDOW_UPDATE cannot increase connection credit above 2^31-1."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2).use { case (peer, _) =>
          // With no streams or DATA, the connection window is exactly 65535.
          connectServer(peer) *> writeFrame(peer, windowUpdate(0, Int.MaxValue)) *>
            connectionError(peer, Set(1L, 2L, 3L), fromServer = true)
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1
    WireCase(
      "h2.client.connection-window-overflow",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.9.1", "Connection WINDOW_UPDATE overflow requires FLOW_CONTROL_ERROR."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = false) { implicit peerSettings =>
        rejectedClient(context) { peer =>
          // The request contains no DATA octets, leaving the full initial connection window.
          acceptClient(peer) *> requestHead(peer) *>
            writeFrame(peer, windowUpdate(0, Int.MaxValue)) *>
            connectionError(peer, Set(1L, 2L, 3L), fromServer = false)
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1
    WireCase(
      "h2.server.stream-window-overflow",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.9.1",
        "Two updates from zero to 2^31-1 and then 2^31 require stream FLOW_CONTROL_ERROR.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(1L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- connectServer(peer, Vector(4 -> 0L))
            decoder <- HpackWire.decoder[IO]()
            _ <- writeFrame(peer, headers(1, unfinishedPostFields, endStream = false))
            _ <- barrier(peer, true, decoder, 1, Progress(0))
            // EchoBody cannot produce DATA before the final request frame, so credit cannot race down.
            _ <- Http2PeerSettings.write(
              peer,
              encode(windowUpdate(1, Int.MaxValue)) ++ encode(windowUpdate(1, 1)) ++
                encode(data(1, ByteVector(97))),
            )
            _ <- streamError(peer, 1, Set(1L, 2L, 3L), fromServer = true)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1
    WireCase(
      "h2.client.stream-window-overflow",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.9.1", "An open upload rejects a stream window above 2^31-1."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = false) { implicit peerSettings =>
        rejectedUpload(context) { peer =>
          for {
            decoder <- HpackWire.decoder[IO]()
            stream <- openUpload(peer, decoder, Vector(4 -> 0L))
            // The upload source remains gated, so no DATA can reduce the window between updates.
            _ <- Http2PeerSettings.write(
              peer,
              encode(windowUpdate(stream, Int.MaxValue)) ++ encode(windowUpdate(stream, 1)),
            )
            _ <- streamError(peer, stream, Set(1L, 2L, 3L), fromServer = false)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2
    WireCase(
      "h2.server.settings-stream-window-overflow",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.9.2",
        "An initial-window SETTINGS change overflowing an active window is a connection error.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(1L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- connectServer(peer, Vector(4 -> 0L))
            decoder <- HpackWire.decoder[IO]()
            _ <- writeFrame(peer, headers(1, unfinishedPostFields, endStream = false))
            _ <- barrier(peer, true, decoder, 1, Progress(0))
            _ <- Http2PeerSettings.write(
              peer,
              encode(windowUpdate(1, Int.MaxValue)) ++ encode(settings(Vector(4 -> 1L))) ++
                encode(data(1, ByteVector(97))),
            )
            _ <- connectionError(peer, Set(1L, 2L, 3L), fromServer = true)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2
    WireCase(
      "h2.client.settings-stream-window-overflow",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.9.2",
        "An initial-window SETTINGS change overflowing an upload window is a connection error.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = false) { implicit peerSettings =>
        rejectedUpload(context) { peer =>
          for {
            decoder <- HpackWire.decoder[IO]()
            stream <- openUpload(peer, decoder, Vector(4 -> 0L))
            _ <- Http2PeerSettings.write(
              peer,
              encode(windowUpdate(stream, Int.MaxValue)) ++ encode(settings(Vector(4 -> 1L))),
            )
            _ <- connectionError(peer, Set(1L, 2L, 3L), fromServer = false)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9
    WireCase(
      "h2.server.maximum-stream-window-update",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.9",
        "The maximum WINDOW_UPDATE increment is valid when the existing window is zero.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use {
          case (peer, _) =>
            for {
              _ <- connectServer(peer, Vector(4 -> 0L))
              decoder <- HpackWire.decoder[IO]()
              _ <- writeFrame(peer, headers(1, requestFields()))
              _ <- responseHead(peer, 1, decoder)
              stopped <- barrier(peer, true, decoder, 1, Progress(0))
              _ <- writeFrame(peer, windowUpdate(1, Int.MaxValue))
              // Track connection credit here; the stream is already at its maximum.
              connection = stopped.copy(credit = 65535)
              increment = completionIncrement(SmallBody, connection)
              _ <- if (increment > 0) writeFrame(peer, windowUpdate(0, increment)) else IO.unit
              complete <- readData(
                peer,
                true,
                decoder,
                1,
                connection.copy(credit = connection.credit + increment),
                boundary = false,
              )
              _ <- Check.equal(complete.body, SmallBody)
            } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9
    WireCase(
      "h2.client.maximum-stream-window-update",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.9", "The maximum WINDOW_UPDATE increment is accepted at zero upload credit."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { ready =>
          context.client(Protocol.Http2)(driver => upload(driver, ready, SmallBody)) { peer =>
            for {
              decoder <- HpackWire.decoder[IO]()
              stream <- openUpload(peer, decoder, Vector(4 -> 0L))
              _ <- ready.complete(())
              stopped <- barrier(peer, false, decoder, stream, Progress(0))
              _ <- writeFrame(peer, windowUpdate(stream, Int.MaxValue))
              // Track connection credit here; the stream is already at its maximum.
              connection = stopped.copy(credit = 65535)
              increment = completionIncrement(SmallBody, connection)
              _ <- if (increment > 0) writeFrame(peer, windowUpdate(0, increment)) else IO.unit
              complete <- readData(
                peer,
                false,
                decoder,
                stream,
                connection.copy(credit = connection.credit + increment),
                boundary = false,
              )
              _ <- Check.equal(complete.body, SmallBody)
              _ <- writeFrame(peer, headers(stream, Vector(":status" -> "200")))
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4
    WireCase(
      "h2.server.reset-closes-blocked-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.4", "After processing RST_STREAM a server cannot resume DATA on that stream."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use {
          case (peer, _) =>
            for {
              _ <- connectServer(peer, Vector(4 -> 0L))
              decoder <- HpackWire.decoder[IO]()
              _ <- writeFrame(peer, headers(1, requestFields()))
              _ <- responseHead(peer, 1, decoder)
              _ <- writeFrame(peer, Frame(RstStream, 0, 1, uint32(8)))
              _ <- resetBarrier(peer, true, decoder, 1)
              // Sample later output after a causal PING barrier; this is not an unbounded
              // proof of reset processing or of the absence of independently scheduled output.
              budget = completionBudget(SmallBody, Progress(0))
              _ <- Http2PeerSettings.write(
                peer,
                encode(windowUpdate(1, 65535)) ++ encode(headers(3, requestFields())) ++
                  encode(windowUpdate(0, budget)) ++ encode(windowUpdate(3, budget)),
              )
              _ <- responseHead(peer, 3, decoder)
              complete <- readData(
                peer,
                true,
                decoder,
                3,
                Progress(budget.toLong),
                boundary = false,
              )
              _ <- Check.equal(complete.body, SmallBody)
              _ <- barrier(peer, true, decoder, 3, complete)
            } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9
    WireCase(
      "h2.server.window-update-on-closed-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.9", "An in-flight WINDOW_UPDATE on a closed stream is not a connection error."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use {
          case (peer, _) =>
            for {
              _ <- connectServer(peer)
              decoder <- HpackWire.decoder[IO]()
              _ <- writeFrame(peer, headers(1, requestFields()))
              _ <- responseHead(peer, 1, decoder)
              initial = Progress(65535)
              firstIncrement = completionIncrement(SmallBody, initial)
              _ <- writeFrame(peer, windowUpdate(1, completionBudget(SmallBody, initial)))
              _ <-
                if (firstIncrement > 0) writeFrame(peer, windowUpdate(0, firstIncrement))
                else IO.unit
              first <- readData(
                peer,
                true,
                decoder,
                1,
                initial.copy(credit = initial.credit + firstIncrement),
                boundary = false,
              )
              _ <- Check.equal(first.body, SmallBody)
              _ <- writeFrame(peer, windowUpdate(1, 1))
              _ <- barrier(peer, true, decoder, 1, first)
              _ <- writeFrame(peer, headers(3, requestFields()))
              _ <- responseHead(peer, 3, decoder)
              // Carry the actual connection remainder into a fresh response state.
              next = Progress(first.credit)
              secondIncrement = completionIncrement(SmallBody, next)
              _ <- writeFrame(peer, windowUpdate(3, completionBudget(SmallBody, next)))
              _ <-
                if (secondIncrement > 0) writeFrame(peer, windowUpdate(0, secondIncrement))
                else IO.unit
              second <- readData(
                peer,
                true,
                decoder,
                3,
                next.copy(credit = next.credit + secondIncrement),
                boundary = false,
              )
              _ <- Check.equal(second.body, SmallBody)
            } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.7
    WireCase(
      "h2.server.goaway-includes-dispatched-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "8.7", "GOAWAY cannot declare an application-dispatched stream unprocessed."),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use {
          case (peer, server) =>
            for {
              _ <- connectServer(peer, Vector(4 -> 0L))
              decoder <- HpackWire.decoder[IO]()
              _ <- writeFrame(peer, headers(7, requestFields()))
              _ <- responseHead(peer, 7, decoder)
              received <- server.received
              _ <- Check.equal(received.path, "/")
              _ <- writeFrame(peer, data(9, ByteVector.empty))
              bytes <- peer.readToEnd(1024 * 1024, allowReset = true)
              frames <- IO.fromEither(decodeAll(bytes))
              _ <- checkConnectionFrames(frames, Set(1L, 2L), fromServer = true)
              _ <- frames.filter(_.frameType == GoAway).traverse_ { frame =>
                Check(
                  (readUint32(frame.payload.take(4)) & 0x7fffffffL) >= 7L,
                  "GOAWAY claimed an application-dispatched request was unprocessed",
                )
              }
            } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1.2
    WireCase(
      "h2.server.peer-concurrency-zero-allows-responses",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "5.1.2",
        "A client's concurrent-stream limit restricts server initiation, not responses on client streams.",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use {
          case (peer, _) =>
            for {
              _ <- connectServer(peer, Vector(3 -> 0L))
              decoder <- HpackWire.decoder[IO]()
              _ <- writeFrame(peer, headers(1, requestFields()))
              _ <- responseHead(peer, 1, decoder)
              initial = Progress(65535)
              increment = completionIncrement(SmallBody, initial)
              _ <- writeFrame(peer, windowUpdate(1, completionBudget(SmallBody, initial)))
              _ <- if (increment > 0) writeFrame(peer, windowUpdate(0, increment)) else IO.unit
              complete <- readData(
                peer,
                true,
                decoder,
                1,
                initial.copy(credit = initial.credit + increment),
                boundary = false,
              )
              _ <- Check.equal(complete.body, SmallBody)
            } yield ()
        }
      }
    },
  )

  private final case class Progress(
      credit: Long,
      body: ByteVector = ByteVector.empty,
      ended: Boolean = false,
  )

  private def requestFields(): Vector[(String, String)] =
    Vector(":method" -> "GET", ":scheme" -> "http", ":authority" -> "wire.test", ":path" -> "/")

  private val unfinishedPostFields =
    Vector(
      ":method" -> "POST",
      ":scheme" -> "http",
      ":authority" -> "wire.test",
      ":path" -> "/",
      "content-length" -> "1",
    )

  /** Finish the peer SETTINGS exchange before any test stream is created. */
  private def connectServer(
      peer: RawPeer[IO],
      values: Vector[(Int, Long)] = Vector.empty,
  )(implicit peerSettings: Http2PeerSettings[IO], dataBudget: Http2Wire.DataBudget): IO[Unit] =
    for {
      // State readers do not consume promised streams; disable push before requests.
      _ <- Http2PeerSettings.write(peer, clientPreface ++ encode(settings(values :+ (2 -> 0L))))
      first <- readFrame(peer)
      _ <- Check(
        first.frameType == Settings && !first.hasFlag(Ack),
        "server preface requires SETTINGS",
      )
      _ <- control(peer, first, fromServer = true)
      decoder <- HpackWire.decoder[IO]()
      _ <- settingsAck(peer, true, decoder, 0, Progress(0))
    } yield ()

  private def acceptClient(
      peer: RawPeer[IO],
      values: Vector[(Int, Long)] = Vector.empty,
  )(implicit peerSettings: Http2PeerSettings[IO], dataBudget: Http2Wire.DataBudget): IO[Unit] =
    for {
      preface <- WireDecodingFailure.read(peer.readExact(clientPreface.size.toInt))
      _ <- Check.equal(preface, clientPreface)
      first <- readFrame(peer)
      _ <- Check(
        first.frameType == Settings && !first.hasFlag(Ack),
        "client preface requires SETTINGS",
      )
      _ <- IO.fromEither(Http2RejectionChecks.validate(first, fromServer = false))
      _ <- Check.equal(first.streamId, 0)
      _ <- Check.equal(first.payload.size % 6L, 0L)
      _ <- IO(dataBudget.check(first))
      _ <- Http2PeerSettings.write(peer, encode(settings(values)) ++ encode(settings(ack = true)))
    } yield ()

  private def requestHead(
      peer: RawPeer[IO]
  )(implicit peerSettings: Http2PeerSettings[IO], dataBudget: Http2Wire.DataBudget): IO[Frame] =
    for {
      first <- readUntil(peer, fromServer = false)(_.frameType == Headers)
      decoder <- HpackWire.decoder[IO]()
      fields <- readHeaders(peer, first, decoder)
      _ <- Check(
        first.streamId > 0 && (first.streamId & 1) == 1,
        "request stream ID must be positive and odd",
      )
      _ <- Check.equal(fields.collect { case (":method", value) => value }, Vector("POST"))
    } yield first

  /** Request HEADERS and SETTINGS ACK can arrive in either order; no upload byte is released yet. */
  private def openUpload(
      peer: RawPeer[IO],
      decoder: HpackWire.Decoder[IO],
      values: Vector[(Int, Long)],
  )(implicit peerSettings: Http2PeerSettings[IO], dataBudget: Http2Wire.DataBudget): IO[Int] = {
    def loop(stream: Option[Int], acknowledged: Boolean, remaining: Int): IO[Int] =
      (stream, acknowledged) match {
        case (Some(id), true) => IO.pure(id)
        case _ =>
          observationBound(remaining > 0, "missing request HEADERS or SETTINGS acknowledgment") *>
            readFrame(peer).flatMap { frame =>
              if (frame.frameType == Headers)
                for {
                  _ <- Check(stream.isEmpty, "unexpected second upload stream")
                  _ <- Check(
                    frame.streamId > 0 && (frame.streamId & 1) == 1,
                    "request stream ID must be odd",
                  )
                  fields <- readHeaders(peer, frame, decoder)
                  _ <- Check.equal(
                    fields.collect { case (":method", value) => value },
                    Vector("POST"),
                  )
                  _ <- Check(
                    !frame.hasFlag(EndStream),
                    "upload HEADERS prematurely ended the stream",
                  )
                  id <- loop(Some(frame.streamId), acknowledged, remaining - 1)
                } yield id
              else if (frame.frameType == Settings && frame.hasFlag(Ack))
                control(peer, frame, fromServer = false) *> loop(stream, true, remaining - 1)
              else
                control(peer, frame, fromServer = false) *> loop(
                  stream,
                  acknowledged,
                  remaining - 1,
                )
            }
      }
    acceptClient(peer, values) *> loop(None, false, 1024)
  }

  private def responseHead(
      peer: RawPeer[IO],
      stream: Int,
      decoder: HpackWire.Decoder[IO],
  )(implicit peerSettings: Http2PeerSettings[IO], dataBudget: Http2Wire.DataBudget): IO[Unit] = {
    def loop(remaining: Int): IO[Unit] =
      observationBound(remaining > 0, "too many informational response sections") *>
        (for {
          first <- readUntil(peer, fromServer = true)(_.frameType == Headers)
          _ <- Check.equal(first.streamId, stream)
          fields <- readHeaders(peer, first, decoder)
          statuses = fields.collect { case (":status", value) => value }
          informational = statuses match {
            case Vector(value) => value.length == 3 && value.head == '1' && value.forall(_.isDigit)
            case _ => false
          }
          _ <- Check(!first.hasFlag(EndStream), "nonempty response ended in HEADERS")
          _ <-
            if (informational)
              Check(statuses != Vector("101"), "HTTP/2 does not permit status 101") *>
                IO.defer(loop(remaining - 1))
            else Check.equal(statuses, Vector("200"))
        } yield ())
    validObservation(loop(1024))
  }

  private def consume(frame: Frame, stream: Int, progress: Progress): IO[Progress] =
    for {
      _ <- Check.equal(frame.streamId, stream)
      _ <- Check(!progress.ended, "DATA arrived after END_STREAM")
      _ <- Check(
        frame.payload.size <= math.max(0L, progress.credit),
        s"DATA payload ${frame.payload.size} exceeds remaining credit ${progress.credit}",
      )
      bytes <- IO(unpad(frame))
      _ <- observationBound(
        progress.body.size + bytes.size <= 1024 * 1024L,
        "unexpectedly large DATA body",
      )
    } yield Progress(
      progress.credit - frame.payload.size,
      progress.body ++ bytes,
      frame.hasFlag(EndStream),
    )

  private def consumeTrailers(
      peer: RawPeer[IO],
      decoder: HpackWire.Decoder[IO],
      frame: Frame,
      stream: Int,
      progress: Progress,
  )(implicit peerSettings: Http2PeerSettings[IO]): IO[Progress] =
    for {
      _ <- Check.equal(frame.streamId, stream)
      _ <- Check(!progress.ended, "HEADERS arrived after END_STREAM")
      _ <- Check(frame.hasFlag(EndStream), "trailing HEADERS must carry END_STREAM")
      fields <- readHeaders(peer, frame, decoder)
      _ <- Check(!fields.exists(_._1.startsWith(":")), "pseudo-header in trailers")
    } yield progress.copy(ended = true)

  /** Establish a positive DATA charge without prescribing its frame or chunk size.
    * A PING ACK alone cannot establish this prerequisite: DATA scheduling is independent.
    */
  private def firstPositiveData(
      peer: RawPeer[IO],
      fromServer: Boolean,
      decoder: HpackWire.Decoder[IO],
      stream: Int,
      initial: Progress,
  )(implicit
      peerSettings: Http2PeerSettings[IO],
      dataBudget: Http2Wire.DataBudget,
  ): IO[Progress] = {
    def loop(current: Progress, remaining: Int): IO[Progress] =
      if (current.credit < initial.credit) IO.pure(current)
      else
        observationBound(
          !current.ended && remaining > 0,
          "positive DATA prerequisite was not observed",
        ) *> readFrame(peer).flatMap { frame =>
          if (frame.frameType == Data)
            consume(frame, stream, current).flatMap(loop(_, remaining - 1))
          else if (frame.frameType == Headers)
            consumeTrailers(peer, decoder, frame, stream, current).flatMap(loop(_, remaining - 1))
          else control(peer, frame, fromServer) *> loop(current, remaining - 1)
        }
    validObservation(loop(initial, 4096))
  }

  private def readData(
      peer: RawPeer[IO],
      fromServer: Boolean,
      decoder: HpackWire.Decoder[IO],
      stream: Int,
      initial: Progress,
      boundary: Boolean,
  )(implicit
      peerSettings: Http2PeerSettings[IO],
      dataBudget: Http2Wire.DataBudget,
  ): IO[Progress] = {
    def loop(current: Progress, remaining: Int): IO[Progress] =
      if (current.ended || (boundary && current.credit == 0L)) IO.pure(current)
      else
        observationBound(remaining > 0, "too many frames before DATA completion") *> readFrame(peer)
          .flatMap { frame =>
            if (frame.frameType == Data)
              consume(frame, stream, current).flatMap(loop(_, remaining - 1))
            else if (frame.frameType == Headers)
              consumeTrailers(peer, decoder, frame, stream, current).flatMap(loop(_, remaining - 1))
            else control(peer, frame, fromServer) *> loop(current, remaining - 1)
          }
    validObservation(loop(initial, CompletionFrameLimit))
  }

  /** No frame-skipping reader is used here: every DATA payload before the ACK consumes credit. */
  private def barrier(
      peer: RawPeer[IO],
      fromServer: Boolean,
      decoder: HpackWire.Decoder[IO],
      stream: Int,
      initial: Progress,
  )(implicit
      peerSettings: Http2PeerSettings[IO],
      dataBudget: Http2Wire.DataBudget,
  ): IO[Progress] = {
    def loop(current: Progress, remaining: Int): IO[Progress] =
      observationBound(remaining > 0, "missing PING acknowledgment") *> readFrame(peer).flatMap {
        frame =>
          if (frame.frameType == Ping && frame.hasFlag(Ack))
            Check.equal(frame.streamId, 0) *> Check.equal(frame.payload, PingBytes).as(current)
          else if (frame.frameType == Data)
            consume(frame, stream, current).flatMap(loop(_, remaining - 1))
          else if (frame.frameType == Headers)
            consumeTrailers(peer, decoder, frame, stream, current).flatMap(loop(_, remaining - 1))
          else control(peer, frame, fromServer) *> loop(current, remaining - 1)
      }
    writeFrame(peer, Frame(Ping, 0, 0, PingBytes)) *> validObservation(loop(initial, 4096))
  }

  /** A reset can cross already queued output. Drain only to the first matching PING ACK;
    * keep the connection HPACK state and charge even discarded frames to the observation budget.
    */
  private def resetBarrier(
      peer: RawPeer[IO],
      fromServer: Boolean,
      decoder: HpackWire.Decoder[IO],
      stream: Int,
  )(implicit peerSettings: Http2PeerSettings[IO], dataBudget: Http2Wire.DataBudget): IO[Unit] = {
    def loop(
        current: Progress,
        pending: Option[Frame],
        remaining: Int,
        retained: Long,
    ): IO[Unit] =
      observationBound(remaining > 0, "missing reset PING acknowledgment") *>
        readFrame(peer).flatMap { frame =>
          Http2ObservationBudget.reserve[IO](retained, frame.payload.size + 9L).flatMap {
            reserved =>
              def trailers(section: Frame): IO[Unit] =
                if (section.hasFlag(EndHeaders))
                  consumeTrailers(peer, decoder, section, stream, current)
                    .flatMap(loop(_, None, remaining - 1, reserved))
                else loop(current, Some(section), remaining - 1, reserved)

              pending match {
                case Some(section) =>
                  Check(
                    frame.frameType == Continuation && frame.streamId == stream,
                    "expected an uninterrupted CONTINUATION on the reset stream",
                  ) *>
                    trailers(
                      section.copy(
                        flags = section.flags | (frame.flags & EndHeaders),
                        payload = section.payload ++ frame.payload,
                      )
                    )
                case None if frame.frameType == Ping && frame.hasFlag(Ack) =>
                  Check.equal(frame.streamId, 0) *> Check.equal(frame.payload, PingBytes)
                case None if frame.frameType == RstStream =>
                  Check.equal(frame.streamId, stream) *> Check.equal(frame.payload.size, 4L) *>
                    loop(current.copy(ended = true), None, remaining - 1, reserved)
                case None if frame.frameType == Data =>
                  consume(frame, stream, current).flatMap(loop(_, None, remaining - 1, reserved))
                case None if frame.frameType == Headers =>
                  for {
                    _ <- Check.equal(frame.streamId, stream)
                    _ <- Check(!current.ended, "HEADERS arrived after END_STREAM or RST_STREAM")
                    _ <- Check(frame.hasFlag(EndStream), "trailing HEADERS must carry END_STREAM")
                    unpadded <- IO(unpad(frame))
                    _ <- Check(
                      !frame.hasFlag(0x20) || unpadded.size >= 5L,
                      "truncated HEADERS priority fields",
                    )
                    _ <- trailers(
                      frame.copy(
                        flags = frame.flags & ~(Padded | 0x20),
                        payload = if (frame.hasFlag(0x20)) unpadded.drop(5) else unpadded,
                      )
                    )
                  } yield ()
                case None =>
                  control(peer, frame, fromServer) *> loop(current, None, remaining - 1, reserved)
              }
          }
        }
    writeFrame(peer, Frame(Ping, 0, 0, PingBytes)) *>
      validObservation(loop(Progress(0), None, 4096, 0L))
  }

  private def settingsAck(
      peer: RawPeer[IO],
      fromServer: Boolean,
      decoder: HpackWire.Decoder[IO],
      stream: Int,
      initial: Progress,
  )(implicit
      peerSettings: Http2PeerSettings[IO],
      dataBudget: Http2Wire.DataBudget,
  ): IO[Progress] = {
    def loop(current: Progress, remaining: Int): IO[Progress] =
      observationBound(remaining > 0, "missing SETTINGS acknowledgment") *> readFrame(peer).flatMap {
        frame =>
          if (frame.frameType == Settings && frame.hasFlag(Ack))
            control(peer, frame, fromServer).as(current)
          else if (frame.frameType == Data)
            consume(frame, stream, current).flatMap(loop(_, remaining - 1))
          else if (frame.frameType == Headers)
            consumeTrailers(peer, decoder, frame, stream, current).flatMap(loop(_, remaining - 1))
          else control(peer, frame, fromServer) *> loop(current, remaining - 1)
      }
    validObservation(loop(initial, 4096))
  }

  private def control(
      peer: RawPeer[IO],
      frame: Frame,
      fromServer: Boolean,
      rejecting: Boolean = false,
      closing: Boolean = false,
  )(implicit
      peerSettings: Http2PeerSettings[IO],
      dataBudget: Http2Wire.DataBudget,
  ): IO[Boolean] = {
    def reply(write: IO[Unit]): IO[Boolean] = if (closing) IO.pure(false)
    else if (rejecting) Http2RejectionWrite.reply(write)
    else write.as(true)
    IO.fromEither(Http2RejectionChecks.validate(frame, fromServer)) *> (frame.frameType match {
      case Settings =>
        IO(dataBudget.check(frame)) *> (if (frame.hasFlag(Ack)) IO.pure(true)
                                        else reply(writeFrame(peer, settings(ack = true))))
      case Ping =>
        if (frame.hasFlag(Ack)) IO.pure(true) else reply(writeFrame(peer, frame.copy(flags = Ack)))
      case WindowUpdate | Priority => IO.pure(true)
      case kind if kind > Continuation => IO.pure(true)
      case _ =>
        Check(false, s"unexpected frame type ${frame.frameType} on stream ${frame.streamId}")
          .as(true)
    })
  }

  private def connectionError(
      peer: RawPeer[IO],
      codes: Set[Long],
      fromServer: Boolean,
      initialState: Http2RejectionChecks.State = Http2RejectionChecks.State(),
  ): IO[Unit] =
    for {
      // GOAWAY is best effort; actual EOF, before target-resource cleanup, proves closure.
      bytes <- peer.readToEnd(1024 * 1024, allowReset = true)
      frames <- IO.fromEither(decodeAll(bytes))
      _ <- checkConnectionFrames(frames, codes, fromServer, initialState)
    } yield ()

  private def checkConnectionFrames(
      frames: Vector[Frame],
      codes: Set[Long],
      fromServer: Boolean,
      initialState: Http2RejectionChecks.State = Http2RejectionChecks.State(),
  ): IO[Unit] =
    frames
      .foldLeftM(initialState) { (previous, frame) =>
        IO.fromEither(Http2RejectionChecks.validate(frame, fromServer, previous)).flatTap { _ =>
          if (frame.frameType == GoAway) checkGoAway(frame, codes) else IO.unit
        }
      }
      .void

  // Caller validates the envelope before reading fixed fields.
  private def checkGoAway(frame: Frame, codes: Set[Long]): IO[Unit] = IO.defer {
    val code = readUint32(frame.payload.slice(4, 8))
    Check(code == 0L || codes.contains(code), s"unexpected GOAWAY: $frame")
  }

  /** Transport termination is accepted only between complete frames. */
  private def frameOrEof(peer: RawPeer[IO])(implicit
      peerSettings: Http2PeerSettings[IO]
  ): IO[Option[Frame]] =
    readFrameOrClose(peer)

  private def streamError(
      peer: RawPeer[IO],
      stream: Int,
      codes: Set[Long],
      fromServer: Boolean,
  )(implicit peerSettings: Http2PeerSettings[IO], dataBudget: Http2Wire.DataBudget): IO[Unit] = {
    def loop(remaining: Int, state: Http2RejectionChecks.State, closing: Boolean): IO[Unit] =
      observationBound(remaining > 0, "missing stream reset or connection closure") *> frameOrEof(
        peer
      )
        .flatMap {
          case None => IO.unit // Section 5.4.1 permits escalation to a connection error.
          case Some(frame) =>
            IO.fromEither(Http2RejectionChecks.validate(frame, fromServer, state)).flatMap { next =>
              frame.frameType match {
                case RstStream =>
                  Check.equal(frame.streamId, stream) *>
                    Check(
                      codes.contains(readUint32(frame.payload)),
                      s"unexpected RST_STREAM: $frame",
                    ) *> (if (closing) loop(remaining - 1, next, closing) else IO.unit)
                case GoAway =>
                  checkGoAway(frame, codes) *>
                    (if (readUint32(frame.payload.slice(4, 8)) == 0L)
                       loop(remaining - 1, next, closing)
                     else connectionError(peer, codes, fromServer, next))
                case _ =>
                  control(peer, frame, fromServer, rejecting = true, closing = closing).flatMap(
                    written => loop(remaining - 1, next, closing || !written)
                  )
              }
            }
        }
    loop(4096, Http2RejectionChecks.State(), closing = false)
  }

  private def upload(
      driver: ClientDriver[IO],
      ready: Deferred[IO, Unit],
      body: ByteVector,
  ): IO[Unit] =
    validExchange(
      driver
        .exchangeStream(
          RequestData(method = "POST", headers = Vector("content-length" -> body.size.toString)),
          _ => Stream.eval(ready.get).drain ++ Stream.chunk(Chunk.byteVector(body)),
        )
        .use { response =>
          response.body.compile.to(Chunk).flatMap { bytes =>
            Check.equal(response.status, 200) *> Check.equal(bytes.toByteVector, ByteVector.empty)
          }
        }
    )

  private def rejectedClient(context: CaseContext)(serve: RawPeer[IO] => IO[Unit]): IO[Unit] =
    Deferred[IO, Unit].flatMap { observed =>
      context.client(Protocol.Http2)(driver =>
        ClientObservation(driver.exchange(EmptyPost))(_.body.compile.drain)(result =>
          rejected(result) *> observed.get
        )
      )(peer => serve(peer) *> observed.complete(()).void)
    }

  private def rejectedUpload(context: CaseContext)(serve: RawPeer[IO] => IO[Unit]): IO[Unit] =
    (Deferred[IO, Unit], Deferred[IO, Unit]).tupled.flatMap { case (neverReleased, observed) =>
      context.client(Protocol.Http2)(driver =>
        ClientObservation(
          driver
            .exchangeStream(
              RequestData(method = "POST", headers = Vector("content-length" -> "16")),
              _ => Stream.eval(neverReleased.get).drain ++ Stream.chunk(Chunk.byteVector(SmallBody)),
            )
        )(_.body.compile.drain)(result => rejected(result) *> observed.get)
      )(peer => serve(peer) *> observed.complete(()).void)
    }

  private def rejected(result: Either[Throwable, Unit]): IO[Unit] =
    result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *> (result match {
      case Left(error: ProtocolFailure) => IO.raiseError(error)
      case Left(error: WireDecodingFailure) => IO.raiseError(error)
      case Left(error: WireObservationLimit) => IO.raiseError(error)
      case Left(error) => RawPeer.raiseAdapterOrTimeout[IO](error)
      case Right(_) => Check(false, "target completed an exchange that required rejection")
    })

  private def validExchange(operation: IO[Unit]): IO[Unit] =
    validObservation(operation)

  private def validObservation[A](operation: IO[A]): IO[A] =
    operation.handleErrorWith { error =>
      RawPeer.raiseAdapterOrTimeout[IO](error) *> (error match {
        case error: ProtocolFailure => IO.raiseError(error)
        case error: WireDecodingFailure => IO.raiseError(error)
        case error: WireObservationLimit => IO.raiseError(error)
        case error =>
          RawPeer.raiseAdapterOrTimeout[IO](error) *> IO.raiseError(
            new ProtocolFailure(s"valid exchange failed: $error")
          )
      })
    }

  private def observationBound(condition: Boolean, detail: String): IO[Unit] =
    if (condition) IO.unit else IO.raiseError(new WireObservationLimit(detail))

}
