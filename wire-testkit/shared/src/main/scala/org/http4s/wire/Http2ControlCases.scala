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

/** Plaintext HTTP/2 establishment, framing, settings, and connection controls. */
object Http2ControlCases {
  val all: Vector[WireCase] = Vector(
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4
    WireCase(
      "h2.server.prior-knowledge-preface",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "3.4",
        "The first server frame is SETTINGS, and both initial SETTINGS frames are acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- peer.write(Http2Wire.clientPreface ++ Http2Wire.encode(Http2Wire.settings()))
          first <- Http2Wire.readFrame(peer)
          _ <- Check.equal(first.frameType, Http2Wire.Settings)
          _ <- Check(!first.hasFlag(Http2Wire.Ack), "server preface was a SETTINGS acknowledgment")
          _ <- validSettings(first, fromServer = true)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))
          observed <- observeUntil(peer)(_.exists(isSettingsAck))
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4
    WireCase(
      "h2.client.prior-knowledge-preface",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "3.4",
        "The client starts with the 24-octet magic and SETTINGS, then acknowledges server SETTINGS.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile
              .to(Chunk)
              .flatMap(body =>
                Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
              )
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          magic <- peer.readExact(24)
          _ <- Check.equal(magic, Http2Wire.clientPreface)
          first <- Http2Wire.readFrame(peer)
          _ <- Check.equal(first.frameType, Http2Wire.Settings)
          _ <- Check(!first.hasFlag(Http2Wire.Ack), "client preface was a SETTINGS acknowledgment")
          _ <- validSettings(first, fromServer = false)
          _ <- peer.write(
            Http2Wire.encode(Http2Wire.settings()) ++ Http2Wire.encode(
              Http2Wire.settings(ack = true)
            )
          )
          observed <- observeUntil(peer)(frames =>
            frames.exists(isSettingsAck) && requestComplete(frames)
          )
          streamId = observed.find(_.frameType == Http2Wire.Headers).get.streamId
          _ <- Check(
            streamId > 0 && (streamId & 1) == 1,
            "client request stream identifier must be positive and odd",
          )
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4
    WireCase(
      "h2.server.reject-preface-magic",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "3.4", "An invalid client connection preface is a connection error.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- IO.unit
          _ <- peer.write(
            Http1Wire.ascii("PRI * HTTP/2.0\r\n\r\nSN\r\n\r\n") ++ Http2Wire
              .encode(Http2Wire.settings())
          )
          _ <- connectionClosed(peer, Set(1L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4
    WireCase(
      "h2.server.reject-preface-first-ping",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "3.4", "The client magic must be immediately followed by a SETTINGS frame.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- IO.unit
          _ <- peer.write(
            Http2Wire.clientPreface ++ Http2Wire
              .encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          )
          _ <- connectionClosed(peer, Set(1L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4
    WireCase(
      "h2.client.reject-preface-first-ping",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "3.4", "A server preface that starts with PING is a connection error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(result => requireRejection(result) *> closed.get)
        ) { peer =>
          for {
            magic <- peer.readExact(24)
            _ <- Check.equal(magic, Http2Wire.clientPreface)
            initial <- Http2Wire.readFrame(peer)
            _ <- validSettings(initial, fromServer = false)
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier)) ++
                Http2Wire.encode(Http2Wire.settings()) ++ Http2Wire.encode(
                  Http2Wire.settings(ack = true)
                )
            )
            _ <- connectionClosed(peer, Set(1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.server.unknown-connection-frame",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "4.1",
        "Unknown frame types are discarded without disrupting subsequent connection controls.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(0xff, 0xff, 0, ByteVector(1, 2, 3)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- IO.unit
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.server.unknown-stream-frame",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "4.1",
        "Unknown frame types are discarded without disrupting subsequent connection controls.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(0xff, 0xff, 17, ByteVector(7, 8, 9)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- IO.unit
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.server.unknown-empty-frame",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "4.1",
        "Unknown frame types are discarded without disrupting subsequent connection controls.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(0xff, 0xff, 0, ByteVector.empty))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- IO.unit
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.server.unknown-max-default-frame",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "4.1",
        "Unknown frame types are discarded without disrupting subsequent connection controls.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire
            .writeFrame(peer, Http2Wire.Frame(0xff, 0xff, 0, ByteVector.fill(16384)(0.toByte)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- IO.unit
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.server.ignore-reserved-frame-bit",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "4.1", "The reserved frame-header bit is ignored on receipt.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          encoded = Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Opaque))
          _ <- peer.write(encoded.take(5) ++ Http2Wire.uint32(0x80000000L) ++ encoded.drop(9))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          replies = observed
            .filter(frame => frame.frameType == Http2Wire.Ping && frame.payload == Opaque)
          _ <- Check.equal(replies.map(_.flags), Vector(Http2Wire.Ack))
          _ <- Check.equal(replies.map(_.streamId), Vector(0))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.server.ignore-ping-unused-flags",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "4.1",
        "Unused PING flags are ignored and remain unset in the acknowledgment.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0xfe, 0, Opaque))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          replies = observed
            .filter(frame => frame.frameType == Http2Wire.Ping && frame.payload == Opaque)
          _ <- Check.equal(replies.map(_.flags), Vector(Http2Wire.Ack))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.server.ignore-settings-unused-flags",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "4.1",
        "Unused SETTINGS flags do not prevent acknowledgment of valid settings.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings().copy(flags = 0xfe))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- observed
            .filter(isSettingsAck)
            .traverse_(frame => Check.equal(frame.flags, Http2Wire.Ack))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5
    WireCase(
      "h2.server.settings-empty-update",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.5", "An empty non-ACK SETTINGS frame is valid and is acknowledged.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings())
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.settings-unknown-identifier",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "Unknown settings are ignored while the whole SETTINGS frame is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire
            .writeFrame(peer, Http2Wire.settings(Vector(0xffff -> 0xffffffffL, 0 -> 0L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.settings-max-frame-minimum",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "The inclusive minimum SETTINGS_MAX_FRAME_SIZE value of 16384 is accepted.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(5 -> 16384L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.settings-max-frame-maximum",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "The inclusive maximum SETTINGS_MAX_FRAME_SIZE value of 16777215 is accepted.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(5 -> 0xffffffL)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.settings-enable-push-zero",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "SETTINGS_ENABLE_PUSH equal to zero is valid from either endpoint.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(2 -> 0L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.settings-header-list-advisory",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "SETTINGS_MAX_HEADER_LIST_SIZE is advisory and permits the unsigned 32-bit maximum.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(6 -> 0xffffffffL)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.3
    WireCase(
      "h2.server.settings-ordered-acknowledgments",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.3",
        "Each SETTINGS frame, including successive empty and unknown-setting frames, is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- peer.write(
            Http2Wire.encode(Http2Wire.settings()) ++
              Http2Wire.encode(Http2Wire.settings(Vector(0xffff -> 42L))) ++
              Http2Wire.encode(Http2Wire.settings(Vector(5 -> 32768L)))
          )
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 3
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 3)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.server.ping-ack-no-response",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.7", "An endpoint does not respond to a PING that already has ACK set.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, Http2Wire.Ack, 0, Opaque))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- Check(
            !observed.exists(frame => frame.frameType == Http2Wire.Ping && frame.payload == Opaque),
            "responded to an ACK PING",
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.server.ping-all-octets",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.7", "A PING acknowledgment copies all eight opaque octets exactly.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Opaque))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          replies = observed
            .filter(frame => frame.frameType == Http2Wire.Ping && frame.payload == Opaque)
          _ <- Check.equal(replies.map(_.flags), Vector(Http2Wire.Ack))
          _ <- Check.equal(replies.map(_.streamId), Vector(0))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.client.unknown-connection-frame",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "4.1",
        "Unknown frame types are discarded without disrupting subsequent connection controls.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(0xff, 0xff, 0, ByteVector(1, 2, 3)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- IO.unit
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.client.unknown-stream-frame",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "4.1",
        "Unknown frame types are discarded without disrupting subsequent connection controls.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(0xff, 0xff, 17, ByteVector(7, 8, 9)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- IO.unit
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.client.unknown-empty-frame",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "4.1",
        "Unknown frame types are discarded without disrupting subsequent connection controls.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(0xff, 0xff, 0, ByteVector.empty))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- IO.unit
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.client.unknown-max-default-frame",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "4.1",
        "Unknown frame types are discarded without disrupting subsequent connection controls.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(0xff, 0xff, 0, ByteVector.fill(16384)(0.toByte)),
          )
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- IO.unit
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.client.ignore-reserved-frame-bit",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "4.1", "The reserved frame-header bit is ignored on receipt.", "MUST"),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          encoded = Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Opaque))
          _ <- peer.write(encoded.take(5) ++ Http2Wire.uint32(0x80000000L) ++ encoded.drop(9))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          replies = observed.filter(frame =>
            frame.frameType == Http2Wire.Ping && frame.payload == Opaque
          )
          _ <- Check.equal(replies.map(_.flags), Vector(Http2Wire.Ack))
          _ <- Check.equal(replies.map(_.streamId), Vector(0))
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.client.ignore-ping-unused-flags",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "4.1",
        "Unused PING flags are ignored and remain unset in the acknowledgment.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0xfe, 0, Opaque))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          replies = observed.filter(frame =>
            frame.frameType == Http2Wire.Ping && frame.payload == Opaque
          )
          _ <- Check.equal(replies.map(_.flags), Vector(Http2Wire.Ack))
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.1
    WireCase(
      "h2.client.ignore-settings-unused-flags",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "4.1",
        "Unused SETTINGS flags do not prevent acknowledgment of valid settings.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings().copy(flags = 0xfe))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- observed
            .filter(isSettingsAck)
            .traverse_(frame => Check.equal(frame.flags, Http2Wire.Ack))
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5
    WireCase(
      "h2.client.settings-empty-update",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.5", "An empty non-ACK SETTINGS frame is valid and is acknowledged.", "MUST"),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings())
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.settings-unknown-identifier",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "Unknown settings are ignored while the whole SETTINGS frame is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.settings(Vector(0xffff -> 0xffffffffL, 0 -> 0L)),
          )
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.settings-max-frame-minimum",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "The inclusive minimum SETTINGS_MAX_FRAME_SIZE value of 16384 is accepted.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(5 -> 16384L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.settings-max-frame-maximum",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "The inclusive maximum SETTINGS_MAX_FRAME_SIZE value of 16777215 is accepted.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(5 -> 0xffffffL)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.settings-enable-push-zero",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "SETTINGS_ENABLE_PUSH equal to zero is valid from either endpoint.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(2 -> 0L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.settings-header-list-advisory",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "SETTINGS_MAX_HEADER_LIST_SIZE is advisory and permits the unsigned 32-bit maximum.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(6 -> 0xffffffffL)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.3
    WireCase(
      "h2.client.settings-ordered-acknowledgments",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.3",
        "Each SETTINGS frame, including successive empty and unknown-setting frames, is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- peer.write(
            Http2Wire.encode(Http2Wire.settings()) ++
              Http2Wire.encode(Http2Wire.settings(Vector(0xffff -> 42L))) ++
              Http2Wire.encode(Http2Wire.settings(Vector(5 -> 32768L)))
          )
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 3
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 3)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.client.ping-ack-no-response",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.7", "An endpoint does not respond to a PING that already has ACK set.", "MUST"),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, Http2Wire.Ack, 0, Opaque))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- Check(
            !observed.exists(frame => frame.frameType == Http2Wire.Ping && frame.payload == Opaque),
            "responded to an ACK PING",
          )
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.client.ping-all-octets",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.7", "A PING acknowledgment copies all eight opaque octets exactly.", "MUST"),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Opaque))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          replies = observed.filter(frame =>
            frame.frameType == Http2Wire.Ping && frame.payload == Opaque
          )
          _ <- Check.equal(replies.map(_.flags), Vector(Http2Wire.Ack))
          _ <- Check.equal(replies.map(_.streamId), Vector(0))
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5
    WireCase(
      "h2.server.reject-settings-stream-id",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.5", "A nonzero SETTINGS stream identifier is a connection error.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings().copy(streamId = 1))
          _ <- connectionClosed(peer, Set(1L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5
    WireCase(
      "h2.client.reject-settings-stream-id",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.5", "A nonzero SETTINGS stream identifier is a connection error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.settings().copy(streamId = 1)) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(1L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5
    WireCase(
      "h2.server.reject-settings-ack-payload",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5",
        "A SETTINGS acknowledgment with a payload is a connection frame-size error.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(5 -> 16384L), ack = true))
          _ <- connectionClosed(peer, Set(6L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5
    WireCase(
      "h2.client.reject-settings-ack-payload",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5",
        "A SETTINGS acknowledgment with a payload is a connection frame-size error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.settings(Vector(5 -> 16384L), ack = true)) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(6L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5
    WireCase(
      "h2.server.reject-settings-length",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5",
        "A SETTINGS payload whose length is not divisible by six is a connection frame-size error.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire
            .writeFrame(peer, Http2Wire.Frame(Http2Wire.Settings, 0, 0, ByteVector(0, 5, 0, 0, 64)))
          _ <- connectionClosed(peer, Set(6L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5
    WireCase(
      "h2.client.reject-settings-length",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5",
        "A SETTINGS payload whose length is not divisible by six is a connection frame-size error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(
                Http2Wire.Frame(Http2Wire.Settings, 0, 0, ByteVector(0, 5, 0, 0, 64))
              ) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(6L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.reject-settings-enable-push-two",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "SETTINGS_ENABLE_PUSH values other than zero and one are connection errors.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(2 -> 2L)))
          _ <- connectionClosed(peer, Set(1L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.reject-settings-enable-push-two",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "SETTINGS_ENABLE_PUSH values other than zero and one are connection errors.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.settings(Vector(2 -> 2L))) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(1L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.reject-settings-window-overflow",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "An initial stream window above 2147483647 is a connection flow-control error.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(4 -> 0x80000000L)))
          _ <- connectionClosed(peer, Set(3L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.reject-settings-window-overflow",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "An initial stream window above 2147483647 is a connection flow-control error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.settings(Vector(4 -> 0x80000000L))) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(3L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.reject-settings-frame-size-below-minimum",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.5.2", "SETTINGS_MAX_FRAME_SIZE below 16384 is a connection error.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(5 -> 16383L)))
          _ <- connectionClosed(peer, Set(1L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.reject-settings-frame-size-below-minimum",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.5.2", "SETTINGS_MAX_FRAME_SIZE below 16384 is a connection error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.settings(Vector(5 -> 16383L))) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(1L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.reject-settings-frame-size-above-maximum",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.5.2", "SETTINGS_MAX_FRAME_SIZE above 16777215 is a connection error.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(5 -> 0x1000000L)))
          _ <- connectionClosed(peer, Set(1L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.reject-settings-frame-size-above-maximum",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.5.2", "SETTINGS_MAX_FRAME_SIZE above 16777215 is a connection error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.settings(Vector(5 -> 0x1000000L))) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(1L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.server.reject-ping-stream-id",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.7", "PING with a nonzero stream identifier is a connection error.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 1, Opaque))
          _ <- connectionClosed(peer, Set(1L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.client.reject-ping-stream-id",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.7", "PING with a nonzero stream identifier is a connection error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 1, Opaque)) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(1L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.server.reject-ping-length-nine",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.7", "PING with nine payload octets is a connection frame-size error.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire
            .writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Opaque ++ ByteVector(0)))
          _ <- connectionClosed(peer, Set(6L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.client.reject-ping-length-nine",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.7", "PING with nine payload octets is a connection frame-size error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Opaque ++ ByteVector(0))) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(6L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.server.reject-ping-ack-length",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.7", "PING acknowledgments also require exactly eight payload octets.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire
            .writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, Http2Wire.Ack, 0, Opaque.take(7)))
          _ <- connectionClosed(peer, Set(6L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.client.reject-ping-ack-length",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.7", "PING acknowledgments also require exactly eight payload octets.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, Http2Wire.Ack, 0, Opaque.take(7))) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(6L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3
    WireCase(
      "h2.server.reject-priority-stream-zero",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.3", "A PRIORITY frame on stream zero is a connection error.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(Http2Wire.Priority, 0, 0, Http2Wire.uint32(1L) ++ ByteVector(15)),
          )
          _ <- connectionClosed(peer, Set(1L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3
    WireCase(
      "h2.client.reject-priority-stream-zero",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.3", "A PRIORITY frame on stream zero is a connection error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(
                Http2Wire.Frame(Http2Wire.Priority, 0, 0, Http2Wire.uint32(1L) ++ ByteVector(15))
              ) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(1L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4
    WireCase(
      "h2.server.reject-rst-stream-zero",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.4", "RST_STREAM on stream zero is a connection error.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire
            .writeFrame(peer, Http2Wire.Frame(Http2Wire.RstStream, 0, 0, Http2Wire.uint32(8L)))
          _ <- connectionClosed(peer, Set(1L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4
    WireCase(
      "h2.client.reject-rst-stream-zero",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.4", "RST_STREAM on stream zero is a connection error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.Frame(Http2Wire.RstStream, 0, 0, Http2Wire.uint32(8L))) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(1L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4
    WireCase(
      "h2.server.reject-rst-idle-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.4", "RST_STREAM on an idle stream is a connection error.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire
            .writeFrame(peer, Http2Wire.Frame(Http2Wire.RstStream, 0, 17, Http2Wire.uint32(8L)))
          _ <- connectionClosed(peer, Set(1L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4
    WireCase(
      "h2.client.reject-rst-idle-stream",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.4", "RST_STREAM on an idle stream is a connection error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.Frame(Http2Wire.RstStream, 0, 17, Http2Wire.uint32(8L))) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(1L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4
    WireCase(
      "h2.server.reject-rst-length-three",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.4",
        "RST_STREAM with fewer than four payload octets is a connection frame-size error.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.headers(1, RequestFields, endStream = false))
          _ <- Http2Wire
            .writeFrame(peer, Http2Wire.Frame(Http2Wire.RstStream, 0, 1, ByteVector(0, 0, 8)))
          _ <- connectionClosed(peer, Set(6L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4
    WireCase(
      "h2.client.reject-rst-length-three",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.4",
        "RST_STREAM with fewer than four payload octets is a connection frame-size error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(
                Http2Wire.Frame(Http2Wire.RstStream, 0, streamId, ByteVector(0, 0, 8))
              ) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(6L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4
    WireCase(
      "h2.server.reject-rst-length-five",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.4",
        "RST_STREAM with more than four payload octets is a connection frame-size error.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.headers(1, RequestFields, endStream = false))
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(Http2Wire.RstStream, 0, 1, Http2Wire.uint32(8L) ++ ByteVector(0)),
          )
          _ <- connectionClosed(peer, Set(6L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4
    WireCase(
      "h2.client.reject-rst-length-five",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.4",
        "RST_STREAM with more than four payload octets is a connection frame-size error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(
                Http2Wire
                  .Frame(Http2Wire.RstStream, 0, streamId, Http2Wire.uint32(8L) ++ ByteVector(0))
              ) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(6L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.8
    WireCase(
      "h2.server.reject-goaway-stream-id",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.8", "GOAWAY on a nonzero stream identifier is a connection error.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(Http2Wire.GoAway, 0, 1, Http2Wire.uint32(0L) ++ Http2Wire.uint32(0L)),
          )
          _ <- connectionClosed(peer, Set(1L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.8
    WireCase(
      "h2.client.reject-goaway-stream-id",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.8", "GOAWAY on a nonzero stream identifier is a connection error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(
                Http2Wire
                  .Frame(Http2Wire.GoAway, 0, 1, Http2Wire.uint32(0L) ++ Http2Wire.uint32(0L))
              ) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(1L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.2
    WireCase(
      "h2.server.reject-goaway-length-seven",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "4.2",
        "GOAWAY shorter than its mandatory fixed fields is a connection frame-size error.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire
            .writeFrame(peer, Http2Wire.Frame(Http2Wire.GoAway, 0, 0, ByteVector.fill(7)(0.toByte)))
          _ <- connectionClosed(peer, Set(6L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.2
    WireCase(
      "h2.client.reject-goaway-length-seven",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "4.2",
        "GOAWAY shorter than its mandatory fixed fields is a connection frame-size error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(
                Http2Wire.Frame(Http2Wire.GoAway, 0, 0, ByteVector.fill(7)(0.toByte))
              ) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(6L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.reject-settings-server-enable-push-one",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "Clients reject a server explicitly enabling push with SETTINGS_ENABLE_PUSH equal to one.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.settings(Vector(2 -> 1L))) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(1L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.7
    WireCase(
      "h2.client.reject-ping-length-seven",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.7", "A PING with seven payload octets is a connection frame-size error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Opaque.take(7))) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(6L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.settings-client-enable-push-one",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "A client may explicitly enable push by sending SETTINGS_ENABLE_PUSH equal to one.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(2 -> 1L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.settings-header-table-zero",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(1 -> 0L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.settings-header-table-uint32",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(1 -> 0xffffffffL)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.settings-window-zero",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(4 -> 0L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.settings-window-maximum",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(4 -> 0x7fffffffL)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.settings-concurrency-zero",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(3 -> 0L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.settings-concurrency-uint32",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(3 -> 0xffffffffL)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.server.settings-header-list-zero",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(6 -> 0L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3
    WireCase(
      "h2.server.priority-idle",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.3",
        "A valid PRIORITY frame is accepted on an idle stream, with its opaque dependency and weight fields.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(Http2Wire.Priority, 0xff, 17, Http2Wire.uint32(0L) ++ ByteVector(0)),
          )
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- IO.unit
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3
    WireCase(
      "h2.server.priority-exclusive-max-dependency",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.3",
        "A valid PRIORITY frame is accepted on an idle stream, with its opaque dependency and weight fields.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(
              Http2Wire.Priority,
              0xff,
              17,
              Http2Wire.uint32(0xffffffffL) ++ ByteVector(255.toByte),
            ),
          )
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- IO.unit
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.settings-header-table-zero",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(1 -> 0L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.settings-header-table-uint32",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(1 -> 0xffffffffL)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.settings-window-zero",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(4 -> 0L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.settings-window-maximum",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(4 -> 0x7fffffffL)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.settings-concurrency-zero",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(3 -> 0L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.settings-concurrency-uint32",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(3 -> 0xffffffffL)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2
    WireCase(
      "h2.client.settings-header-list-zero",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.2",
        "The defined setting accepts this unsigned boundary value and is acknowledged.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(6 -> 0L)))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(frames =>
            frames.exists(isBarrier) && frames.count(isSettingsAck) >= 1
          )
          _ <- checkBarrier(observed)
          _ <- Check.equal(observed.count(isSettingsAck), 1)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3
    WireCase(
      "h2.client.priority-idle",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.3",
        "A valid PRIORITY frame is accepted on an idle stream, with its opaque dependency and weight fields.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(Http2Wire.Priority, 0xff, 17, Http2Wire.uint32(0L) ++ ByteVector(0)),
          )
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- IO.unit
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3
    WireCase(
      "h2.client.priority-exclusive-max-dependency",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.3",
        "A valid PRIORITY frame is accepted on an idle stream, with its opaque dependency and weight fields.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(EmptyRequest)
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Body)
            }
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          streamId <- establishClient(peer)
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(
              Http2Wire.Priority,
              0xff,
              17,
              Http2Wire.uint32(0xffffffffL) ++ ByteVector(255.toByte),
            ),
          )
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
          _ <- IO.unit
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.3
    WireCase(
      "h2.server.reject-settings-duplicate-invalid-first",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.3",
        "Every SETTINGS occurrence is processed in wire order; a repeated identifier does not hide an invalid value.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(5 -> 16383L, 5 -> 16384L)))
          _ <- connectionClosed(peer, Set(1L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.3
    WireCase(
      "h2.client.reject-settings-duplicate-invalid-first",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.3",
        "Every SETTINGS occurrence is processed in wire order; a repeated identifier does not hide an invalid value.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.settings(Vector(5 -> 16383L, 5 -> 16384L))) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(1L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.3
    WireCase(
      "h2.server.reject-settings-duplicate-invalid-last",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.3",
        "Every SETTINGS occurrence is processed in wire order; a repeated identifier does not hide an invalid value.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(5 -> 16384L, 5 -> 16383L)))
          _ <- connectionClosed(peer, Set(1L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.3
    WireCase(
      "h2.client.reject-settings-duplicate-invalid-last",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.5.3",
        "Every SETTINGS occurrence is processed in wire order; a repeated identifier does not hide an invalid value.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(EmptyRequest).use(_.body.compile.drain).attempt.flatMap { result =>
            requireRejection(result) *> closed.get
          }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            // A complete valid response follows in the same write: ignoring the bad frame
            // cannot make an accepted response appear to have been rejected by EOF.
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.settings(Vector(5 -> 16384L, 5 -> 16383L))) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body))
            )
            // Hold the target resource open until its protocol handler closes the TCP socket.
            _ <- connectionClosed(peer, Set(1L, 1L, 2L))
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3
    WireCase(
      "h2.server.priority-does-not-open-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.3",
        "PRIORITY does not open an idle stream or advance the stream identifier sequence.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, server) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(Http2Wire.Priority, 0, 17, Http2Wire.uint32(0L) ++ ByteVector(15)),
          )
          _ <- Http2Wire.writeFrame(peer, Http2Wire.headers(1, RequestFields))
          frames <- observeUntil(peer)(
            _.exists(frame =>
              frame.streamId == 1 && frame.hasFlag(
                Http2Wire.EndStream
              ) && (frame.frameType == Http2Wire.Headers || frame.frameType == Http2Wire.Data)
            )
          )
          _ <- checkResponse(frames, 1, Http1Wire.ascii("accepted"))
          received <- server.received
          _ <- Check.equal(received.path, "/control")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3
    WireCase(
      "h2.server.priority-on-closed-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.3",
        "PRIORITY is permitted on a closed stream and does not disrupt the connection.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.headers(1, RequestFields))
          first <- observeUntil(peer)(
            _.exists(frame =>
              frame.streamId == 1 && frame.hasFlag(
                Http2Wire.EndStream
              ) && (frame.frameType == Http2Wire.Headers || frame.frameType == Http2Wire.Data)
            )
          )
          _ <- checkResponse(first, 1, Http1Wire.ascii("accepted"))
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire
              .Frame(Http2Wire.Priority, 0, 1, Http2Wire.uint32(0L) ++ ByteVector(255.toByte)),
          )
          _ <- Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          observed <- observeUntil(peer)(_.exists(isBarrier))
          _ <- checkBarrier(observed)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3
    WireCase(
      "h2.server.reject-priority-length-4",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.3",
        "A PRIORITY frame with a non-five-octet payload is a stream frame-size error, which may be escalated.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(Http2Wire.Priority, 0, 17, ByteVector.fill(4)(0.toByte)),
          )
          _ <- streamRejected(peer, 17, Set(6L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3
    WireCase(
      "h2.client.reject-priority-length-4",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.3",
        "A PRIORITY frame with a non-five-octet payload is a stream frame-size error, which may be escalated.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { rejected =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(result => requireRejection(result) *> rejected.get)
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            _ <- peer.write(
              Http2Wire.encode(
                Http2Wire.Frame(Http2Wire.Priority, 0, streamId, ByteVector.fill(4)(0.toByte))
              ) ++
                Http2Wire.encode(Http2Wire.headers(streamId, Vector(":status" -> "200")))
            )
            _ <- streamRejected(peer, streamId, Set(6L, 1L, 2L))
            _ <- rejected.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3
    WireCase(
      "h2.server.reject-priority-length-6",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.3",
        "A PRIORITY frame with a non-five-octet payload is a stream frame-size error, which may be escalated.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(Http2Wire.Priority, 0, 17, ByteVector.fill(6)(0.toByte)),
          )
          _ <- streamRejected(peer, 17, Set(6L, 1L, 2L))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3
    WireCase(
      "h2.client.reject-priority-length-6",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.3",
        "A PRIORITY frame with a non-five-octet payload is a stream frame-size error, which may be escalated.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { rejected =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(result => requireRejection(result) *> rejected.get)
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            _ <- peer.write(
              Http2Wire.encode(
                Http2Wire.Frame(Http2Wire.Priority, 0, streamId, ByteVector.fill(6)(0.toByte))
              ) ++
                Http2Wire.encode(Http2Wire.headers(streamId, Vector(":status" -> "200")))
            )
            _ <- streamRejected(peer, streamId, Set(6L, 1L, 2L))
            _ <- rejected.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.2
    WireCase(
      "h2.server.default-frame-size-splits-data",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "4.2",
        "A sender keeps every frame within the peer default 16384-octet maximum, excluding the nine header octets.",
        "MUST",
      ),
    ) { context =>
      val body = ByteVector.fill(40000)(42.toByte)
      context.server(Protocol.Http2, ServerScenario.Reply(body = body)).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.headers(1, RequestFields))
          observed <- observeUntil(peer)(
            _.exists(frame =>
              frame.streamId == 1 && (frame.frameType == Http2Wire.Headers || frame.frameType == Http2Wire.Data) && frame
                .hasFlag(Http2Wire.EndStream)
            )
          )
          _ <- observed.traverse_(frame =>
            Check(
              frame.payload.size <= 16384L,
              s"frame exceeded default maximum: ${frame.payload.size}",
            )
          )
          _ <- checkResponse(observed, 1, body)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.2
    WireCase(
      "h2.client.default-frame-size-splits-data",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "4.2",
        "A client splits a large request body into frames within the peer default 16384-octet maximum.",
        "MUST",
      ),
    ) { context =>
      val body = ByteVector.fill(40000)(42.toByte)
      context.client(Protocol.Http2)(driver =>
        driver
          .exchange(
            RequestData(method = "POST", headers = Vector("Content-Length" -> "40000"), body = body)
          )
          .use { response =>
            response.body.compile
              .to(Chunk)
              .flatMap(bytes =>
                Check.equal(response.status, 200) *> Check.equal(bytes.toByteVector, Body)
              )
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          magic <- peer.readExact(24)
          _ <- Check.equal(magic, Http2Wire.clientPreface)
          initial <- Http2Wire.readFrame(peer)
          _ <- validSettings(initial, fromServer = false)
          _ <- peer.write(
            Http2Wire.encode(Http2Wire.settings()) ++ Http2Wire.encode(
              Http2Wire.settings(ack = true)
            )
          )
          observed <- observeUntil(peer)(frames =>
            frames.exists(isSettingsAck) && frames.exists(frame =>
              (frame.frameType == Http2Wire.Headers || frame.frameType == Http2Wire.Data) && frame
                .hasFlag(Http2Wire.EndStream)
            )
          )
          streamId = observed.find(_.frameType == Http2Wire.Headers).get.streamId
          _ <- observed.traverse_(frame =>
            Check(
              frame.payload.size <= 16384L,
              s"frame exceeded default maximum: ${frame.payload.size}",
            )
          )
          actual = observed
            .filter(_.frameType == Http2Wire.Data)
            .foldLeft(ByteVector.empty)((bytes, frame) => bytes ++ Http2Wire.unpad(frame))
          _ <- Check.equal(actual, body)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
            ) ++
              Http2Wire.encode(Http2Wire.data(streamId, Body))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.3
    WireCase(
      "h2.server.settings-replace-frame-limit",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.5.3",
        "An acknowledged lower frame limit replaces a prior larger limit before subsequent response frames.",
        "MUST",
      ),
    ) { context =>
      val body = ByteVector.fill(40000)(42.toByte)
      context.server(Protocol.Http2, ServerScenario.Reply(body = body)).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(5 -> 32768L)))
          _ <- observeUntil(peer)(_.exists(isSettingsAck))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(5 -> 32768L, 5 -> 16384L)))
          _ <- observeUntil(peer)(_.exists(isSettingsAck))
          _ <- Http2Wire.writeFrame(peer, Http2Wire.headers(1, RequestFields))
          observed <- observeUntil(peer)(
            _.exists(frame =>
              frame.streamId == 1 && (frame.frameType == Http2Wire.Headers || frame.frameType == Http2Wire.Data) && frame
                .hasFlag(Http2Wire.EndStream)
            )
          )
          _ <- observed.traverse_(frame =>
            Check(
              frame.payload.size <= 16384L,
              s"frame exceeded replaced maximum: ${frame.payload.size}",
            )
          )
          _ <- checkResponse(observed, 1, body)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.2
    WireCase(
      "h2.server.rst-cancel-no-response",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "5.4.2", "A received RST_STREAM is not answered with another RST_STREAM.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.headers(1, RequestFields, endStream = false))
          _ <- peer.write(
            Http2Wire.encode(Http2Wire.Frame(Http2Wire.RstStream, 0xff, 1, Http2Wire.uint32(8L))) ++
              Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          )
          observed <- barrierOrClose(peer)
          _ <- Check(!observed.exists(_.frameType == Http2Wire.RstStream), "RST_STREAM was echoed")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.2
    WireCase(
      "h2.client.rst-cancel-no-response",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "5.4.2",
        "A received RST_STREAM terminates the operation without a reset response, including unknown error codes.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { observed =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(result => requireRejection(result) *> observed.get)
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            _ <- peer.write(
              Http2Wire.encode(
                Http2Wire.Frame(Http2Wire.RstStream, 0xff, streamId, Http2Wire.uint32(8L))
              ) ++
                Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
            )
            frames <- barrierOrClose(peer)
            _ <- Check(!frames.exists(_.frameType == Http2Wire.RstStream), "RST_STREAM was echoed")
            _ <- observed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.2
    WireCase(
      "h2.server.rst-unknown-error-no-response",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "5.4.2", "A received RST_STREAM is not answered with another RST_STREAM.", "MUST"),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.headers(1, RequestFields, endStream = false))
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.Frame(Http2Wire.RstStream, 0xff, 1, Http2Wire.uint32(4294967295L))
            ) ++
              Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          )
          observed <- barrierOrClose(peer)
          _ <- Check(!observed.exists(_.frameType == Http2Wire.RstStream), "RST_STREAM was echoed")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.2
    WireCase(
      "h2.client.rst-unknown-error-no-response",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "5.4.2",
        "A received RST_STREAM terminates the operation without a reset response, including unknown error codes.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { observed =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(result => requireRejection(result) *> observed.get)
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            _ <- peer.write(
              Http2Wire.encode(
                Http2Wire.Frame(Http2Wire.RstStream, 0xff, streamId, Http2Wire.uint32(4294967295L))
              ) ++
                Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
            )
            frames <- barrierOrClose(peer)
            _ <- Check(!frames.exists(_.frameType == Http2Wire.RstStream), "RST_STREAM was echoed")
            _ <- observed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.8
    WireCase(
      "h2.server.goaway-debug-data",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.8",
        "A valid GOAWAY permits connection shutdown; opaque debug bytes and unused flags do not create protocol errors.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.Frame(
                Http2Wire.GoAway,
                0xff,
                0,
                Http2Wire.uint32(0L) ++ Http2Wire.uint32(0L) ++ Opaque,
              )
            ) ++
              Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          )
          frames <- barrierOrClose(peer)
          _ <- frames.filter(_.frameType == Http2Wire.GoAway).traverse_ { frame =>
            Check(frame.payload.size >= 8L, "short GOAWAY") *>
              Check(
                Set(0L, 2L).contains(Http2Wire.readUint32(frame.payload.slice(4, 8))),
                s"valid GOAWAY elicited a protocol error: $frame",
              )
          }
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.8
    WireCase(
      "h2.server.goaway-reserved-last-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.8",
        "A valid GOAWAY permits connection shutdown; opaque debug bytes and unused flags do not create protocol errors.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.Frame(
                Http2Wire.GoAway,
                0xff,
                0,
                Http2Wire.uint32(0x80000000L) ++ Http2Wire.uint32(0L) ++ Opaque,
              )
            ) ++
              Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          )
          frames <- barrierOrClose(peer)
          _ <- frames.filter(_.frameType == Http2Wire.GoAway).traverse_ { frame =>
            Check(frame.payload.size >= 8L, "short GOAWAY") *>
              Check(
                Set(0L, 2L).contains(Http2Wire.readUint32(frame.payload.slice(4, 8))),
                s"valid GOAWAY elicited a protocol error: $frame",
              )
          }
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-7
    WireCase(
      "h2.server.goaway-unknown-error",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "7",
        "A valid GOAWAY permits connection shutdown; opaque debug bytes and unused flags do not create protocol errors.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http2).use { case (peer, _) =>
        for {
          _ <- establishServer(peer)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.Frame(
                Http2Wire.GoAway,
                0xff,
                0,
                Http2Wire.uint32(0L) ++ Http2Wire.uint32(4294967295L) ++ Opaque,
              )
            ) ++
              Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
          )
          frames <- barrierOrClose(peer)
          _ <- frames.filter(_.frameType == Http2Wire.GoAway).traverse_ { frame =>
            Check(frame.payload.size >= 8L, "short GOAWAY") *>
              Check(
                Set(0L, 2L).contains(Http2Wire.readUint32(frame.payload.slice(4, 8))),
                s"valid GOAWAY elicited a protocol error: $frame",
              )
          }
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.8
    WireCase(
      "h2.client.goaway-debug-data",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.8",
        "GOAWAY may complete or interrupt an existing stream; debug bytes and unknown codes do not create a protocol error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { observed =>
        context.client(Protocol.Http2)(driver =>
          // This non-idempotent request is included in Last-Stream-ID, so a closure is
          // not evidence that the request can be automatically retried on a new socket.
          driver
            .exchange(EmptyRequest.copy(method = "POST"))
            .use { response =>
              response.body.compile.to(Chunk).map(body => response.status -> body.toByteVector)
            }
            .attempt
            .flatMap {
              case Left(error) if causedByTimeout(error) => IO.raiseError(error)
              case Left(_) => observed.get
              case Right((status, body)) =>
                Check.equal(status, 200) *> Check.equal(body, Body) *> observed.get
            }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            _ <- peer.write(
              Http2Wire.encode(
                Http2Wire.Frame(
                  Http2Wire.GoAway,
                  0xff,
                  0,
                  Http2Wire.uint32(streamId.toLong) ++ Http2Wire.uint32(0L) ++ Opaque,
                )
              ) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body)) ++
                Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
            )
            frames <- barrierOrClose(peer)
            _ <- frames.filter(_.frameType == Http2Wire.GoAway).traverse_ { frame =>
              Check(frame.payload.size >= 8L, "short GOAWAY") *>
                Check(
                  Set(0L, 2L).contains(Http2Wire.readUint32(frame.payload.slice(4, 8))),
                  s"valid GOAWAY elicited a protocol error: $frame",
                )
            }
            _ <- observed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-7
    WireCase(
      "h2.client.goaway-unknown-error",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "7",
        "GOAWAY may complete or interrupt an existing stream; debug bytes and unknown codes do not create a protocol error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { observed =>
        context.client(Protocol.Http2)(driver =>
          // This non-idempotent request is included in Last-Stream-ID, so a closure is
          // not evidence that the request can be automatically retried on a new socket.
          driver
            .exchange(EmptyRequest.copy(method = "POST"))
            .use { response =>
              response.body.compile.to(Chunk).map(body => response.status -> body.toByteVector)
            }
            .attempt
            .flatMap {
              case Left(error) if causedByTimeout(error) => IO.raiseError(error)
              case Left(_) => observed.get
              case Right((status, body)) =>
                Check.equal(status, 200) *> Check.equal(body, Body) *> observed.get
            }
        ) { peer =>
          for {
            streamId <- establishClient(peer)
            _ <- peer.write(
              Http2Wire.encode(
                Http2Wire.Frame(
                  Http2Wire.GoAway,
                  0xff,
                  0,
                  Http2Wire.uint32(streamId.toLong) ++ Http2Wire.uint32(4294967295L) ++ Opaque,
                )
              ) ++
                Http2Wire.encode(
                  Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)
                ) ++
                Http2Wire.encode(Http2Wire.data(streamId, Body)) ++
                Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, Barrier))
            )
            frames <- barrierOrClose(peer)
            _ <- frames.filter(_.frameType == Http2Wire.GoAway).traverse_ { frame =>
              Check(frame.payload.size >= 8L, "short GOAWAY") *>
                Check(
                  Set(0L, 2L).contains(Http2Wire.readUint32(frame.payload.slice(4, 8))),
                  s"valid GOAWAY elicited a protocol error: $frame",
                )
            }
            _ <- observed.complete(())
          } yield ()
        }
      }
    },
  )

  private val Body = Http1Wire.ascii("control")
  private val Barrier = ByteVector(0, 1, 2, 3, 4, 5, 6, 7)
  private val Opaque = ByteVector(0, 127, 128, 255, 13, 10, 42, 0)
  private val EmptyRequest = RequestData(headers = Vector("Content-Length" -> "0"))
  private val RequestFields = Vector(
    ":method" -> "POST",
    ":scheme" -> "http",
    ":authority" -> "wire.test",
    ":path" -> "/control",
  )

  private def isSettingsAck(frame: Http2Wire.Frame): Boolean =
    frame.frameType == Http2Wire.Settings && frame.hasFlag(Http2Wire.Ack)

  private def isBarrier(frame: Http2Wire.Frame): Boolean =
    frame.frameType == Http2Wire.Ping && frame.hasFlag(Http2Wire.Ack) && frame.payload == Barrier

  private def checkBarrier(frames: Vector[Http2Wire.Frame]): IO[Unit] =
    Check.equal(frames.filter(isBarrier).map(_.streamId), Vector(0))

  private def requestComplete(frames: Vector[Http2Wire.Frame]): Boolean =
    frames.exists(_.frameType == Http2Wire.Headers) && frames.exists(frame =>
      (frame.frameType == Http2Wire.Headers || frame.frameType == Http2Wire.Continuation) &&
        frame.hasFlag(Http2Wire.EndHeaders)
    ) && frames.exists(frame =>
      // Even a zero-length request may end in a separate empty DATA frame. Consume
      // it before injecting the fault so legal in-flight request bytes cannot be
      // mistaken for the target's reaction to the malformed control frame.
      (frame.frameType == Http2Wire.Headers || frame.frameType == Http2Wire.Data) &&
        frame.hasFlag(Http2Wire.EndStream)
    )

  /** All frames remain visible to the case. Only ordinary peer housekeeping is automatic. */
  private def observeUntil(peer: RawPeer[IO])(
      complete: Vector[Http2Wire.Frame] => Boolean
  ): IO[Vector[Http2Wire.Frame]] = {
    def loop(frames: Vector[Http2Wire.Frame]): IO[Vector[Http2Wire.Frame]] =
      if (frames.size >= 1024)
        IO.raiseError(new ProtocolFailure("control-frame observation limit exceeded"))
      else
        Http2Wire.readFrame(peer).flatMap { frame =>
          val next = frames :+ frame
          val housekeeping = frame.frameType match {
            case Http2Wire.Settings =>
              validSettings(frame, fromServer = false) *>
                (if (frame.hasFlag(Http2Wire.Ack)) IO.unit
                 else Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true)))
            case Http2Wire.Ping =>
              Check.equal(frame.streamId, 0) *> Check.equal(frame.payload.size, 8L) *>
                (if (frame.hasFlag(Http2Wire.Ack)) IO.unit
                 else Http2Wire.writeFrame(peer, frame.copy(flags = Http2Wire.Ack)))
            case Http2Wire.GoAway =>
              IO.raiseError(
                new ProtocolFailure(s"unexpected GOAWAY before the control barrier: $frame")
              )
            case Http2Wire.RstStream =>
              IO.raiseError(
                new ProtocolFailure(s"unexpected RST_STREAM before the control barrier: $frame")
              )
            case _ => IO.unit
          }
          housekeeping *> (if (complete(next)) IO.pure(next) else IO.defer(loop(next)))
        }
    loop(Vector.empty)
  }

  private def settingsValues(frame: Http2Wire.Frame): Vector[(Int, Long)] =
    frame.payload
      .grouped(6)
      .map { value =>
        (((value(0) & 0xff) << 8) | (value(1) & 0xff)) -> Http2Wire.readUint32(value.drop(2))
      }
      .toVector

  private def validSettings(frame: Http2Wire.Frame, fromServer: Boolean): IO[Unit] =
    for {
      _ <- Check.equal(frame.frameType, Http2Wire.Settings)
      _ <- Check.equal(frame.streamId, 0)
      _ <- Check.equal(frame.payload.size % 6L, 0L)
      _ <- Check(
        !frame.hasFlag(Http2Wire.Ack) || frame.payload.isEmpty,
        "SETTINGS ACK carried a payload",
      )
      _ <- settingsValues(frame).traverse_ { case (key, value) =>
        key match {
          case 2 =>
            Check(value <= (if (fromServer) 0L else 1L), s"invalid ENABLE_PUSH value $value")
          case 4 => Check(value <= 0x7fffffffL, s"invalid INITIAL_WINDOW_SIZE value $value")
          case 5 =>
            Check(value >= 16384L && value <= 0xffffffL, s"invalid MAX_FRAME_SIZE value $value")
          case _ => IO.unit
        }
      }
    } yield ()

  private def establishServer(peer: RawPeer[IO]): IO[Http2Wire.Frame] =
    for {
      _ <- peer.write(Http2Wire.clientPreface ++ Http2Wire.encode(Http2Wire.settings()))
      initial <- Http2Wire.readFrame(peer)
      _ <- validSettings(initial, fromServer = true)
      _ <- Check(!initial.hasFlag(Http2Wire.Ack), "server preface must advertise settings")
      _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))
      _ <- observeUntil(peer)(_.exists(isSettingsAck))
    } yield initial

  private def establishClient(peer: RawPeer[IO]): IO[Int] =
    for {
      magic <- peer.readExact(24)
      _ <- Check.equal(magic, Http2Wire.clientPreface)
      initial <- Http2Wire.readFrame(peer)
      _ <- validSettings(initial, fromServer = false)
      _ <- Check(!initial.hasFlag(Http2Wire.Ack), "client preface must advertise settings")
      _ <- peer.write(
        Http2Wire.encode(Http2Wire.settings()) ++ Http2Wire.encode(Http2Wire.settings(ack = true))
      )
      frames <- observeUntil(peer)(frames =>
        frames.exists(isSettingsAck) && requestComplete(frames)
      )
      streamId = frames.find(_.frameType == Http2Wire.Headers).get.streamId
      _ <- Check(
        streamId > 0 && (streamId & 1) == 1,
        "client request stream identifier must be positive and odd",
      )
    } yield streamId

  /** An error GOAWAY alone is insufficient: connection errors must close the transport. */
  private def connectionClosed(peer: RawPeer[IO], allowedCodes: Set[Long]): IO[Unit] = {
    def loop(remaining: Int): IO[Unit] =
      if (remaining == 0) Check(false, "connection did not close within frame observation limit")
      else
        frameOrEof(peer).flatMap {
          case None => IO.unit
          case Some(frame) =>
            val check =
              if (frame.frameType == Http2Wire.GoAway)
                Check.equal(frame.streamId, 0) *>
                  Check(frame.payload.size >= 8L, "GOAWAY omitted mandatory fixed fields") *>
                  Check(
                    allowedCodes.contains(Http2Wire.readUint32(frame.payload.slice(4, 8))),
                    s"unexpected GOAWAY error code: $frame",
                  )
              else IO.unit
            check *> IO.defer(loop(remaining - 1))
        }
    loop(1024)
  }

  private def checkResponse(
      frames: Vector[Http2Wire.Frame],
      streamId: Int,
      expected: ByteVector,
  ): IO[Unit] =
    for {
      decoder <- HpackWire.decoder[IO]()
      selected = frames.filter(_.streamId == streamId)
      block = selected
        .filter(frame =>
          frame.frameType == Http2Wire.Headers || frame.frameType == Http2Wire.Continuation
        )
        .foldLeft(ByteVector.empty) { (bytes, frame) =>
          val unpadded =
            if (frame.frameType == Http2Wire.Headers) Http2Wire.unpad(frame) else frame.payload
          bytes ++ (if (frame.frameType == Http2Wire.Headers && frame.hasFlag(0x20))
                      unpadded.drop(5)
                    else unpadded)
        }
      fields <- decoder.decode(block)
      _ <- Check.equal(fields.collect { case (":status", value) => value }, Vector("200"))
      actual = selected
        .filter(_.frameType == Http2Wire.Data)
        .foldLeft(ByteVector.empty)((bytes, frame) => bytes ++ Http2Wire.unpad(frame))
      _ <- Check.equal(actual, expected)
    } yield ()

  /** Transport termination is accepted only at a frame boundary, never within a frame. */
  private def frameOrEof(peer: RawPeer[IO]): IO[Option[Http2Wire.Frame]] =
    Http2Wire.readFrameOrClose(peer)

  /** A stream error may use RST_STREAM, or escalate to a GOAWAY and actual EOF. */
  private def streamRejected(
      peer: RawPeer[IO],
      streamId: Int,
      allowedCodes: Set[Long],
  ): IO[Unit] = {
    def loop(remaining: Int): IO[Unit] =
      if (remaining == 0) Check(false, "no stream error within frame observation limit")
      else
        frameOrEof(peer).flatMap {
          case None => IO.unit
          case Some(frame) if frame.frameType == Http2Wire.RstStream =>
            Check.equal(frame.streamId, streamId) *> Check.equal(frame.payload.size, 4L) *>
              Check(
                allowedCodes.contains(Http2Wire.readUint32(frame.payload)),
                s"unexpected stream error code: $frame",
              )
          case Some(frame) if frame.frameType == Http2Wire.GoAway =>
            Check.equal(frame.streamId, 0) *> Check(frame.payload.size >= 8L, "short GOAWAY") *>
              Check(
                allowedCodes.contains(Http2Wire.readUint32(frame.payload.slice(4, 8))),
                s"unexpected connection error code: $frame",
              ) *>
              connectionClosed(peer, allowedCodes)
          case Some(frame) if frame.frameType == Http2Wire.Settings =>
            validSettings(frame, fromServer = false) *>
              (if (frame.hasFlag(Http2Wire.Ack)) IO.unit
               else Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))) *>
              IO.defer(loop(remaining - 1))
          case Some(frame) if frame.frameType == Http2Wire.Ping && !frame.hasFlag(Http2Wire.Ack) =>
            Check.equal(frame.streamId, 0) *> Check.equal(frame.payload.size, 8L) *>
              Http2Wire.writeFrame(peer, frame.copy(flags = Http2Wire.Ack)) *> IO.defer(
                loop(remaining - 1)
              )
          case Some(frame)
              if frame.frameType == Http2Wire.WindowUpdate || frame.frameType > Http2Wire.Continuation =>
            IO.defer(loop(remaining - 1))
          case Some(frame) =>
            Check(false, s"unexpected frame while waiting for stream rejection: $frame")
        }
    loop(1024)
  }

  /** Collect the response to a PING barrier, or prove actual closure while the target is held. */
  private def barrierOrClose(peer: RawPeer[IO]): IO[Vector[Http2Wire.Frame]] = {
    def loop(frames: Vector[Http2Wire.Frame]): IO[Vector[Http2Wire.Frame]] =
      if (frames.size >= 1024)
        IO.raiseError(new ProtocolFailure("control-frame observation limit exceeded"))
      else
        frameOrEof(peer).flatMap {
          case None => IO.pure(frames)
          case Some(frame) if isBarrier(frame) =>
            Check.equal(frame.streamId, 0).as(frames :+ frame)
          case Some(frame) if frame.frameType == Http2Wire.GoAway =>
            for {
              _ <- Check.equal(frame.streamId, 0)
              _ <- Check(frame.payload.size >= 8L, "short GOAWAY")
              code = Http2Wire.readUint32(frame.payload.slice(4, 8))
              result <-
                if (code == 0L) IO.defer(loop(frames :+ frame))
                else
                  peer
                    .readToEnd(65536, allowReset = true)
                    .flatMap(bytes => IO.fromEither(Http2Wire.decodeAll(bytes)))
                    .map(rest => frames ++ (frame +: rest))
            } yield result
          case Some(frame) if frame.frameType == Http2Wire.Settings =>
            validSettings(frame, fromServer = false) *>
              (if (frame.hasFlag(Http2Wire.Ack)) IO.unit
               else Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))) *>
              IO.defer(loop(frames :+ frame))
          case Some(frame) if frame.frameType == Http2Wire.Ping && !frame.hasFlag(Http2Wire.Ack) =>
            Check.equal(frame.streamId, 0) *> Check.equal(frame.payload.size, 8L) *>
              Http2Wire.writeFrame(peer, frame.copy(flags = Http2Wire.Ack)) *> IO.defer(
                loop(frames :+ frame)
              )
          case Some(frame) => IO.defer(loop(frames :+ frame))
        }
    loop(Vector.empty)
  }

  private def requireRejection(result: Either[Throwable, Unit]): IO[Unit] = result match {
    case Left(error) => if (causedByTimeout(error)) IO.raiseError(error) else IO.unit
    case Right(_) =>
      Check(false, "target completed an exchange despite a connection protocol error")
  }

  private def unexpectedTargetRejection(error: Throwable): IO[Unit] =
    if (error.isInstanceOf[ProtocolFailure] || causedByTimeout(error)) IO.raiseError(error)
    else
      IO.raiseError(new ProtocolFailure(s"target rejected a valid control-frame exchange: $error"))

  private def causedByTimeout(error: Throwable): Boolean = {
    @annotation.tailrec
    def loop(current: Throwable, remaining: Int): Boolean =
      if (current == null || remaining == 0) false
      else if (current.isInstanceOf[TimeoutException]) true
      else loop(current.getCause, remaining - 1)
    loop(error, 16)
  }
}
