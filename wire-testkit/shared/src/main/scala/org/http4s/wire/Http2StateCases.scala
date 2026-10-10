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

import java.util.concurrent.TimeoutException

/** Stream-state and flow-control oracles. Credit counts complete DATA payloads, not frame headers. */
object Http2StateCases {
  import Http2Wire._

  private val SmallBody = Http1Wire.ascii("abcdefghijklmnop")
  private val LargeBody = ByteVector.fill(65536)(0x61.toByte)
  private val PingBytes = ByteVector(19, 37, 55, 73, 91, 109, 127, 17)
  private val EmptyPost = RequestData(method = "POST", headers = Vector("content-length" -> "0"))

  val all: Vector[WireCase] = Vector(
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.server.idle-data",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "5.1", "DATA on an idle stream is a connection PROTOCOL_ERROR."),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        connectServer(peer) *> writeFrame(peer, data(1, ByteVector.empty)) *>
          connectionError(peer, Set(1L, 2L))
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.server.idle-window-update",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "5.1", "WINDOW_UPDATE on an idle stream is a connection PROTOCOL_ERROR."),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        connectServer(peer) *> writeFrame(peer, windowUpdate(1, 1)) *>
          connectionError(peer, Set(1L, 2L))
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1.1
    WireCase(
      "h2.server.even-request-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "5.1.1", "Client-initiated streams require odd identifiers."),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        connectServer(peer) *> writeFrame(peer, headers(2, requestFields())) *>
          connectionError(peer, Set(1L, 2L))
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
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- connectServer(peer, Vector(4 -> 0L))
          decoder <- HpackWire.decoder[IO]()
          _ <- writeFrame(peer, headers(3, requestFields()))
          _ <- responseHead(peer, 3, decoder)
          _ <- writeFrame(peer, headers(1, requestFields()))
          _ <- connectionError(peer, Set(1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.client.idle-data",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "5.1", "A client treats DATA on an uncreated stream as a connection error."),
    ) { context =>
      rejectedClient(context) { peer =>
        for {
          _ <- acceptClient(peer)
          request <- requestHead(peer)
          _ <- writeFrame(peer, data(request.streamId + 2, ByteVector.empty))
          _ <- connectionError(peer, Set(1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.client.idle-window-update",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "5.1", "A client treats WINDOW_UPDATE on an idle stream as a connection error."),
    ) { context =>
      rejectedClient(context) { peer =>
        for {
          _ <- acceptClient(peer)
          request <- requestHead(peer)
          _ <- writeFrame(peer, windowUpdate(request.streamId + 2, 1))
          _ <- connectionError(peer, Set(1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.client.unpromised-server-headers",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "5.1", "HEADERS cannot open an unpromised server-initiated stream."),
    ) { context =>
      rejectedClient(context) { peer =>
        acceptClient(peer) *> requestHead(peer) *>
          writeFrame(peer, headers(2, Vector(":status" -> "200"))) *>
          connectionError(peer, Set(1L, 2L))
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.server.half-closed-remote-data",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "5.1", "DATA after the peer's END_STREAM requires STREAM_CLOSED."),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use { case (peer, _) =>
        for {
          // Zero response credit holds the target in half-closed (remote), not closed.
          _ <- connectServer(peer, Vector(4 -> 0L))
          decoder <- HpackWire.decoder[IO]()
          _ <- writeFrame(peer, headers(1, requestFields()))
          _ <- responseHead(peer, 1, decoder)
          _ <- writeFrame(peer, data(1, ByteVector.empty))
          _ <- streamError(peer, 1, Set(1L, 2L, 5L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1
    WireCase(
      "h2.server.half-closed-remote-headers",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "5.1", "HEADERS after the peer's END_STREAM requires STREAM_CLOSED."),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use { case (peer, _) =>
        for {
          _ <- connectServer(peer, Vector(4 -> 0L))
          decoder <- HpackWire.decoder[IO]()
          _ <- writeFrame(peer, headers(1, requestFields()))
          _ <- responseHead(peer, 1, decoder)
          _ <- writeFrame(peer, headers(1, Vector("x-after-end" -> "invalid")))
          _ <- streamError(peer, 1, Set(1L, 2L, 5L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9
    WireCase(
      "h2.server.window-update-after-peer-end",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.9", "WINDOW_UPDATE remains valid after the peer sends END_STREAM."),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use { case (peer, _) =>
        for {
          _ <- connectServer(peer, Vector(4 -> 0L))
          decoder <- HpackWire.decoder[IO]()
          _ <- writeFrame(peer, headers(1, requestFields()))
          _ <- responseHead(peer, 1, decoder)
          stopped <- barrier(peer, 1, Progress(0))
          _ <- writeFrame(peer, windowUpdate(1, 65535))
          complete <- readData(peer, 1, stopped.copy(credit = 65535), boundary = false)
          _ <- Check.equal(complete.body, SmallBody)
        } yield ()
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
      context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use { case (peer, _) =>
        for {
          _ <- connectServer(peer, Vector(4 -> 3L))
          decoder <- HpackWire.decoder[IO]()
          _ <- writeFrame(peer, headers(1, requestFields()))
          _ <- responseHead(peer, 1, decoder)
          exhausted <- readData(peer, 1, Progress(3), boundary = true)
          _ <- Check.equal(exhausted.credit, 0L)
          _ <- Check(!exhausted.ended, "response ended before its complete body")
          stopped <- barrier(peer, 1, exhausted)
          _ <- writeFrame(peer, windowUpdate(1, 65535))
          complete <- readData(peer, 1, stopped.copy(credit = 65535), boundary = false)
          _ <- Check.equal(complete.body, SmallBody)
        } yield ()
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
      Deferred[IO, Unit].flatMap { ready =>
        context.client(Protocol.Http2)(driver => upload(driver, ready, SmallBody)) { peer =>
          for {
            stream <- openUpload(peer, Vector(4 -> 3L))
            _ <- ready.complete(())
            exhausted <- readData(peer, stream, Progress(3), boundary = true)
            _ <- Check.equal(exhausted.credit, 0L)
            _ <- Check(!exhausted.ended, "upload ended before its complete body")
            stopped <- barrier(peer, stream, exhausted)
            _ <- writeFrame(peer, windowUpdate(stream, 65535))
            complete <- readData(peer, stream, stopped.copy(credit = 65535), boundary = false)
            _ <- Check.equal(complete.body, SmallBody)
            _ <- writeFrame(peer, headers(stream, Vector(":status" -> "200")))
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.Reply(body = LargeBody)).use { case (peer, _) =>
        for {
          _ <- connectServer(peer, Vector(4 -> 131072L))
          decoder <- HpackWire.decoder[IO]()
          _ <- writeFrame(peer, headers(1, requestFields()))
          _ <- responseHead(peer, 1, decoder)
          exhausted <- readData(peer, 1, Progress(65535), boundary = true)
          _ <- Check.equal(exhausted.credit, 0L)
          _ <- Check(!exhausted.ended, "response exceeded initial connection credit")
          stopped <- barrier(peer, 1, exhausted)
          // Neither a stream update nor SETTINGS may alter the connection window.
          _ <- peer.write(encode(windowUpdate(1, 1)) ++ encode(settings(Vector(4 -> 262144L))))
          unchanged <- settingsAck(peer, 1, stopped)
          confirmed <- barrier(peer, 1, unchanged)
          _ <- writeFrame(peer, windowUpdate(0, 65535))
          complete <- readData(peer, 1, confirmed.copy(credit = 65535), boundary = false)
          _ <- Check.equal(complete.body, LargeBody)
        } yield ()
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
        "SETTINGS and stream WINDOW_UPDATE cannot replenish exhausted upload connection credit.",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { ready =>
        context.client(Protocol.Http2)(driver => upload(driver, ready, LargeBody)) { peer =>
          for {
            stream <- openUpload(peer, Vector(4 -> 131072L))
            _ <- ready.complete(())
            exhausted <- readData(peer, stream, Progress(65535), boundary = true)
            _ <- Check.equal(exhausted.credit, 0L)
            _ <- Check(!exhausted.ended, "upload exceeded initial connection credit")
            stopped <- barrier(peer, stream, exhausted)
            _ <- peer.write(
              encode(windowUpdate(stream, 1)) ++ encode(settings(Vector(4 -> 262144L)))
            )
            unchanged <- settingsAck(peer, stream, stopped)
            confirmed <- barrier(peer, stream, unchanged)
            _ <- writeFrame(peer, windowUpdate(0, 65535))
            complete <- readData(peer, stream, confirmed.copy(credit = 65535), boundary = false)
            _ <- Check.equal(complete.body, LargeBody)
            _ <- writeFrame(peer, headers(stream, Vector(":status" -> "200")))
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use { case (peer, _) =>
        for {
          _ <- connectServer(peer, Vector(4 -> 8L))
          decoder <- HpackWire.decoder[IO]()
          _ <- writeFrame(peer, headers(1, requestFields()))
          _ <- responseHead(peer, 1, decoder)
          exhausted <- readData(peer, 1, Progress(8), boundary = true)
          _ <- Check.equal(exhausted.credit, 0L)
          _ <- writeFrame(peer, settings(Vector(4 -> 3L)))
          negative <- settingsAck(peer, 1, exhausted.copy(credit = -5))
          negativeStopped <- barrier(peer, 1, negative)
          _ <- writeFrame(peer, windowUpdate(1, 5))
          zero <- barrier(peer, 1, negativeStopped.copy(credit = 0))
          _ <- writeFrame(peer, windowUpdate(1, 1))
          one <- readData(peer, 1, zero.copy(credit = 1), boundary = true)
          _ <- Check.equal(one.credit, 0L)
          stopped <- barrier(peer, 1, one)
          _ <- writeFrame(peer, windowUpdate(1, 65535))
          complete <- readData(peer, 1, stopped.copy(credit = 65535), boundary = false)
          _ <- Check.equal(complete.body, SmallBody)
        } yield ()
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
      Deferred[IO, Unit].flatMap { ready =>
        context.client(Protocol.Http2)(driver => upload(driver, ready, SmallBody)) { peer =>
          for {
            stream <- openUpload(peer, Vector(4 -> 8L))
            _ <- ready.complete(())
            exhausted <- readData(peer, stream, Progress(8), boundary = true)
            _ <- Check.equal(exhausted.credit, 0L)
            _ <- writeFrame(peer, settings(Vector(4 -> 3L)))
            negative <- settingsAck(peer, stream, exhausted.copy(credit = -5))
            negativeStopped <- barrier(peer, stream, negative)
            _ <- writeFrame(peer, windowUpdate(stream, 5))
            zero <- barrier(peer, stream, negativeStopped.copy(credit = 0))
            _ <- writeFrame(peer, windowUpdate(stream, 1))
            one <- readData(peer, stream, zero.copy(credit = 1), boundary = true)
            _ <- Check.equal(one.credit, 0L)
            stopped <- barrier(peer, stream, one)
            _ <- writeFrame(peer, windowUpdate(stream, 65535))
            complete <- readData(peer, stream, stopped.copy(credit = 65535), boundary = false)
            _ <- Check.equal(complete.body, SmallBody)
            _ <- writeFrame(peer, headers(stream, Vector(":status" -> "200")))
          } yield ()
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
      context.server(Protocol.Http2).use { case (peer, _) =>
        connectServer(peer) *> writeFrame(peer, Frame(WindowUpdate, 0, 0, uint32(0))) *>
          connectionError(peer, Set(1L, 2L))
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
      rejectedClient(context) { peer =>
        acceptClient(peer) *> requestHead(peer) *>
          writeFrame(peer, Frame(WindowUpdate, 0, 0, uint32(0))) *>
          connectionError(peer, Set(1L, 2L))
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- connectServer(peer)
          _ <- writeFrame(peer, headers(1, unfinishedPostFields, endStream = false))
          _ <- barrier(peer, 1, Progress(0))
          // Complete the request even on a nonconforming target; EchoBody must not be abandoned.
          _ <- peer
            .write(encode(Frame(WindowUpdate, 0, 1, uint32(0))) ++ encode(data(1, ByteVector(97))))
          _ <- streamError(peer, 1, Set(1L, 2L))
        } yield ()
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
      rejectedUpload(context) { peer =>
        for {
          stream <- openUpload(peer, Vector(4 -> 0L))
          _ <- writeFrame(peer, Frame(WindowUpdate, 0, stream, uint32(0)))
          _ <- streamError(peer, stream, Set(1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1
    WireCase(
      "h2.server.connection-window-overflow",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.9.1", "WINDOW_UPDATE cannot increase connection credit above 2^31-1."),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        // With no streams or DATA, the connection window is exactly 65535.
        connectServer(peer) *> writeFrame(peer, windowUpdate(0, Int.MaxValue)) *>
          connectionError(peer, Set(1L, 2L, 3L))
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1
    WireCase(
      "h2.client.connection-window-overflow",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.9.1", "Connection WINDOW_UPDATE overflow requires FLOW_CONTROL_ERROR."),
    ) { context =>
      rejectedClient(context) { peer =>
        // The request contains no DATA octets, leaving the full initial connection window.
        acceptClient(peer) *> requestHead(peer) *>
          writeFrame(peer, windowUpdate(0, Int.MaxValue)) *>
          connectionError(peer, Set(1L, 2L, 3L))
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- connectServer(peer, Vector(4 -> 0L))
          _ <- writeFrame(peer, headers(1, unfinishedPostFields, endStream = false))
          _ <- barrier(peer, 1, Progress(0))
          // EchoBody cannot produce DATA before the final request frame, so credit cannot race down.
          _ <- peer.write(
            encode(windowUpdate(1, Int.MaxValue)) ++ encode(windowUpdate(1, 1)) ++
              encode(data(1, ByteVector(97)))
          )
          _ <- streamError(peer, 1, Set(1L, 2L, 3L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1
    WireCase(
      "h2.client.stream-window-overflow",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.9.1", "An open upload rejects a stream window above 2^31-1."),
    ) { context =>
      rejectedUpload(context) { peer =>
        for {
          stream <- openUpload(peer, Vector(4 -> 0L))
          // The upload source remains gated, so no DATA can reduce the window between updates.
          _ <- peer.write(
            encode(windowUpdate(stream, Int.MaxValue)) ++ encode(windowUpdate(stream, 1))
          )
          _ <- streamError(peer, stream, Set(1L, 2L, 3L))
        } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- connectServer(peer, Vector(4 -> 0L))
          _ <- writeFrame(peer, headers(1, unfinishedPostFields, endStream = false))
          _ <- barrier(peer, 1, Progress(0))
          _ <- peer.write(
            encode(windowUpdate(1, Int.MaxValue)) ++ encode(settings(Vector(4 -> 1L))) ++
              encode(data(1, ByteVector(97)))
          )
          _ <- connectionError(peer, Set(1L, 2L, 3L))
        } yield ()
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
      rejectedUpload(context) { peer =>
        for {
          stream <- openUpload(peer, Vector(4 -> 0L))
          _ <- peer.write(
            encode(windowUpdate(stream, Int.MaxValue)) ++ encode(settings(Vector(4 -> 1L)))
          )
          _ <- connectionError(peer, Set(1L, 2L, 3L))
        } yield ()
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
      context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use { case (peer, _) =>
        for {
          _ <- connectServer(peer, Vector(4 -> 0L))
          decoder <- HpackWire.decoder[IO]()
          _ <- writeFrame(peer, headers(1, requestFields()))
          _ <- responseHead(peer, 1, decoder)
          stopped <- barrier(peer, 1, Progress(0))
          _ <- writeFrame(peer, windowUpdate(1, Int.MaxValue))
          complete <- readData(peer, 1, stopped.copy(credit = 65535), boundary = false)
          _ <- Check.equal(complete.body, SmallBody)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9
    WireCase(
      "h2.client.maximum-stream-window-update",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.9", "The maximum WINDOW_UPDATE increment is accepted at zero upload credit."),
    ) { context =>
      Deferred[IO, Unit].flatMap { ready =>
        context.client(Protocol.Http2)(driver => upload(driver, ready, SmallBody)) { peer =>
          for {
            stream <- openUpload(peer, Vector(4 -> 0L))
            _ <- ready.complete(())
            stopped <- barrier(peer, stream, Progress(0))
            _ <- writeFrame(peer, windowUpdate(stream, Int.MaxValue))
            complete <- readData(peer, stream, stopped.copy(credit = 65535), boundary = false)
            _ <- Check.equal(complete.body, SmallBody)
            _ <- writeFrame(peer, headers(stream, Vector(":status" -> "200")))
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use { case (peer, _) =>
        for {
          _ <- connectServer(peer, Vector(4 -> 0L))
          decoder <- HpackWire.decoder[IO]()
          _ <- writeFrame(peer, headers(1, requestFields()))
          _ <- responseHead(peer, 1, decoder)
          _ <- writeFrame(peer, Frame(RstStream, 0, 1, uint32(8)))
          stopped <- barrier(peer, 1, Progress(0))
          _ <- Check(!stopped.ended, "server sent END_STREAM in response to a reset")
          // PING proves the reset has been read; credit now must not restart stream 1.
          _ <- peer.write(
            encode(windowUpdate(1, 65535)) ++ encode(headers(3, requestFields())) ++
              encode(windowUpdate(3, 65535))
          )
          _ <- responseHead(peer, 3, decoder)
          complete <- readData(peer, 3, Progress(65535), boundary = false)
          _ <- Check.equal(complete.body, SmallBody)
          _ <- barrier(peer, 3, complete)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9
    WireCase(
      "h2.server.window-update-on-closed-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.9", "An in-flight WINDOW_UPDATE on a closed stream is not a connection error."),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use { case (peer, _) =>
        for {
          _ <- connectServer(peer)
          decoder <- HpackWire.decoder[IO]()
          _ <- writeFrame(peer, headers(1, requestFields()))
          _ <- responseHead(peer, 1, decoder)
          first <- readData(peer, 1, Progress(65535), boundary = false)
          _ <- Check.equal(first.body, SmallBody)
          _ <- writeFrame(peer, windowUpdate(1, 1))
          _ <- barrier(peer, 1, first)
          _ <- writeFrame(peer, headers(3, requestFields()))
          _ <- responseHead(peer, 3, decoder)
          second <- readData(peer, 3, Progress(first.credit), boundary = false)
          _ <- Check.equal(second.body, SmallBody)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.7
    WireCase(
      "h2.server.goaway-includes-dispatched-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "8.7", "GOAWAY cannot declare an application-dispatched stream unprocessed."),
    ) { context =>
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
            _ <- frames.filter(_.frameType == GoAway).traverse_ { frame =>
              checkGoAway(frame, Set(1L, 2L)) *>
                Check(
                  (readUint32(frame.payload.take(4)) & 0x7fffffffL) >= 7L,
                  "GOAWAY claimed an application-dispatched request was unprocessed",
                )
            }
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.Reply(body = SmallBody)).use { case (peer, _) =>
        for {
          _ <- connectServer(peer, Vector(3 -> 0L))
          decoder <- HpackWire.decoder[IO]()
          _ <- writeFrame(peer, headers(1, requestFields()))
          _ <- responseHead(peer, 1, decoder)
          complete <- readData(peer, 1, Progress(65535), boundary = false)
          _ <- Check.equal(complete.body, SmallBody)
        } yield ()
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
  ): IO[Unit] =
    for {
      _ <- peer.write(clientPreface ++ encode(settings(values)))
      first <- readFrame(peer)
      _ <- Check(
        first.frameType == Settings && !first.hasFlag(Ack),
        "server preface requires SETTINGS",
      )
      _ <- control(peer, first)
      _ <- settingsAck(peer, 0, Progress(0))
    } yield ()

  private def acceptClient(
      peer: RawPeer[IO],
      values: Vector[(Int, Long)] = Vector.empty,
  ): IO[Unit] =
    for {
      preface <- peer.readExact(clientPreface.size.toInt)
      _ <- Check.equal(preface, clientPreface)
      first <- readFrame(peer)
      _ <- Check(
        first.frameType == Settings && !first.hasFlag(Ack),
        "client preface requires SETTINGS",
      )
      _ <- Check.equal(first.streamId, 0)
      _ <- Check.equal(first.payload.size % 6L, 0L)
      _ <- peer.write(encode(settings(values)) ++ encode(settings(ack = true)))
    } yield ()

  private def requestHead(peer: RawPeer[IO]): IO[Frame] =
    for {
      first <- readUntil(peer)(_.frameType == Headers)
      decoder <- HpackWire.decoder[IO]()
      fields <- readHeaders(peer, first, decoder)
      _ <- Check(
        first.streamId > 0 && (first.streamId & 1) == 1,
        "request stream ID must be positive and odd",
      )
      _ <- Check.equal(fields.collect { case (":method", value) => value }, Vector("POST"))
    } yield first

  /** Request HEADERS and SETTINGS ACK can arrive in either order; no upload byte is released yet. */
  private def openUpload(peer: RawPeer[IO], values: Vector[(Int, Long)]): IO[Int] = {
    def loop(stream: Option[Int], acknowledged: Boolean, remaining: Int): IO[Int] =
      (stream, acknowledged) match {
        case (Some(id), true) => IO.pure(id)
        case _ =>
          Check(remaining > 0, "missing request HEADERS or SETTINGS acknowledgment") *>
            readFrame(peer).flatMap { frame =>
              if (frame.frameType == Headers)
                for {
                  _ <- Check(stream.isEmpty, "unexpected second upload stream")
                  _ <- Check(
                    frame.streamId > 0 && (frame.streamId & 1) == 1,
                    "request stream ID must be odd",
                  )
                  decoder <- HpackWire.decoder[IO]()
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
                control(peer, frame) *> loop(stream, true, remaining - 1)
              else control(peer, frame) *> loop(stream, acknowledged, remaining - 1)
            }
      }
    acceptClient(peer, values) *> loop(None, false, 1024)
  }

  private def responseHead(
      peer: RawPeer[IO],
      stream: Int,
      decoder: HpackWire.Decoder[IO],
  ): IO[Unit] =
    validObservation {
      for {
        first <- readUntil(peer)(_.frameType == Headers)
        _ <- Check.equal(first.streamId, stream)
        fields <- readHeaders(peer, first, decoder)
        _ <- Check.equal(fields.collect { case (":status", value) => value }, Vector("200"))
        _ <- Check(!first.hasFlag(EndStream), "nonempty response ended in HEADERS")
      } yield ()
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
      _ <- Check(progress.body.size + bytes.size <= 1024 * 1024L, "unexpectedly large DATA body")
    } yield Progress(
      progress.credit - frame.payload.size,
      progress.body ++ bytes,
      frame.hasFlag(EndStream),
    )

  private def readData(
      peer: RawPeer[IO],
      stream: Int,
      initial: Progress,
      boundary: Boolean,
  ): IO[Progress] = {
    def loop(current: Progress, remaining: Int): IO[Progress] =
      if (current.ended || (boundary && current.credit == 0L)) IO.pure(current)
      else
        Check(remaining > 0, "too many frames before DATA completion") *> readFrame(peer).flatMap {
          frame =>
            if (frame.frameType == Data)
              consume(frame, stream, current).flatMap(loop(_, remaining - 1))
            else control(peer, frame) *> loop(current, remaining - 1)
        }
    validObservation(loop(initial, 4096))
  }

  /** No frame-skipping reader is used here: every DATA payload before the ACK consumes credit. */
  private def barrier(peer: RawPeer[IO], stream: Int, initial: Progress): IO[Progress] = {
    def loop(current: Progress, remaining: Int): IO[Progress] =
      Check(remaining > 0, "missing PING acknowledgment") *> readFrame(peer).flatMap { frame =>
        if (frame.frameType == Ping && frame.hasFlag(Ack))
          Check.equal(frame.streamId, 0) *> Check.equal(frame.payload, PingBytes).as(current)
        else if (frame.frameType == Data)
          consume(frame, stream, current).flatMap(loop(_, remaining - 1))
        else control(peer, frame) *> loop(current, remaining - 1)
      }
    writeFrame(peer, Frame(Ping, 0, 0, PingBytes)) *> validObservation(loop(initial, 4096))
  }

  private def settingsAck(peer: RawPeer[IO], stream: Int, initial: Progress): IO[Progress] = {
    def loop(current: Progress, remaining: Int): IO[Progress] =
      Check(remaining > 0, "missing SETTINGS acknowledgment") *> readFrame(peer).flatMap { frame =>
        if (frame.frameType == Settings && frame.hasFlag(Ack)) control(peer, frame).as(current)
        else if (frame.frameType == Data)
          consume(frame, stream, current).flatMap(loop(_, remaining - 1))
        else control(peer, frame) *> loop(current, remaining - 1)
      }
    validObservation(loop(initial, 4096))
  }

  private def control(peer: RawPeer[IO], frame: Frame): IO[Unit] = frame.frameType match {
    case Settings =>
      Check.equal(frame.streamId, 0) *> Check.equal(frame.payload.size % 6L, 0L) *>
        (if (frame.hasFlag(Ack)) Check.equal(frame.payload, ByteVector.empty)
         else writeFrame(peer, settings(ack = true)))
    case Ping =>
      Check.equal(frame.streamId, 0) *> Check.equal(frame.payload.size, 8L) *>
        (if (frame.hasFlag(Ack)) IO.unit else writeFrame(peer, frame.copy(flags = Ack)))
    case WindowUpdate =>
      Check.equal(frame.payload.size, 4L) *>
        Check((readUint32(frame.payload) & 0x7fffffffL) != 0L, "zero WINDOW_UPDATE from target")
    case Priority =>
      Check(frame.streamId != 0 && frame.payload.size == 5L, "invalid PRIORITY from target")
    case kind if kind > Continuation => IO.unit
    case _ => Check(false, s"unexpected frame type ${frame.frameType} on stream ${frame.streamId}")
  }

  private def connectionError(peer: RawPeer[IO], codes: Set[Long]): IO[Unit] =
    for {
      // GOAWAY is best effort; actual EOF, before target-resource cleanup, proves closure.
      bytes <- peer.readToEnd(1024 * 1024, allowReset = true)
      frames <- IO.fromEither(decodeAll(bytes))
      _ <- frames.filter(_.frameType == GoAway).traverse_(checkGoAway(_, codes))
    } yield ()

  private def checkGoAway(frame: Frame, codes: Set[Long]): IO[Unit] =
    Check.equal(frame.streamId, 0) *> Check(frame.payload.size >= 8L, "short GOAWAY") *>
      Check(codes.contains(readUint32(frame.payload.slice(4, 8))), s"unexpected GOAWAY: $frame")

  /** Transport termination is accepted only between complete frames. */
  private def frameOrEof(peer: RawPeer[IO]): IO[Option[Frame]] =
    readFrameOrClose(peer)

  private def streamError(peer: RawPeer[IO], stream: Int, codes: Set[Long]): IO[Unit] = {
    def loop(remaining: Int): IO[Unit] =
      Check(remaining > 0, "missing stream reset or connection closure") *> frameOrEof(peer)
        .flatMap {
          case None => IO.unit // Section 5.4.1 permits escalation to a connection error.
          case Some(frame) if frame.frameType == RstStream =>
            Check.equal(frame.streamId, stream) *> Check.equal(frame.payload.size, 4L) *>
              Check(codes.contains(readUint32(frame.payload)), s"unexpected RST_STREAM: $frame")
          case Some(frame) if frame.frameType == GoAway =>
            checkGoAway(frame, codes) *> connectionError(peer, codes)
          case Some(frame) => control(peer, frame) *> loop(remaining - 1)
        }
    loop(4096)
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
          Stream.eval(ready.get).drain ++ Stream.chunk(Chunk.byteVector(body)),
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
        rejected(driver.exchange(EmptyPost).use(_.body.compile.drain)) *> observed.get
      )(peer => serve(peer) *> observed.complete(()).void)
    }

  private def rejectedUpload(context: CaseContext)(serve: RawPeer[IO] => IO[Unit]): IO[Unit] =
    (Deferred[IO, Unit], Deferred[IO, Unit]).tupled.flatMap { case (neverReleased, observed) =>
      context.client(Protocol.Http2)(driver =>
        rejected(
          driver
            .exchangeStream(
              RequestData(method = "POST", headers = Vector("content-length" -> "16")),
              Stream.eval(neverReleased.get).drain ++ Stream.chunk(Chunk.byteVector(SmallBody)),
            )
            .use(_.body.compile.drain)
        ) *> observed.get
      )(peer => serve(peer) *> observed.complete(()).void)
    }

  private def rejected(operation: IO[Unit]): IO[Unit] =
    operation.attempt.flatMap {
      case Left(error) if causedByTimeout(error) => IO.raiseError(error)
      case Left(_) => IO.unit
      case Right(_) => Check(false, "target completed an exchange that required rejection")
    }

  private def validExchange(operation: IO[Unit]): IO[Unit] =
    validObservation(operation)

  private def validObservation[A](operation: IO[A]): IO[A] =
    operation.handleErrorWith {
      case error if causedByTimeout(error) => IO.raiseError(error)
      case error: ProtocolFailure => IO.raiseError(error)
      case error => IO.raiseError(new ProtocolFailure(s"valid exchange failed: $error"))
    }

  private def causedByTimeout(error: Throwable): Boolean = {
    @annotation.tailrec
    def loop(current: Throwable, remaining: Int): Boolean =
      if (current == null || remaining == 0) false
      else if (current.isInstanceOf[TimeoutException]) true
      else loop(current.getCause, remaining - 1)
    loop(error, 16)
  }
}
