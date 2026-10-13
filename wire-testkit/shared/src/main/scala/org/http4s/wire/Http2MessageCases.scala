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
import fs2.Chunk
import scodec.bits.ByteVector

/** HTTP/2 field sections, message mapping, and message-specific error handling. */
object Http2MessageCases {
  private val RequestFields = Vector(
    ":method" -> "GET",
    ":scheme" -> "http",
    ":authority" -> "wire.test",
    ":path" -> "/mapping",
  )
  private val EmptyRequest =
    RequestData(path = "/mapping", headers = Vector("Content-Length" -> "0"))
  private val Body = ByteVector(0, 1, 127, 128, 255)

  val all: Vector[WireCase] = Vector(
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.server.message-empty",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.1",
        "A complete valid request field section is decoded and delivered to the application.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.message-empty",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1",
        "A valid complete response is consumed without rejecting the field section.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, ByteVector.empty)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.server.message-binary-data",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.1",
        "Consecutive DATA frames carry the exact binary message content through END_STREAM.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(
              Http2Wire.headers(1, fields, endStream = false),
              Http2Wire.data(1, Body.take(2), endStream = false),
              Http2Wire.data(1, Body.drop(2)),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, Body)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.server.headers-fragmented-end-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.2",
        "HEADERS with END_STREAM may be completed by multiple same-stream CONTINUATION frames.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x-fragment" -> "several fragments")
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndStream,
                1,
                Http2Wire.literalFields(fields).take(3),
              ),
              Http2Wire
                .Frame(Http2Wire.Continuation, 0, 1, Http2Wire.literalFields(fields).slice(3, 7)),
              Http2Wire.Frame(
                Http2Wire.Continuation,
                Http2Wire.EndHeaders,
                1,
                Http2Wire.literalFields(fields).drop(7),
              ),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.10
    WireCase(
      "h2.server.headers-empty-final-continuation",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.10",
        "An empty CONTINUATION with END_HEADERS may terminate a field block.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(
              Http2Wire
                .Frame(Http2Wire.Headers, Http2Wire.EndStream, 1, Http2Wire.literalFields(fields)),
              Http2Wire.Frame(Http2Wire.Continuation, Http2Wire.EndHeaders, 1, ByteVector.empty),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.server.headers-zero-padding",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.2",
        "The PADDED flag permits a zero pad length and the pad length octet is not field-block data.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | Http2Wire.Padded,
                1,
                ByteVector(0) ++ Http2Wire.literalFields(fields),
              )
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.server.headers-padding",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "6.2", "HEADERS padding is excluded from the compressed field section.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | Http2Wire.Padded,
                1,
                ByteVector(3) ++ Http2Wire.literalFields(fields) ++ ByteVector(0, 0, 0),
              )
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.server.headers-priority-fields",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.2",
        "When PRIORITY is set the five priority octets precede the field block fragment.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | 0x20,
                1,
                Http2Wire.uint32(0L) ++ ByteVector(15) ++ Http2Wire.literalFields(fields),
              )
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.server.headers-padding-and-priority",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.2",
        "HEADERS with PADDED and PRIORITY place pad length before priority fields and exclude both from the field block.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | Http2Wire.Padded | 0x20,
                1,
                ByteVector(2) ++ Http2Wire.uint32(0L) ++ ByteVector(0) ++ Http2Wire
                  .literalFields(fields) ++ ByteVector(0, 0),
              )
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.message-binary-data",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1",
        "Consecutive DATA frames carry the exact binary message content through END_STREAM.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Body)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.headers(streamId, fields, endStream = false),
              Http2Wire.data(streamId, Body.take(2), endStream = false),
              Http2Wire.data(streamId, Body.drop(2)),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.client.headers-fragmented-end-stream",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.2",
        "HEADERS with END_STREAM may be completed by multiple same-stream CONTINUATION frames.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, ByteVector.empty)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x-fragment" -> "several fragments")
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndStream,
                streamId,
                Http2Wire.literalFields(fields).take(3),
              ),
              Http2Wire.Frame(
                Http2Wire.Continuation,
                0,
                streamId,
                Http2Wire.literalFields(fields).slice(3, 7),
              ),
              Http2Wire.Frame(
                Http2Wire.Continuation,
                Http2Wire.EndHeaders,
                streamId,
                Http2Wire.literalFields(fields).drop(7),
              ),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.10
    WireCase(
      "h2.client.headers-empty-final-continuation",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.10",
        "An empty CONTINUATION with END_HEADERS may terminate a field block.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, ByteVector.empty)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndStream,
                streamId,
                Http2Wire.literalFields(fields),
              ),
              Http2Wire.Frame(
                Http2Wire.Continuation,
                Http2Wire.EndHeaders,
                streamId,
                ByteVector.empty,
              ),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.client.headers-zero-padding",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.2",
        "The PADDED flag permits a zero pad length and the pad length octet is not field-block data.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, ByteVector.empty)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | Http2Wire.Padded,
                streamId,
                ByteVector(0) ++ Http2Wire.literalFields(fields),
              )
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.client.headers-padding",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.2", "HEADERS padding is excluded from the compressed field section.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, ByteVector.empty)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | Http2Wire.Padded,
                streamId,
                ByteVector(3) ++ Http2Wire.literalFields(fields) ++ ByteVector(0, 0, 0),
              )
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.client.headers-priority-fields",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.2",
        "When PRIORITY is set the five priority octets precede the field block fragment.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, ByteVector.empty)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | 0x20,
                streamId,
                Http2Wire.uint32(0L) ++ ByteVector(15) ++ Http2Wire.literalFields(fields),
              )
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.client.headers-padding-and-priority",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.2",
        "HEADERS with PADDED and PRIORITY place pad length before priority fields and exclude both from the field block.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, ByteVector.empty)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | Http2Wire.Padded | 0x20,
                streamId,
                ByteVector(2) ++ Http2Wire.uint32(0L) ++ ByteVector(0) ++ Http2Wire.literalFields(
                  fields
                ) ++ ByteVector(0, 0),
              )
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-uppercase-field-name",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "A prohibited request field makes the message malformed and requires a stream error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("X-Upper" -> "ok")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-value-nul",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "A prohibited request field makes the message malformed and requires a stream error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x-value" -> "a\u0000b")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-value-cr",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "A prohibited request field makes the message malformed and requires a stream error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x-value" -> "a\rb")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-value-lf",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "A prohibited request field makes the message malformed and requires a stream error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x-value" -> "a\nb")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.server.reject-connection-field",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.2",
        "Connection-specific header fields must not occur in HTTP/2 messages.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("connection" -> "test")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.server.reject-proxy-connection-field",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.2",
        "Connection-specific header fields must not occur in HTTP/2 messages.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("proxy-connection" -> "test")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.server.reject-keep-alive-field",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.2",
        "Connection-specific header fields must not occur in HTTP/2 messages.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("keep-alive" -> "test")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.server.reject-transfer-encoding-field",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.2",
        "Connection-specific header fields must not occur in HTTP/2 messages.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("transfer-encoding" -> "chunked")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.server.reject-upgrade-field",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.2",
        "Connection-specific header fields must not occur in HTTP/2 messages.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("upgrade" -> "test")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-uppercase-field-name",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "A prohibited response field makes the message malformed and must not be accepted.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("X-Upper" -> "ok")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-value-nul",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "A prohibited response field makes the message malformed and must not be accepted.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x-value" -> "a\u0000b")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-value-cr",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "A prohibited response field makes the message malformed and must not be accepted.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x-value" -> "a\rb")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-value-lf",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "A prohibited response field makes the message malformed and must not be accepted.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x-value" -> "a\nb")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.client.reject-connection-field",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.2",
        "Connection-specific header fields must not occur in HTTP/2 messages.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("connection" -> "test")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.client.reject-proxy-connection-field",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.2",
        "Connection-specific header fields must not occur in HTTP/2 messages.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("proxy-connection" -> "test")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.client.reject-keep-alive-field",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.2",
        "Connection-specific header fields must not occur in HTTP/2 messages.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("keep-alive" -> "test")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.client.reject-transfer-encoding-field",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.2",
        "Connection-specific header fields must not occur in HTTP/2 messages.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("transfer-encoding" -> "chunked")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.client.reject-upgrade-field",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.2",
        "Connection-specific header fields must not occur in HTTP/2 messages.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("upgrade" -> "test")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-name-nul",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x\u0000bad" -> "ok")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-name-tab",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x\tbad" -> "ok")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-name-space",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x bad" -> "ok")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-name-control-boundary",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x\u001fbad" -> "ok")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-name-uppercase-boundary",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("xZbad" -> "ok")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-name-del",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x\u007fbad" -> "ok")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-name-obs-text-low",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x\u0080bad" -> "ok")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-name-obs-text-high",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x\u00ffbad" -> "ok")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-name-colon",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x:bad" -> "ok")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-value-leading-space",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "8.2.1", "Field values must not begin or end with SP or HTAB.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x-value" -> " value")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-value-trailing-space",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "8.2.1", "Field values must not begin or end with SP or HTAB.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x-value" -> "value ")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-value-leading-tab",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "8.2.1", "Field values must not begin or end with SP or HTAB.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x-value" -> "\tvalue")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-field-value-trailing-tab",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "8.2.1", "Field values must not begin or end with SP or HTAB.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x-value" -> "value\t")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.validate-field-empty-name",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "Full HTTP field-name token validation is recommended beyond the mandatory minimum character restrictions.",
        "SHOULD",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("" -> "value")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.validate-field-separator-name",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "Full HTTP field-name token validation is recommended beyond the mandatory minimum character restrictions.",
        "SHOULD",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x/bad" -> "value")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // Syntax: RFC 9113 8.2.1; opaque preservation: RFC 9110 5.5 SHOULD.
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-5.5
    WireCase(
      "h2.server.field-value-interior-tab-and-obs-text",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9110,
        "5.5",
        "Recipients should treat the permitted obs-text field-value octets as opaque data.",
        "SHOULD",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x-value" -> "a\tb\u0080\u00ff")
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")
            _ <- Check(
              received.headers.exists { case (name, value) =>
                name.equalsIgnoreCase("x-value") && value == "a\tb\u0080\u00ff"
              },
              "valid field octets were not preserved",
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.field-value-empty",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "8.2.1", "An empty field value is syntactically permitted.", "syntax"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("x-empty" -> "")
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1
    WireCase(
      "h2.server.content-length-matching",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "8.1.1", "A content-length equal to the content octet count is valid.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("content-length" -> "5")
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(
              Http2Wire.headers(1, fields, endStream = false),
              Http2Wire.data(1, Body),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, Body)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1
    WireCase(
      "h2.server.content-length-padded-data",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.1.1",
        "Content-length counts content octets and excludes the DATA pad length field and padding.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(9L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("content-length" -> "5")
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(
              Http2Wire.headers(1, fields, endStream = false),
              Http2Wire.Frame(
                Http2Wire.Data,
                Http2Wire.EndStream | Http2Wire.Padded,
                1,
                ByteVector(3) ++ Body ++ ByteVector(0, 0, 0),
              ),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, Body)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1
    WireCase(
      "h2.server.reject-content-length-underflow",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.1.1",
        "A complete message whose content-length differs from its DATA content length is malformed.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("content-length" -> "6")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(
              Http2Wire.headers(1, fields, endStream = false),
              Http2Wire.data(1, Body),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // Section 8.1.1 permits progressive processing before a DATA-length fault.
            _ <- readRejection(
              peer,
              1,
              connection = false,
              code = 1L,
              fromServer = true,
              allowMessageFrames = true,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1
    WireCase(
      "h2.server.reject-content-length-overflow",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.1.1",
        "A complete message whose content-length differs from its DATA content length is malformed.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("content-length" -> "4")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(
              Http2Wire.headers(1, fields, endStream = false),
              Http2Wire.data(1, Body),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // Section 8.1.1 permits progressive processing before a DATA-length fault.
            _ <- readRejection(
              peer,
              1,
              connection = false,
              code = 1L,
              fromServer = true,
              allowMessageFrames = true,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.server.message-trailers",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.1",
        "A final trailer field section follows DATA and terminates the stream.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(
              Http2Wire.headers(1, fields, endStream = false),
              Http2Wire.data(1, Body, endStream = false),
              Http2Wire.headers(1, Vector("x-checksum" -> "five")),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, Body)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.server.message-empty-trailers",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "8.1", "An empty final trailer section can terminate a message after DATA.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(
              Http2Wire.headers(1, fields, endStream = false),
              Http2Wire.data(1, Body, endStream = false),
              Http2Wire.headers(1, Vector.empty),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, Body)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.server.message-fragmented-trailers",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.1",
        "A trailer HEADERS with END_STREAM can be followed by CONTINUATION to complete that trailer field block.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(
              Http2Wire.headers(1, fields, endStream = false),
              Http2Wire.data(1, Body, endStream = false),
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndStream,
                1,
                Http2Wire.literalFields(Vector("x-checksum" -> "five")).take(5),
              ),
              Http2Wire.Frame(
                Http2Wire.Continuation,
                Http2Wire.EndHeaders,
                1,
                Http2Wire.literalFields(Vector("x-checksum" -> "five")).drop(5),
              ),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, Body)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.server.reject-trailers-without-end-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.1",
        "A second field section after request headers or a final response must carry END_STREAM.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(
              Http2Wire.headers(1, fields, endStream = false),
              Http2Wire.data(1, Body, endStream = false),
              Http2Wire.headers(1, Vector("x-trailer" -> "value"), endStream = false),
              Http2Wire.data(1, ByteVector.empty),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              1,
              connection = false,
              code = 1L,
              fromServer = true,
              allowMessageFrames = true,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.server.reject-trailer-pseudo-header",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "8.3", "Pseudo-header fields are prohibited in trailer sections.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(
              Http2Wire.headers(1, fields, endStream = false),
              Http2Wire.data(1, Body, endStream = false),
              Http2Wire.headers(1, Vector(":path" -> "/mapping")),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              1,
              connection = false,
              code = 1L,
              fromServer = true,
              allowMessageFrames = true,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.server.reject-trailer-uppercase-name",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.1",
        "Mandatory field validation applies to trailers as well as initial header sections.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(
              Http2Wire.headers(1, fields, endStream = false),
              Http2Wire.data(1, Body, endStream = false),
              Http2Wire.headers(1, Vector("X-Trailer" -> "value")),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              1,
              connection = false,
              code = 1L,
              fromServer = true,
              allowMessageFrames = true,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.server.reject-trailer-prohibited-field",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.2",
        "A connection-specific field in a trailer section makes the message malformed.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(
              Http2Wire.headers(1, fields, endStream = false),
              Http2Wire.data(1, Body, endStream = false),
              Http2Wire.headers(1, Vector("connection" -> "close")),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              1,
              connection = false,
              code = 1L,
              fromServer = true,
              allowMessageFrames = true,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.server.reject-undefined-pseudo-header",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3",
        "Unnegotiated undefined pseudo-header fields make the message malformed.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector(":undefined" -> "value")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.server.reject-pseudo-header-after-regular-field",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "8.3", "Every pseudo-header field must precede every regular field line.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = Vector("x-first" -> "value") ++ RequestFields
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.server.reject-headers-stream-zero",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.2",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(0, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = true, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.server.reject-headers-padding-equals-payload",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.2",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            // Only the documented field or frame property differs from a complete valid request.
            // Start a valid message; the faulty frame contains only an empty trailer
            // block, so missing request/status pseudo-fields cannot satisfy rejection.
            _ <- Http2PeerSettings
              .writeFrames(peer, Vector(Http2Wire.headers(1, RequestFields, endStream = false)))
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | Http2Wire.Padded,
                1,
                ByteVector(1),
              )
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, Vector.empty)
            _ <- expectServerRejection(peer, 1, connection = true, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.server.reject-headers-padding-exceeds-payload",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.2",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            // Only the documented field or frame property differs from a complete valid request.
            // Start a valid message; the faulty frame contains only an empty trailer
            // block, so missing request/status pseudo-fields cannot satisfy rejection.
            _ <- Http2PeerSettings
              .writeFrames(peer, Vector(Http2Wire.headers(1, RequestFields, endStream = false)))
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | Http2Wire.Padded,
                1,
                ByteVector(2),
              )
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, Vector.empty)
            _ <- expectServerRejection(peer, 1, connection = true, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.server.reject-headers-missing-pad-length",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.2",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            // Only the documented field or frame property differs from a complete valid request.
            // Start a valid message; the faulty frame contains only an empty trailer
            // block, so missing request/status pseudo-fields cannot satisfy rejection.
            _ <- Http2PeerSettings
              .writeFrames(peer, Vector(Http2Wire.headers(1, RequestFields, endStream = false)))
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | Http2Wire.Padded,
                1,
                ByteVector.empty,
              )
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, Vector.empty)
            _ <- expectServerRejection(peer, 1, connection = true, code = 6L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.server.reject-headers-short-priority",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.2",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            // Only the documented field or frame property differs from a complete valid request.
            // Start a valid message; the faulty frame contains only an empty trailer
            // block, so missing request/status pseudo-fields cannot satisfy rejection.
            frames = Vector(
              Http2Wire.headers(1, RequestFields, endStream = false),
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | 0x20,
                1,
                ByteVector(0, 0, 0, 0),
              ),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = true, code = 6L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.10
    WireCase(
      "h2.server.reject-orphan-continuation",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.10",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(
              Http2Wire.Frame(Http2Wire.Continuation, Http2Wire.EndHeaders, 1, ByteVector.empty)
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = true, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.10
    WireCase(
      "h2.server.reject-continuation-stream-zero",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.10",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(
              Http2Wire
                .Frame(Http2Wire.Headers, Http2Wire.EndStream, 1, Http2Wire.literalFields(fields)),
              Http2Wire.Frame(Http2Wire.Continuation, Http2Wire.EndHeaders, 0, ByteVector.empty),
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, fields)
            _ <- expectServerRejection(peer, 1, connection = true, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.10
    WireCase(
      "h2.server.reject-continuation-different-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.10",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(
              Http2Wire
                .Frame(Http2Wire.Headers, Http2Wire.EndStream, 1, Http2Wire.literalFields(fields)),
              Http2Wire
                .Frame(Http2Wire.Continuation, Http2Wire.EndHeaders, 1 + 2, ByteVector.empty),
              Http2Wire.Frame(Http2Wire.Continuation, Http2Wire.EndHeaders, 1, ByteVector.empty),
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, fields)
            _ <- expectServerRejection(peer, 1, connection = true, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.server.reject-headers-interrupted-by-ping",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.2",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(
              Http2Wire
                .Frame(Http2Wire.Headers, Http2Wire.EndStream, 1, Http2Wire.literalFields(fields)),
              Http2Wire.Frame(Http2Wire.Ping, 0, 0, ByteVector(0, 1, 2, 3, 4, 5, 6, 7)),
              Http2Wire.Frame(Http2Wire.Continuation, Http2Wire.EndHeaders, 1, ByteVector.empty),
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, fields)
            _ <- expectServerRejection(peer, 1, connection = true, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.10
    WireCase(
      "h2.server.reject-continuation-interrupted-by-settings",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.10",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(
              Http2Wire
                .Frame(Http2Wire.Headers, Http2Wire.EndStream, 1, Http2Wire.literalFields(fields)),
              Http2Wire.Frame(Http2Wire.Continuation, 0, 1, ByteVector.empty),
              Http2Wire.settings(),
              Http2Wire.Frame(Http2Wire.Continuation, Http2Wire.EndHeaders, 1, ByteVector.empty),
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, fields)
            _ <- expectServerRejection(peer, 1, connection = true, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-name-nul",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x\u0000bad" -> "ok")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-name-tab",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x\tbad" -> "ok")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-name-space",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x bad" -> "ok")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-name-control-boundary",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x\u001fbad" -> "ok")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-name-uppercase-boundary",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("xZbad" -> "ok")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-name-del",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x\u007fbad" -> "ok")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-name-obs-text-low",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x\u0080bad" -> "ok")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-name-obs-text-high",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x\u00ffbad" -> "ok")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-name-colon",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "The mandatory field-name character restrictions apply after HPACK decompression.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x:bad" -> "ok")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-value-leading-space",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.2.1", "Field values must not begin or end with SP or HTAB.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x-value" -> " value")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-value-trailing-space",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.2.1", "Field values must not begin or end with SP or HTAB.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x-value" -> "value ")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-value-leading-tab",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.2.1", "Field values must not begin or end with SP or HTAB.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x-value" -> "\tvalue")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-field-value-trailing-tab",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.2.1", "Field values must not begin or end with SP or HTAB.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x-value" -> "value\t")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.validate-field-empty-name",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "Full HTTP field-name token validation is recommended beyond the mandatory minimum character restrictions.",
        "SHOULD",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("" -> "value")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.validate-field-separator-name",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "Full HTTP field-name token validation is recommended beyond the mandatory minimum character restrictions.",
        "SHOULD",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x/bad" -> "value")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // Syntax: RFC 9113 8.2.1; opaque preservation: RFC 9110 5.5 SHOULD.
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-5.5
    WireCase(
      "h2.client.field-value-interior-tab-and-obs-text",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9110,
        "5.5",
        "Recipients should treat the permitted obs-text field-value octets as opaque data.",
        "SHOULD",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, ByteVector.empty)
                _ <- Check(
                  response.headers.exists { case (name, value) =>
                    name.equalsIgnoreCase("x-value") && value == "a\tb\u0080\u00ff"
                  },
                  "valid field octets were not preserved",
                )
              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x-value" -> "a\tb\u0080\u00ff")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.field-value-empty",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.2.1", "An empty field value is syntactically permitted.", "syntax"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, ByteVector.empty)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("x-empty" -> "")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1
    WireCase(
      "h2.client.content-length-matching",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.1.1", "A content-length equal to the content octet count is valid.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Body)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("content-length" -> "5")
            frames = Vector(
              Http2Wire.headers(streamId, fields, endStream = false),
              Http2Wire.data(streamId, Body),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1
    WireCase(
      "h2.client.content-length-padded-data",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1.1",
        "Content-length counts content octets and excludes the DATA pad length field and padding.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(9L)
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Body)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("content-length" -> "5")
            frames = Vector(
              Http2Wire.headers(streamId, fields, endStream = false),
              Http2Wire.Frame(
                Http2Wire.Data,
                Http2Wire.EndStream | Http2Wire.Padded,
                streamId,
                ByteVector(3) ++ Body ++ ByteVector(0, 0, 0),
              ),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1
    WireCase(
      "h2.client.reject-content-length-underflow",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1.1",
        "A complete message whose content-length differs from its DATA content length is malformed.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("content-length" -> "6")
            frames = Vector(
              Http2Wire.headers(streamId, fields, endStream = false),
              Http2Wire.data(streamId, Body),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1
    WireCase(
      "h2.client.reject-content-length-overflow",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1.1",
        "A complete message whose content-length differs from its DATA content length is malformed.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector("content-length" -> "4")
            frames = Vector(
              Http2Wire.headers(streamId, fields, endStream = false),
              Http2Wire.data(streamId, Body),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.message-trailers",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1",
        "A final trailer field section follows DATA and terminates the stream.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Body)
                // RFC 9110 sections 6.5.1 and 10.1.4: without TE: trailers,
                // successful message completion does not promise API trailer retention.
                _ <- response.trailers.void
              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.headers(streamId, fields, endStream = false),
              Http2Wire.data(streamId, Body, endStream = false),
              Http2Wire.headers(streamId, Vector("x-checksum" -> "five")),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.message-empty-trailers",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.1", "An empty final trailer section can terminate a message after DATA.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Body)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.headers(streamId, fields, endStream = false),
              Http2Wire.data(streamId, Body, endStream = false),
              Http2Wire.headers(streamId, Vector.empty),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.message-fragmented-trailers",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1",
        "A trailer HEADERS with END_STREAM can be followed by CONTINUATION to complete that trailer field block.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Body)
                // RFC 9110 sections 6.5.1 and 10.1.4: without TE: trailers,
                // successful message completion does not promise API trailer retention.
                _ <- response.trailers.void
              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.headers(streamId, fields, endStream = false),
              Http2Wire.data(streamId, Body, endStream = false),
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndStream,
                streamId,
                Http2Wire.literalFields(Vector("x-checksum" -> "five")).take(5),
              ),
              Http2Wire.Frame(
                Http2Wire.Continuation,
                Http2Wire.EndHeaders,
                streamId,
                Http2Wire.literalFields(Vector("x-checksum" -> "five")).drop(5),
              ),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.reject-trailers-without-end-stream",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1",
        "A second field section after request headers or a final response must carry END_STREAM.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.headers(streamId, fields, endStream = false),
              Http2Wire.data(streamId, Body, endStream = false),
              Http2Wire.headers(streamId, Vector("x-trailer" -> "value"), endStream = false),
              Http2Wire.data(streamId, ByteVector.empty),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.client.reject-trailer-pseudo-header",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.3", "Pseudo-header fields are prohibited in trailer sections.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.headers(streamId, fields, endStream = false),
              Http2Wire.data(streamId, Body, endStream = false),
              Http2Wire.headers(streamId, Vector(":status" -> "200")),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1
    WireCase(
      "h2.client.reject-trailer-uppercase-name",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.1",
        "Mandatory field validation applies to trailers as well as initial header sections.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.headers(streamId, fields, endStream = false),
              Http2Wire.data(streamId, Body, endStream = false),
              Http2Wire.headers(streamId, Vector("X-Trailer" -> "value")),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.client.reject-trailer-prohibited-field",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.2",
        "A connection-specific field in a trailer section makes the message malformed.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget(5L)
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.headers(streamId, fields, endStream = false),
              Http2Wire.data(streamId, Body, endStream = false),
              Http2Wire.headers(streamId, Vector("connection" -> "close")),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.client.reject-undefined-pseudo-header",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.3",
        "Unnegotiated undefined pseudo-header fields make the message malformed.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200") ++ Vector(":undefined" -> "value")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.client.reject-pseudo-header-after-regular-field",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.3", "Every pseudo-header field must precede every regular field line.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector("x-first" -> "value") ++ Vector(":status" -> "200")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.client.reject-headers-stream-zero",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.2",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(Http2Wire.headers(0, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = true,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.client.reject-headers-padding-equals-payload",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.2",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            // Start a valid message; the faulty frame contains only an empty trailer
            // block, so missing request/status pseudo-fields cannot satisfy rejection.
            _ <- Http2PeerSettings.writeFrames(
              peer,
              Vector(Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)),
            )
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | Http2Wire.Padded,
                streamId,
                ByteVector(1),
              )
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, Vector.empty)
            _ <- readRejection(
              peer,
              streamId,
              connection = true,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.client.reject-headers-padding-exceeds-payload",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.2",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            // Start a valid message; the faulty frame contains only an empty trailer
            // block, so missing request/status pseudo-fields cannot satisfy rejection.
            _ <- Http2PeerSettings.writeFrames(
              peer,
              Vector(Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)),
            )
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | Http2Wire.Padded,
                streamId,
                ByteVector(2),
              )
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, Vector.empty)
            _ <- readRejection(
              peer,
              streamId,
              connection = true,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.client.reject-headers-missing-pad-length",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.2",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            // Start a valid message; the faulty frame contains only an empty trailer
            // block, so missing request/status pseudo-fields cannot satisfy rejection.
            _ <- Http2PeerSettings.writeFrames(
              peer,
              Vector(Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false)),
            )
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | Http2Wire.Padded,
                streamId,
                ByteVector.empty,
              )
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, Vector.empty)
            _ <- readRejection(
              peer,
              streamId,
              connection = true,
              code = 6L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.client.reject-headers-short-priority",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.2",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            // Start a valid message; the faulty frame contains only an empty trailer
            // block, so missing request/status pseudo-fields cannot satisfy rejection.
            frames = Vector(
              Http2Wire.headers(streamId, Vector(":status" -> "200"), endStream = false),
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream | 0x20,
                streamId,
                ByteVector(0, 0, 0, 0),
              ),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = true,
              code = 6L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.10
    WireCase(
      "h2.client.reject-orphan-continuation",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.10",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Continuation,
                Http2Wire.EndHeaders,
                streamId,
                ByteVector.empty,
              )
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = true,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.10
    WireCase(
      "h2.client.reject-continuation-stream-zero",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.10",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndStream,
                streamId,
                Http2Wire.literalFields(fields),
              ),
              Http2Wire.Frame(Http2Wire.Continuation, Http2Wire.EndHeaders, 0, ByteVector.empty),
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, fields)
            _ <- readRejection(
              peer,
              streamId,
              connection = true,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.10
    WireCase(
      "h2.client.reject-continuation-different-stream",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.10",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndStream,
                streamId,
                Http2Wire.literalFields(fields),
              ),
              Http2Wire
                .Frame(
                  Http2Wire.Continuation,
                  Http2Wire.EndHeaders,
                  streamId + 2,
                  ByteVector.empty,
                ),
              Http2Wire.Frame(
                Http2Wire.Continuation,
                Http2Wire.EndHeaders,
                streamId,
                ByteVector.empty,
              ),
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, fields)
            _ <- readRejection(
              peer,
              streamId,
              connection = true,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.client.reject-headers-interrupted-by-ping",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.2",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndStream,
                streamId,
                Http2Wire.literalFields(fields),
              ),
              Http2Wire.Frame(Http2Wire.Ping, 0, 0, ByteVector(0, 1, 2, 3, 4, 5, 6, 7)),
              Http2Wire.Frame(
                Http2Wire.Continuation,
                Http2Wire.EndHeaders,
                streamId,
                ByteVector.empty,
              ),
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, fields)
            _ <- readRejection(
              peer,
              streamId,
              connection = true,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.10
    WireCase(
      "h2.client.reject-continuation-interrupted-by-settings",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.10",
        "Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200")
            frames = Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndStream,
                streamId,
                Http2Wire.literalFields(fields),
              ),
              Http2Wire.Frame(Http2Wire.Continuation, 0, streamId, ByteVector.empty),
              Http2Wire.settings(),
              Http2Wire.Frame(
                Http2Wire.Continuation,
                Http2Wire.EndHeaders,
                streamId,
                ByteVector.empty,
              ),
            )
            _ <- Http2PeerSettings.writeFramingFault(peer, frames, fields)
            _ <- readRejection(
              peer,
              streamId,
              connection = true,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.reject-missing-method",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "A non-CONNECT request must contain exactly one valid :method, :scheme, and :path.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.filterNot(_._1 == ":method")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.reject-missing-scheme",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "A non-CONNECT request must contain exactly one valid :method, :scheme, and :path.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.filterNot(_._1 == ":scheme")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.reject-missing-path",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "A non-CONNECT request must contain exactly one valid :method, :scheme, and :path.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.filterNot(_._1 == ":path")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.server.reject-repeated-method",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3",
        "The same pseudo-header field name must not occur more than once, even with identical values.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector(":method" -> "GET")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.server.reject-repeated-scheme",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3",
        "The same pseudo-header field name must not occur more than once, even with identical values.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector(":scheme" -> "http")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.server.reject-repeated-authority",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3",
        "The same pseudo-header field name must not occur more than once, even with identical values.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector(":authority" -> "wire.test")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.server.reject-repeated-path",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3",
        "The same pseudo-header field name must not occur more than once, even with identical values.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector(":path" -> "/mapping")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.server.reject-response-pseudo-header",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "8.3", "A response-only pseudo-header field is invalid in a request.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector(":status" -> "200")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.reject-empty-method",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "A request pseudo-header value must obey its referenced method, URI, or path grammar.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.map { case (name, value) =>
              if (name == ":method") name -> "" else name -> value
            }
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.reject-invalid-method-token",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "A request pseudo-header value must obey its referenced method, URI, or path grammar.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.map { case (name, value) =>
              if (name == ":method") name -> "G ET" else name -> value
            }
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.reject-empty-scheme",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "A request pseudo-header value must obey its referenced method, URI, or path grammar.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.map { case (name, value) =>
              if (name == ":scheme") name -> "" else name -> value
            }
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.reject-invalid-scheme",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "A request pseudo-header value must obey its referenced method, URI, or path grammar.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.map { case (name, value) =>
              if (name == ":scheme") name -> "ht tp" else name -> value
            }
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.reject-empty-path",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "A request pseudo-header value must obey its referenced method, URI, or path grammar.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.map { case (name, value) =>
              if (name == ":path") name -> "" else name -> value
            }
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.reject-relative-path",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "A request pseudo-header value must obey its referenced method, URI, or path grammar.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.map { case (name, value) =>
              if (name == ":path") name -> "relative" else name -> value
            }
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.reject-path-fragment",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "A request pseudo-header value must obey its referenced method, URI, or path grammar.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.map { case (name, value) =>
              if (name == ":path") name -> "/path#fragment" else name -> value
            }
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.reject-path-space",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "A request pseudo-header value must obey its referenced method, URI, or path grammar.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.map { case (name, value) =>
              if (name == ":path") name -> "/a b" else name -> value
            }
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.reject-authority-userinfo",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "A request pseudo-header value must obey its referenced method, URI, or path grammar.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.map { case (name, value) =>
              if (name == ":authority") name -> "user@wire.test" else name -> value
            }
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.request-path-and-query",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "The :path field carries the absolute path and optional query without discarding escaped octets.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.filterNot(_._1 == ":path") :+ (":path" -> "/a%2Fb?x=%20&x=two")
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(normalizeEscapeCase(received.path), "/a%2Fb?x=%20&x=two")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.request-options-asterisk",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "An asterisk-form OPTIONS request is represented by :path equal to an asterisk.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.map { case (name, value) =>
              name -> (if (name == ":method") "OPTIONS" else if (name == ":path") "*" else value)
            }
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "OPTIONS")
            _ <- Check.equal(received.path, "*")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.server.request-host-without-authority",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.3.1",
        "A received translated request can convey authority with Host while retaining all mandatory request pseudo-header fields.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields.filterNot(_._1 == ":authority") ++ Vector("host" -> "wire.test")
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.server.request-te-trailers",
      Protocol.Http2,
      Role.Server,
      Rfc(9113, "8.2.2", "TE is permitted in requests when its only value is trailers.", "MAY"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("te" -> "trailers")
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")

          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.server.reject-te-gzip",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.2",
        "A request TE field must not contain any value other than trailers.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("te" -> "gzip")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.server.reject-te-mixed",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.2",
        "A request TE field must not contain any value other than trailers.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector("te" -> "trailers, gzip")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.3
    WireCase(
      "h2.server.request-split-cookie",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2.3",
        "A valid HTTP/2 request may contain split Cookie field lines.",
        "syntax",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = RequestFields ++ Vector(
              "cookie" -> "a=b",
              "cookie" -> "c=d",
              "cookie" -> "e=f",
            )
            // The complete stream is sent, so EchoBody observes content through END_STREAM.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            response <- readResponse(peer, 1)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            received <- server.received
            _ <- Check.equal(received.method, "GET")
            _ <- Check.equal(received.path, "/mapping")
            // The ordered-field adapter contract does not establish a generic non-HTTP/2
            // application boundary. Section 8.2.3 joining at that boundary is an inventory gap.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2
    WireCase(
      "h2.client.reject-response-te",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2.2",
        "The TE exception is for requests; a response must not contain this connection-specific field.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200", "te" -> "trailers")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.2
    WireCase(
      "h2.client.reject-missing-status",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.3.2",
        "Every response, including interim responses, must include :status.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector("x-field" -> "value")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.client.reject-request-method-pseudo-header",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.3", "Request-only pseudo-header fields must not occur in responses.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200", ":method" -> "GET")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.client.reject-request-scheme-pseudo-header",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.3", "Request-only pseudo-header fields must not occur in responses.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200", ":scheme" -> "http")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.client.reject-request-authority-pseudo-header",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.3", "Request-only pseudo-header fields must not occur in responses.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200", ":authority" -> "wire.test")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.client.reject-request-path-pseudo-header",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.3", "Request-only pseudo-header fields must not occur in responses.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200", ":path" -> "/mapping")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.client.reject-repeated-status-identical",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.3", "A response must not repeat the :status pseudo-header field.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200", ":status" -> "200")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3
    WireCase(
      "h2.client.reject-repeated-status-different",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.3", "A response must not repeat the :status pseudo-header field.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200", ":status" -> "404")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.2
    WireCase(
      "h2.client.reject-status-short",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.3.2",
        "The :status value must obey the three-digit HTTP status-code grammar.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "20")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.2
    WireCase(
      "h2.client.reject-status-long",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.3.2",
        "The :status value must obey the three-digit HTTP status-code grammar.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "2000")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.2
    WireCase(
      "h2.client.reject-status-non-digit",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.3.2",
        "The :status value must obey the three-digit HTTP status-code grammar.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "2x0")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.reject-informational-end-stream",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.1", "An informational HEADERS block with END_STREAM is malformed.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "103")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.6
    WireCase(
      "h2.client.reject-switching-protocols",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.6", "HTTP/2 does not support status 101 Switching Protocols.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        clientUntilObserved(context) { (driver, observed) =>
          // Attempt only the target operation. Script and frame-oracle errors remain failures.
          ClientObservation(
            driver
              .exchange(EmptyRequest)
          ) { response =>
            response.body.compile.drain *> response.trailers.void
          } { result =>
            result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
              expectTargetRejection(result) *> observed
          }
        } { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "101")
            frames = Vector(
              Http2Wire.headers(streamId, fields, endStream = false),
              Http2Wire.headers(streamId, Vector(":status" -> "200")),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- readRejection(
              peer,
              streamId,
              connection = false,
              code = 1L,
              fromServer = false,
              allowMessageFrames = false,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1
    WireCase(
      "h2.client.head-response-content-length",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1.1",
        "A response to HEAD can carry nonzero content-length while including no DATA content.",
        "MAY",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest.copy(method = "HEAD"))
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, ByteVector.empty)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "200", "content-length" -> "123")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1
    WireCase(
      "h2.client.not-modified-content-length",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1.1",
        "A 304 response can carry nonzero content-length while including no DATA content.",
        "MAY",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 304)
                _ <- Check.equal(body.toByteVector, ByteVector.empty)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "304", "content-length" -> "123")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1
    WireCase(
      "h2.client.no-content-response",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "8.1.1", "A 204 response has no message content.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 204)
                _ <- Check.equal(body.toByteVector, ByteVector.empty)

              } yield ()
            }
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            fields = Vector(":status" -> "204")
            frames = Vector(Http2Wire.headers(streamId, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            // CaseContext retains the socket until the operation completes; no implicit EOF.
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.5
    WireCase(
      "h2.server.reject-connect-scheme",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.5",
        "CONNECT requires host and port in :authority and forbids :scheme and :path.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = Vector(
              ":method" -> "CONNECT",
              ":authority" -> "wire.test:443",
              ":scheme" -> "http",
            )
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.5
    WireCase(
      "h2.server.reject-connect-path",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.5",
        "CONNECT requires host and port in :authority and forbids :scheme and :path.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = Vector(":method" -> "CONNECT", ":authority" -> "wire.test:443", ":path" -> "/")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.5
    WireCase(
      "h2.server.reject-connect-missing-authority",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.5",
        "CONNECT requires host and port in :authority and forbids :scheme and :path.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = Vector(":method" -> "CONNECT")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.5
    WireCase(
      "h2.server.reject-connect-missing-port",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.5",
        "CONNECT requires host and port in :authority and forbids :scheme and :path.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            fields = Vector(":method" -> "CONNECT", ":authority" -> "wire.test")
            // Only the documented field or frame property differs from a complete valid request.
            frames = Vector(Http2Wire.headers(1, fields))
            _ <- Http2PeerSettings.writeFrames(peer, frames)
            _ <- expectServerRejection(peer, 1, connection = false, code = 1L)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4
    WireCase(
      "h2.server.reject-client-push-promise",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.4",
        "A client cannot push; servers must treat PUSH_PROMISE as a connection error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            _ <- clientHandshakeWithoutPush(peer)
            // Stream 1 is open and stream 3 is a fresh client-owned ID: the invalid property
            // is that the client sent PUSH_PROMISE at all, not an idle associated stream.
            _ <- Http2Wire.writeFrame(peer, Http2Wire.headers(1, RequestFields, endStream = false))
            _ <- Http2Wire.writeFrame(
              peer,
              Http2Wire.Frame(
                Http2Wire.PushPromise,
                Http2Wire.EndHeaders,
                1,
                Http2Wire.uint32(3L) ++ Http2Wire.literalFields(RequestFields),
              ),
            )
            // Complete the legitimate request even if the target illegally ignores the push.
            _ <- Http2Wire.writeFrame(peer, Http2Wire.data(1, ByteVector.empty))
            _ <- readRejection(
              peer,
              1,
              connection = true,
              code = 1L,
              fromServer = true,
              allowMessageFrames = true,
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1
    WireCase(
      "h2.client.request-pseudo-header-mapping",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.3.1",
        "A generated request emits its method, scheme, authority, and path as ordered pseudo-header fields.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            _ <- Check.equal(
              request.fields.collect { case (":method", value) => value },
              Vector("GET"),
            )
            _ <- Check.equal(
              request.fields.collect { case (":scheme", value) => value },
              Vector("http"),
            )
            _ <- Check.equal(
              request.fields.collect { case (":path", value) => value },
              Vector("/mapping"),
            )
            authority = request.fields.collect { case (":authority", value) => value }
            _ <- Check(
              authority.size == 1 && authority.head.nonEmpty,
              "a direct request must convey its authority",
            )
            _ <- checkGeneratedFields(
              request.fields,
              Set(":method", ":scheme", ":authority", ":path"),
            )
            _ <- Http2Wire.writeFrame(
              peer,
              Http2Wire.headers(request.streamId, Vector(":status" -> "200")),
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2
    WireCase(
      "h2.client.request-lowercase-field-names",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.2",
        "An endpoint converts regular field names to lowercase when constructing an HTTP/2 message.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(
              EmptyRequest.copy(headers = Vector("Content-Length" -> "0", "X-MiXeD" -> "value"))
            )
            .use(_.body.compile.drain)
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            _ <- Check(
              request.fields.contains("x-mixed" -> "value"),
              "the field name was not emitted in lowercase",
            )
            _ <- checkGeneratedFields(
              request.fields,
              Set(":method", ":scheme", ":authority", ":path"),
            )
            _ <- Http2Wire.writeFrame(
              peer,
              Http2Wire.headers(request.streamId, Vector(":status" -> "200")),
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2
    WireCase(
      "h2.server.response-lowercase-field-names",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.2",
        "An endpoint converts regular field names to lowercase when constructing an HTTP/2 message.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context
          .server(Protocol.Http2, ServerScenario.Reply(headers = Vector("X-MiXeD" -> "value")))
          .use { case (peer, _) =>
            for {
              _ <- clientHandshakeWithoutPush(peer)
              _ <- Http2Wire.writeFrame(peer, Http2Wire.headers(1, RequestFields))
              response <- readResponse(peer, 1)
              _ <- Check.equal(response.status, 200)
              _ <- Check(
                response.fields.contains("x-mixed" -> "value"),
                "the field name was not emitted in lowercase",
              )
              _ <- checkGeneratedFields(response.fields, Set(":status"))
            } yield ()
          }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.6
    WireCase(
      "h2.client.reject-push-stream-zero",
      Protocol.Http2,
      Role.Client,
      Rfc(9113, "6.6", "PUSH_PROMISE must be associated with a nonzero stream.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejected =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest)) { response =>
              response.body.compile.drain *> Check.equal(response.status, 200)
            } { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *> (result match {
                case Left(error)
                    if error.isInstanceOf[ProtocolFailure] || error
                      .isInstanceOf[WireDecodingFailure] || error
                      .isInstanceOf[WireObservationLimit] =>
                  IO.raiseError(error)
                // A stream error may be escalated to connection termination. Keep the client
                // resource alive after either outcome until the peer observes that rejection.
                case _ => rejected.get
              })
            }
          ) { peer =>
            for {
              request <- readClientRequest(peer)
              authority = request.fields
                .collectFirst { case (":authority", value) => value }
                .getOrElse("")
              _ <- Check(authority.nonEmpty, "the client omitted request authority")
              promisedFields = Vector(
                ":method" -> "GET",
                ":scheme" -> "http",
                ":authority" -> authority,
                ":path" -> "/pushed",
              )
              fields = promisedFields
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.Frame(
                  Http2Wire.PushPromise,
                  Http2Wire.EndHeaders,
                  0,
                  Http2Wire.uint32(2L) ++ Http2Wire.literalFields(fields),
                ),
              )
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.headers(request.streamId, Vector(":status" -> "200")),
              )
              _ <- readRejection(
                peer,
                2L.toInt,
                connection = true,
                code = 1L,
                fromServer = false,
                allowMessageFrames = true,
                originalRequestStream = Some(request.streamId),
                incidentalPromisedStreams = Set(2),
              )
              _ <- rejected.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.6
    WireCase(
      "h2.client.reject-push-odd-promised-stream",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.6",
        "A promised server-initiated stream must use a fresh even stream identifier.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejected =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest)) { response =>
              response.body.compile.drain *> Check.equal(response.status, 200)
            } { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *> (result match {
                case Left(error)
                    if error.isInstanceOf[ProtocolFailure] || error
                      .isInstanceOf[WireDecodingFailure] || error
                      .isInstanceOf[WireObservationLimit] =>
                  IO.raiseError(error)
                // A stream error may be escalated to connection termination. Keep the client
                // resource alive after either outcome until the peer observes that rejection.
                case _ => rejected.get
              })
            }
          ) { peer =>
            for {
              request <- readClientRequest(peer)
              authority = request.fields
                .collectFirst { case (":authority", value) => value }
                .getOrElse("")
              _ <- Check(authority.nonEmpty, "the client omitted request authority")
              promisedFields = Vector(
                ":method" -> "GET",
                ":scheme" -> "http",
                ":authority" -> authority,
                ":path" -> "/pushed",
              )
              fields = promisedFields
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.Frame(
                  Http2Wire.PushPromise,
                  Http2Wire.EndHeaders,
                  request.streamId,
                  Http2Wire.uint32(3L) ++ Http2Wire.literalFields(fields),
                ),
              )
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.headers(request.streamId, Vector(":status" -> "200")),
              )
              _ <- readRejection(
                peer,
                3L.toInt,
                connection = true,
                code = 1L,
                fromServer = false,
                allowMessageFrames = true,
                originalRequestStream = Some(request.streamId),
              )
              _ <- rejected.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4
    WireCase(
      "h2.client.reject-push-unsafe-method",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.4",
        "An unsafe promised request requires rejection of the promised stream; acknowledged push disablement instead requires a connection error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejected =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest)) { response =>
              response.body.compile.drain *> Check.equal(response.status, 200)
            } { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *> (result match {
                case Left(error)
                    if error.isInstanceOf[ProtocolFailure] || error
                      .isInstanceOf[WireDecodingFailure] || error
                      .isInstanceOf[WireObservationLimit] =>
                  IO.raiseError(error)
                // A stream error may be escalated to connection termination. Keep the client
                // resource alive after either outcome until the peer observes that rejection.
                case _ => rejected.get
              })
            }
          ) { peer =>
            for {
              request <- readClientRequest(peer)
              authority = request.fields
                .collectFirst { case (":authority", value) => value }
                .getOrElse("")
              _ <- Check(authority.nonEmpty, "the client omitted request authority")
              promisedFields = Vector(
                ":method" -> "GET",
                ":scheme" -> "http",
                ":authority" -> authority,
                ":path" -> "/pushed",
              )
              fields = promisedFields.map { case (name, value) =>
                name -> (if (name == ":method") "POST" else value)
              }
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.Frame(
                  Http2Wire.PushPromise,
                  Http2Wire.EndHeaders,
                  request.streamId,
                  Http2Wire.uint32(2L) ++ Http2Wire.literalFields(fields),
                ),
              )
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.headers(request.streamId, Vector(":status" -> "200")),
              )
              _ <- readRejection(
                peer,
                2L.toInt,
                connection = request.pushDisabled,
                code = 1L,
                fromServer = false,
                allowMessageFrames = true,
                originalRequestStream = Some(request.streamId),
                incidentalPromisedStreams = Set(2),
              )
              _ <- rejected.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4
    WireCase(
      "h2.client.reject-push-request-content",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.4",
        "A promise indicating request content must be rejected; acknowledged push disablement instead requires a connection error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejected =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest)) { response =>
              response.body.compile.drain *> Check.equal(response.status, 200)
            } { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *> (result match {
                case Left(error)
                    if error.isInstanceOf[ProtocolFailure] || error
                      .isInstanceOf[WireDecodingFailure] || error
                      .isInstanceOf[WireObservationLimit] =>
                  IO.raiseError(error)
                // A stream error may be escalated to connection termination. Keep the client
                // resource alive after either outcome until the peer observes that rejection.
                case _ => rejected.get
              })
            }
          ) { peer =>
            for {
              request <- readClientRequest(peer)
              authority = request.fields
                .collectFirst { case (":authority", value) => value }
                .getOrElse("")
              _ <- Check(authority.nonEmpty, "the client omitted request authority")
              promisedFields = Vector(
                ":method" -> "GET",
                ":scheme" -> "http",
                ":authority" -> authority,
                ":path" -> "/pushed",
              )
              fields = promisedFields ++ Vector("content-length" -> "1")
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.Frame(
                  Http2Wire.PushPromise,
                  Http2Wire.EndHeaders,
                  request.streamId,
                  Http2Wire.uint32(2L) ++ Http2Wire.literalFields(fields),
                ),
              )
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.headers(request.streamId, Vector(":status" -> "200")),
              )
              _ <- readRejection(
                peer,
                2L.toInt,
                connection = request.pushDisabled,
                code = 1L,
                fromServer = false,
                allowMessageFrames = true,
                originalRequestStream = Some(request.streamId),
                incidentalPromisedStreams = Set(2),
              )
              _ <- rejected.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4.1
    WireCase(
      "h2.client.reject-push-missing-method",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.4.1",
        "A promise must contain a complete request field section; acknowledged push disablement instead requires a connection error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejected =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest)) { response =>
              response.body.compile.drain *> Check.equal(response.status, 200)
            } { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *> (result match {
                case Left(error)
                    if error.isInstanceOf[ProtocolFailure] || error
                      .isInstanceOf[WireDecodingFailure] || error
                      .isInstanceOf[WireObservationLimit] =>
                  IO.raiseError(error)
                // A stream error may be escalated to connection termination. Keep the client
                // resource alive after either outcome until the peer observes that rejection.
                case _ => rejected.get
              })
            }
          ) { peer =>
            for {
              request <- readClientRequest(peer)
              authority = request.fields
                .collectFirst { case (":authority", value) => value }
                .getOrElse("")
              _ <- Check(authority.nonEmpty, "the client omitted request authority")
              promisedFields = Vector(
                ":method" -> "GET",
                ":scheme" -> "http",
                ":authority" -> authority,
                ":path" -> "/pushed",
              )
              fields = promisedFields.filterNot(_._1 == ":method")
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.Frame(
                  Http2Wire.PushPromise,
                  Http2Wire.EndHeaders,
                  request.streamId,
                  Http2Wire.uint32(2L) ++ Http2Wire.literalFields(fields),
                ),
              )
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.headers(request.streamId, Vector(":status" -> "200")),
              )
              _ <- readRejection(
                peer,
                2L.toInt,
                connection = request.pushDisabled,
                code = 1L,
                fromServer = false,
                allowMessageFrames = true,
                originalRequestStream = Some(request.streamId),
                incidentalPromisedStreams = Set(2),
              )
              _ <- rejected.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4.1
    WireCase(
      "h2.client.reject-push-invalid-field",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.4.1",
        "A promise must contain a valid request field section; acknowledged push disablement instead requires a connection error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejected =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest)) { response =>
              response.body.compile.drain *> Check.equal(response.status, 200)
            } { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *> (result match {
                case Left(error)
                    if error.isInstanceOf[ProtocolFailure] || error
                      .isInstanceOf[WireDecodingFailure] || error
                      .isInstanceOf[WireObservationLimit] =>
                  IO.raiseError(error)
                // A stream error may be escalated to connection termination. Keep the client
                // resource alive after either outcome until the peer observes that rejection.
                case _ => rejected.get
              })
            }
          ) { peer =>
            for {
              request <- readClientRequest(peer)
              authority = request.fields
                .collectFirst { case (":authority", value) => value }
                .getOrElse("")
              _ <- Check(authority.nonEmpty, "the client omitted request authority")
              promisedFields = Vector(
                ":method" -> "GET",
                ":scheme" -> "http",
                ":authority" -> authority,
                ":path" -> "/pushed",
              )
              fields = promisedFields ++ Vector("X-Push" -> "value")
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.Frame(
                  Http2Wire.PushPromise,
                  Http2Wire.EndHeaders,
                  request.streamId,
                  Http2Wire.uint32(2L) ++ Http2Wire.literalFields(fields),
                ),
              )
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.headers(request.streamId, Vector(":status" -> "200")),
              )
              _ <- readRejection(
                peer,
                2L.toInt,
                connection = request.pushDisabled,
                code = 1L,
                fromServer = false,
                allowMessageFrames = true,
                originalRequestStream = Some(request.streamId),
                incidentalPromisedStreams = Set(2),
              )
              _ <- rejected.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4
    WireCase(
      "h2.client.reject-push-unauthoritative-origin",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.4",
        "A promise for an origin for which the peer is not authoritative must be rejected; acknowledged push disablement instead requires a connection error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejected =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest)) { response =>
              response.body.compile.drain *> Check.equal(response.status, 200)
            } { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *> (result match {
                case Left(error)
                    if error.isInstanceOf[ProtocolFailure] || error
                      .isInstanceOf[WireDecodingFailure] || error
                      .isInstanceOf[WireObservationLimit] =>
                  IO.raiseError(error)
                // A stream error may be escalated to connection termination. Keep the client
                // resource alive after either outcome until the peer observes that rejection.
                case _ => rejected.get
              })
            }
          ) { peer =>
            for {
              request <- readClientRequest(peer)
              authority = request.fields
                .collectFirst { case (":authority", value) => value }
                .getOrElse("")
              _ <- Check(authority.nonEmpty, "the client omitted request authority")
              promisedFields = Vector(
                ":method" -> "GET",
                ":scheme" -> "http",
                ":authority" -> authority,
                ":path" -> "/pushed",
              )
              fields = promisedFields.map { case (name, value) =>
                name -> (if (name == ":authority") "unrelated.invalid:81" else value)
              }
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.Frame(
                  Http2Wire.PushPromise,
                  Http2Wire.EndHeaders,
                  request.streamId,
                  Http2Wire.uint32(2L) ++ Http2Wire.literalFields(fields),
                ),
              )
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.headers(request.streamId, Vector(":status" -> "200")),
              )
              _ <- readRejection(
                peer,
                2L.toInt,
                connection = request.pushDisabled,
                code = 1L,
                fromServer = false,
                allowMessageFrames = true,
                originalRequestStream = Some(request.streamId),
                incidentalPromisedStreams = Set(2),
              )
              _ <- rejected.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.informational-then-final",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1",
        "A server may send interim field sections before the final response; this valid sequence must not cause a parsing failure.",
        "MAY",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          // The frozen driver exposes one ResponseData. This case asserts non-rejection;
          // it deliberately does not require a particular interim-response exposure API.
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            frames = Vector(
              Http2Wire.headers(streamId, Vector(":status" -> "100"), endStream = false),
              Http2Wire.headers(streamId, Vector(":status" -> "200")),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.multiple-informational-then-final",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1",
        "A server may send interim field sections before the final response; this valid sequence must not cause a parsing failure.",
        "MAY",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        context.client(Protocol.Http2)(driver =>
          // The frozen driver exposes one ResponseData. This case asserts non-rejection;
          // it deliberately does not require a particular interim-response exposure API.
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .handleErrorWith(unexpectedTargetRejection)
        ) { peer =>
          for {
            request <- readClientRequest(peer)
            streamId = request.streamId
            frames = Vector(
              Http2Wire
                .headers(streamId, Vector(":status" -> "103", "link" -> "</a>"), endStream = false),
              Http2Wire.headers(streamId, Vector(":status" -> "100"), endStream = false),
              Http2Wire.headers(streamId, Vector(":status" -> "200")),
            )
            _ <- Http2PeerSettings.writeFrames(peer, frames)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2
    WireCase(
      "h2.client.headers-nonzero-padding-choice",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "6.2",
        "A receiver may ignore nonzero HEADERS padding or terminate the connection with PROTOCOL_ERROR.",
        "MAY",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Boolean].flatMap { accepted =>
          clientUntilObserved(context) { (driver, observed) =>
            ClientObservation(
              driver
                .exchange(EmptyRequest)
            ) { response =>
              response.body.compile.to(Chunk).flatMap { body =>
                Check.equal(response.status, 200) *> Check
                  .equal(body.toByteVector, ByteVector.empty)
              }
            } { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *> (result match {
                case Left(error)
                    if error.isInstanceOf[ProtocolFailure] || error
                      .isInstanceOf[WireDecodingFailure] || error
                      .isInstanceOf[WireObservationLimit] =>
                  IO.raiseError(error)
                case Left(_) => accepted.complete(false).void
                case Right(_) => accepted.complete(true).void
              }) *> observed
            }
          } { peer =>
            for {
              request <- readClientRequest(peer)
              fields = Vector(":status" -> "200")
              _ <- Http2Wire.writeFrame(
                peer,
                Http2Wire.Frame(
                  Http2Wire.Headers,
                  Http2Wire.EndHeaders | Http2Wire.EndStream | Http2Wire.Padded,
                  request.streamId,
                  ByteVector(1) ++ Http2Wire.literalFields(fields) ++ ByteVector(127),
                ),
              )
              consumed <- accepted.get
              _ <-
                if (consumed) IO.unit
                else
                  readRejection(
                    peer,
                    request.streamId,
                    connection = true,
                    code = 1L,
                    fromServer = false,
                    allowMessageFrames = false,
                  )
            } yield ()
          }
        }
      }
    },
  )

  private final case class CapturedRequest(
      streamId: Int,
      fields: Vector[(String, String)],
      body: ByteVector,
      pushDisabled: Boolean,
  )

  private final case class CapturedResponse(
      status: Int,
      fields: Vector[(String, String)],
      body: ByteVector,
      trailers: Vector[(String, String)],
  )

  /** No stream is left half-read before a response or deliberately malformed frame is sent. */
  private def readClientRequest(
      peer: RawPeer[IO]
  )(implicit
      peerSettings: Http2PeerSettings[IO],
      dataBudget: Http2Wire.DataBudget,
  ): IO[CapturedRequest] =
    for {
      preface <- WireDecodingFailure.read(peer.readExact(Http2Wire.clientPreface.size.toInt))
      _ <- Check.equal(preface, Http2Wire.clientPreface)
      initial <- Http2Wire.readFrame(peer)
      _ <- Check(
        initial.frameType == Http2Wire.Settings && !initial.hasFlag(Http2Wire.Ack),
        "missing initial client SETTINGS",
      )
      pushDisabled <- Ref.of[IO, Boolean](false)
      _ <- receiveSettings(initial, pushDisabled)
      _ <- Http2PeerSettings.write(
        peer,
        Http2Wire.encode(Http2Wire.settings()) ++ Http2Wire.encode(Http2Wire.settings(ack = true)),
      )
      first <- readClientFrame(peer, pushDisabled)
      _ <- Check.equal(first.frameType, Http2Wire.Headers)
      _ <- Check(
        first.streamId > 0 && (first.streamId & 1) == 1,
        "invalid client stream identifier",
      )
      decoder <- HpackWire.decoder[IO]()
      fields <- Http2Wire.readHeaders(peer, first, decoder)
      body <- readContent(
        peer,
        first.streamId,
        first.hasFlag(Http2Wire.EndStream),
        decoder,
        Some(readClientFrame(peer, pushDisabled)),
      )
      disabled <- pushDisabled.get
    } yield CapturedRequest(first.streamId, fields, body._1, disabled)

  private def receiveSettings(frame: Http2Wire.Frame, pushDisabled: Ref[IO, Boolean])(implicit
      dataBudget: Http2Wire.DataBudget
  ): IO[Unit] =
    IO.fromEither(Http2RejectionChecks.validate(frame, fromServer = false)) *>
      Check(frame.streamId == 0 && frame.payload.size % 6L == 0L, "invalid client SETTINGS") *>
      Check(
        !frame.hasFlag(Http2Wire.Ack) || frame.payload.isEmpty,
        "SETTINGS acknowledgment contained a payload",
      ) *>
      (0 until (frame.payload.size / 6L).toInt).toVector.traverse_ { index =>
        val entry = frame.payload.slice(index.toLong * 6L, index.toLong * 6L + 6L)
        val key = ((entry(0) & 0xff) << 8) | (entry(1) & 0xff)
        val value = Http2Wire.readUint32(entry.drop(2))
        if (key == 2)
          Check(value <= 1L, "invalid SETTINGS_ENABLE_PUSH") *> pushDisabled.set(value == 0L)
        else IO.unit
      } *> IO(dataBudget.check(frame))

  /** Observe every SETTINGS update instead of silently losing a push-disablement transition. */
  private def readClientFrame(
      peer: RawPeer[IO],
      pushDisabled: Ref[IO, Boolean],
  )(implicit
      peerSettings: Http2PeerSettings[IO],
      dataBudget: Http2Wire.DataBudget,
  ): IO[Http2Wire.Frame] = peerSettings.positive(peer).flatMap { positive =>
    Http2FrameReader.readUntil(
      Http2Wire.readFrame(peer),
      1024,
      "too many control frames before the request",
    )(
      frame => positive.observe(frame, fromServer = false),
      frame =>
        !Set(
          Http2Wire.Settings,
          Http2Wire.Ping,
          Http2Wire.GoAway,
          Http2Wire.WindowUpdate,
          Http2Wire.Priority,
        )(frame.frameType) &&
          frame.frameType <= Http2Wire.Continuation,
      frame =>
        frame.frameType match {
          case Http2Wire.Settings =>
            receiveSettings(frame, pushDisabled) *>
              (if (frame.hasFlag(Http2Wire.Ack)) IO.unit
               else
                 positive
                   .incidental(Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true)))
                   .void)
          case Http2Wire.Ping =>
            Check(frame.streamId == 0 && frame.payload.size == 8L, "invalid client PING") *>
              (if (frame.hasFlag(Http2Wire.Ack)) IO.unit
               else
                 positive
                   .incidental(Http2Wire.writeFrame(peer, frame.copy(flags = Http2Wire.Ack)))
                   .void)
          case Http2Wire.GoAway | Http2Wire.WindowUpdate | Http2Wire.Priority =>
            IO.unit
          case kind if kind > Http2Wire.Continuation => IO.unit
          case _ => IO.unit
        },
    )
  }

  // RFC 9113 sections 5, 6.5.2 and 6.5.3: these reference-client readers do not
  // support server push. Disable it before opening any request stream. Received
  // frame ordering and immediate settings application preclude an in-flight
  // lawful promise at this point, so no additional ACK wait is necessary.
  private def clientHandshakeWithoutPush(peer: RawPeer[IO])(implicit
      peerSettings: Http2PeerSettings[IO],
      dataBudget: Http2Wire.DataBudget,
  ): IO[Unit] =
    Http2Wire.clientHandshake(peer) *>
      Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(2 -> 0L)))

  private def readResponse(peer: RawPeer[IO], streamId: Int)(implicit
      peerSettings: Http2PeerSettings[IO]
  ): IO[CapturedResponse] = {
    // Every caller has already delivered all local DATA before observing its response.
    implicit val dataBudget: Http2Wire.DataBudget = Http2Wire.DataBudget.empty
    def response(decoder: HpackWire.Decoder[IO]): IO[CapturedResponse] =
      for {
        first <- Http2PositiveObservation.readUntil(peer, fromServer = true)(frame =>
          frame.frameType == Http2Wire.Headers || frame.frameType == Http2Wire.RstStream ||
            frame.frameType == Http2Wire.GoAway
        )
        _ <- Check.equal(first.frameType, Http2Wire.Headers)
        _ <- Check.equal(first.streamId, streamId)
        fields <- Http2Wire.readHeaders(peer, first, decoder).handleErrorWith(invalidTargetFrame)
        statuses = fields.collect { case (":status", value) => value }
        _ <- Check(
          statuses.size == 1 && statuses.head.matches("[1-5][0-9][0-9]"),
          "invalid response :status",
        )
        status = statuses.head.toInt
        // RFC 9113 section 8.6 excludes Switching Protocols from HTTP/2.
        _ <- Check(status != 101, "HTTP/2 forbids status 101")
        result <-
          if (status < 200)
            Check(!first.hasFlag(Http2Wire.EndStream), "informational response ended the stream") *>
              response(decoder)
          else
            readContent(peer, streamId, first.hasFlag(Http2Wire.EndStream), decoder)
              .map { case (body, trailers) => CapturedResponse(status, fields, body, trailers) }
      } yield result
    peerSettings.positive(peer).flatMap { positive =>
      positive.expecting(Set(streamId)) *>
        HpackWire
          .decoder[IO]()
          .flatMap(response)
          .flatTap(_ => positive.completed(Set(streamId)))
          .handleErrorWith(unexpectedEof)
    }
  }

  private def readContent(
      peer: RawPeer[IO],
      streamId: Int,
      ended: Boolean,
      decoder: HpackWire.Decoder[IO],
      requestFrame: Option[IO[Http2Wire.Frame]] = None,
  )(implicit
      peerSettings: Http2PeerSettings[IO],
      dataBudget: Http2Wire.DataBudget,
  ): IO[(ByteVector, Vector[(String, String)])] = {
    val policy = Http2ContentBudget.Policy(
      1024,
      Http2ObservationBudget.MaxBytes,
      "too many content frames",
      s"HTTP/2 observation byte budget exceeded (${Http2ObservationBudget.MaxBytes} bytes)",
    )
    def loop(
        body: ByteVector,
        budget: Http2ContentBudget.State,
    ): IO[(ByteVector, Vector[(String, String)])] =
      IO.fromEither(budget.beforeRead) *>
        requestFrame
          .getOrElse(
            Http2PositiveObservation.readUntil(peer, fromServer = true)(frame =>
              frame.frameType == Http2Wire.Data || frame.frameType == Http2Wire.Headers ||
                frame.frameType == Http2Wire.RstStream || frame.frameType == Http2Wire.GoAway
            )
          )
          .flatMap { frame =>
            IO.fromEither(budget.reserve(frame.payload.size + 9L)).flatMap { reserved =>
              Check.equal(frame.streamId, streamId) *> (frame.frameType match {
                case Http2Wire.Data =>
                  IO(Http2Wire.unpad(frame)).handleErrorWith(invalidTargetFrame).flatMap { bytes =>
                    val next = body ++ bytes
                    if (frame.hasFlag(Http2Wire.EndStream)) IO.pure((next, Vector.empty))
                    else loop(next, reserved)
                  }
                case Http2Wire.Headers =>
                  Check(
                    frame.hasFlag(Http2Wire.EndStream),
                    "trailers must terminate the stream",
                  ) *>
                    Http2Wire
                      .readHeaders(peer, frame, decoder)
                      .handleErrorWith(invalidTargetFrame)
                      .flatMap { trailers =>
                        Check(
                          !trailers.exists(_._1.startsWith(":")),
                          "trailers contained a pseudo-header",
                        )
                          .as((body, trailers))
                      }
                case _ =>
                  IO.raiseError(new ProtocolFailure(s"valid message rejected by frame $frame"))
              })
            }
          }
    if (ended) IO.pure((ByteVector.empty, Vector.empty)) else loop(ByteVector.empty, policy.initial)
  }

  /** Origin observations may be progressive; rejection is established independently on the wire. */
  private def expectServerRejection(
      peer: RawPeer[IO],
      streamId: Int,
      connection: Boolean,
      code: Long,
  )(implicit peerSettings: Http2PeerSettings[IO]): IO[Unit] =
    readRejection(peer, streamId, connection, code, fromServer = true, allowMessageFrames = true)

  /** A timeout is never evidence of rejection. RST_STREAM, or connection closure, is required.
    * Section 5.4 permits generic error codes and escalation of stream errors to connections.
    */
  private def readRejection(
      peer: RawPeer[IO],
      streamId: Int,
      connection: Boolean,
      code: Long,
      fromServer: Boolean,
      allowMessageFrames: Boolean,
      beforeFrame: IO[Unit] = IO.unit,
      incidentalPromisedStreams: Set[Int] = Set.empty,
      originalRequestStream: Option[Int] = None,
  )(implicit peerSettings: Http2PeerSettings[IO]): IO[Unit] =
    peerSettings.positive(peer).flatMap { observation =>
      val codes = Set(1L, 2L, code)
      sealed trait ResetDisposition
      case object OriginalRequestReset extends ResetDisposition
      case object RequireConnectionClosure extends ResetDisposition
      case object FaultStreamRejected extends ResetDisposition

      def checkReset(
          frame: Http2Wire.Frame,
          connectionRequired: Boolean,
      ): IO[ResetDisposition] =
        // checked validates the envelope first. A reset of the known original request
        // is incidental, and cannot change how rejection of the promised stream is proved.
        if (originalRequestStream.contains(frame.streamId)) IO.pure(OriginalRequestReset)
        else
          Check.equal(frame.streamId, streamId) *> IO.defer {
            if (
              incidentalPromisedStreams(frame.streamId) &&
              (connectionRequired || !codes.contains(Http2Wire.readUint32(frame.payload)))
            )
              // Refusal is permitted for any reason (8.4.2); subsequent resets cannot
              // independently establish rejection after the first reset (5.4).
              IO.pure(RequireConnectionClosure)
            else
              Check(
                codes.contains(Http2Wire.readUint32(frame.payload)),
                "incorrect RST_STREAM error code",
              ).as(if (connectionRequired) RequireConnectionClosure else FaultStreamRejected)
          }
      def checkFrameKind(frame: Http2Wire.Frame): IO[Unit] = frame.frameType match {
        // Progressive origin processing does not replace wire rejection. The same
        // caller policy applies to live frames and frames captured after GOAWAY.
        case Http2Wire.Headers | Http2Wire.Continuation | Http2Wire.Data =>
          Check(
            allowMessageFrames,
            "the client sent unexpected message frames after its complete request",
          )
        case Http2Wire.Settings | Http2Wire.Ping | Http2Wire.RstStream | Http2Wire.GoAway |
            Http2Wire.WindowUpdate | Http2Wire.Priority =>
          IO.unit
        case kind if kind > Http2Wire.Continuation => IO.unit
        case _ => Check(false, s"unexpected frame while awaiting rejection: $frame")
      }
      def checked(
          frame: Http2Wire.Frame
      ): IO[Http2RejectionChecks.State] =
        observation
          .observeControl(frame, fromServer)
          .flatTap(_ => checkFrameKind(frame))
          .flatTap { _ =>
            if (frame.frameType != Http2Wire.GoAway) IO.unit
            else {
              val observed = Http2Wire.readUint32(frame.payload.slice(4, 8))
              Check(observed == 0L || codes.contains(observed), "incorrect GOAWAY error code")
            }
          }
      def loop(
          remaining: Int,
          connectionRequired: Boolean,
          closedWrite: Boolean,
      ): IO[Unit] =
        observationBound(remaining > 0, "too many frames without required protocol rejection") *>
          Http2Wire.readFrameOrClose(peer).flatMap {
            case None => beforeFrame
            case Some(frame) =>
              beforeFrame *> checked(frame).flatMap { _ =>
                frame.frameType match {
                  case Http2Wire.RstStream =>
                    checkReset(frame, connectionRequired).flatMap {
                      case OriginalRequestReset =>
                        loop(remaining - 1, connectionRequired, closedWrite)
                      case RequireConnectionClosure =>
                        loop(remaining - 1, connectionRequired = true, closedWrite)
                      case FaultStreamRejected => IO.unit
                    }
                  case Http2Wire.GoAway if Http2Wire.readUint32(frame.payload.slice(4, 8)) == 0L =>
                    loop(remaining - 1, connectionRequired, closedWrite)
                  case Http2Wire.GoAway =>
                    // Error GOAWAY still requires actual closure (5.4.1), with every captured
                    // tail frame checked under the same kind, role, sequence, scope and code policy.
                    for {
                      rest <- peer.readToEnd(65536, allowReset = true)
                      frames <- IO.fromEither(Http2Wire.decodeAll(rest))
                      _ <- frames.traverse_ { observed =>
                        beforeFrame *> checked(observed).flatTap { _ =>
                          if (observed.frameType == Http2Wire.RstStream)
                            checkReset(observed, connectionRequired = true).void
                          else IO.unit
                        }
                      }
                      _ <- beforeFrame
                    } yield ()
                  case Http2Wire.Settings =>
                    (if (closedWrite || frame.hasFlag(Http2Wire.Ack)) IO.pure(!closedWrite)
                     else
                       Http2RejectionWrite.reply(
                         Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))
                       ))
                      .flatMap(written =>
                        loop(
                          remaining - 1,
                          connectionRequired || !written,
                          closedWrite || !written,
                        )
                      )
                  case Http2Wire.Ping =>
                    (if (closedWrite || frame.hasFlag(Http2Wire.Ack)) IO.pure(!closedWrite)
                     else
                       Http2RejectionWrite.reply(
                         Http2Wire.writeFrame(peer, frame.copy(flags = Http2Wire.Ack))
                       ))
                      .flatMap(written =>
                        loop(
                          remaining - 1,
                          connectionRequired || !written,
                          closedWrite || !written,
                        )
                      )
                  // Frame acceptance is shared with the captured tail; replies remain live-only.
                  case _ => loop(remaining - 1, connectionRequired, closedWrite)
                }
              }
          }
      loop(1024, connection, closedWrite = false)
    }

  // RFC 9110 4.2.3 / RFC 3986 6.2.2.1: normalize only escape hex spelling.
  // Encoded reserved delimiters, path case and query content/order remain significant.
  private def normalizeEscapeCase(value: String): String =
    "%[0-9a-fA-F]{2}".r.replaceAllIn(value, matched => matched.matched.map(_.toUpper))

  private def checkGeneratedFields(
      fields: Vector[(String, String)],
      allowed: Set[String],
  ): IO[Unit] = {
    val pseudo = fields.takeWhile(_._1.startsWith(":"))
    Check(
      !fields.drop(pseudo.size).exists(_._1.startsWith(":")),
      "pseudo-header after regular field",
    ) *>
      Check(
        pseudo.forall(field => allowed.contains(field._1)),
        "invalid generated pseudo-header",
      ) *>
      Check(pseudo.map(_._1).distinct.size == pseudo.size, "duplicate generated pseudo-header") *>
      Check(
        fields.forall { case (name, _) => name.forall(c => c < 'A' || c > 'Z') },
        "uppercase generated field name",
      )
  }

  private def clientUntilObserved(context: CaseContext)(
      exercise: (ClientDriver[IO], IO[Unit]) => IO[Unit]
  )(serve: RawPeer[IO] => IO[Unit]): IO[Unit] =
    Deferred[IO, Unit].flatMap { observed =>
      context.client(Protocol.Http2)(driver => exercise(driver, observed.get))(peer =>
        serve(peer) *> observed.complete(()).void
      )
    }

  private def expectTargetRejection(result: Either[Throwable, Unit]): IO[Unit] =
    result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *> (result match {
      case Left(error: ProtocolFailure) => IO.raiseError(error)
      case Left(error: WireDecodingFailure) => IO.raiseError(error)
      case Left(error: WireObservationLimit) => IO.raiseError(error)
      case Left(error) => RawPeer.raiseAdapterOrTimeout[IO](error)
      case Right(_) => Check(false, "the client consumed a deliberately malformed response")
    })

  private def unexpectedTargetRejection(error: Throwable): IO[Unit] =
    RawPeer.raiseAdapterOrTimeout[IO](error) *> {
      if (
        error.isInstanceOf[ProtocolFailure] || error
          .isInstanceOf[WireDecodingFailure] || error
          .isInstanceOf[WireObservationLimit]
      ) IO.raiseError(error)
      else Check(false, s"the target rejected valid HTTP/2 input: $error")
    }

  private def invalidTargetFrame[A](error: Throwable): IO[A] =
    RawPeer.raiseAdapterOrTimeout[IO](error) *> {
      if (
        error.isInstanceOf[ProtocolFailure] || error
          .isInstanceOf[WireDecodingFailure] || error
          .isInstanceOf[WireObservationLimit]
      ) IO.raiseError(error)
      else IO.raiseError(new ProtocolFailure(s"the target emitted an invalid HTTP/2 frame: $error"))
    }

  private def unexpectedEof[A](error: Throwable): IO[A] =
    RawPeer.raiseAdapterOrTimeout[IO](error) *> {
      if (isEof(error))
        IO.raiseError(
          new ProtocolFailure("the target closed while a valid response was incomplete")
        )
      else IO.raiseError(error)
    }

  private def isEof(error: Throwable): Boolean = error match {
    case _: WireReadFailure => true
    case _ => false
  }

  private def observationBound(condition: Boolean, detail: String): IO[Unit] =
    if (condition) IO.unit else IO.raiseError(new WireObservationLimit(detail))

}
