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

import cats.ApplicativeThrow
import cats.effect.Async
import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all._
import fs2.Chunk
import scodec.bits.ByteVector

/** RFC 7541 target-codec conformance through independent HTTP/2 socket peers. */
object HpackCases {
  // Static GET/http/path plus a non-indexed :authority; no hidden dynamic insertion.
  private val RequestPrefix = hex("8286840109776972652e74657374")
  private val ResponsePrefix = hex("88")
  private val EmptyRequest = RequestData(headers = Vector("Content-Length" -> "0"))
  private val boundaryEntries = Vector.tabulate(66)(index => s"x-$index" -> "v")

  val all: Vector[WireCase] = Vector(
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.client.hpack-literal-without-new-name",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "6.2.2",
        "A literal without indexing appends its new name and value without changing the dynamic table.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "wire"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("0001780477697265")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.server.hpack-literal-without-new-name",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "6.2.2",
        "A literal without indexing appends its new name and value without changing the dynamic table.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("0001780477697265")
            )
            expected = Vector(Vector("x" -> "wire"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.client.hpack-literal-without-static-name",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "6.2.2", "A four-bit indexed literal name can refer to the static table.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("user-agent" -> "wire"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("0f2b0477697265")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.server.hpack-literal-without-static-name",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "6.2.2", "A four-bit indexed literal name can refer to the static table.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("0f2b0477697265")
            )
            expected = Vector(Vector("user-agent" -> "wire"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.client.hpack-literal-never-new-name",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "6.2.3", "A never-indexed literal appends the supplied new name and value.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "wire"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("1001780477697265")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.server.hpack-literal-never-new-name",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "6.2.3", "A never-indexed literal appends the supplied new name and value.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("1001780477697265")
            )
            expected = Vector(Vector("x" -> "wire"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.client.hpack-literal-never-static-name",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "6.2.3", "A never-indexed literal can use a static indexed name.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("authorization" -> "token"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("1f0805746f6b656e")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.server.hpack-literal-never-static-name",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "6.2.3", "A never-indexed literal can use a static indexed name.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("1f0805746f6b656e")
            )
            expected = Vector(Vector("authorization" -> "token"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.1
    WireCase(
      "h2.client.hpack-literal-incremental-new-name",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "6.2.1",
        "Incremental indexing delivers the literal even when the chosen table capacity is zero.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "wire"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("4001780477697265")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.1
    WireCase(
      "h2.server.hpack-literal-incremental-new-name",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "6.2.1",
        "Incremental indexing delivers the literal even when the chosen table capacity is zero.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("4001780477697265")
            )
            expected = Vector(Vector("x" -> "wire"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.1
    WireCase(
      "h2.client.hpack-literal-incremental-static-name",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "6.2.1", "A six-bit indexed literal name can refer to the static table.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("user-agent" -> "wire"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("7a0477697265")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.1
    WireCase(
      "h2.server.hpack-literal-incremental-static-name",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "6.2.1", "A six-bit indexed literal name can refer to the static table.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("7a0477697265")
            )
            expected = Vector(Vector("user-agent" -> "wire"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.client.hpack-static-last-index",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "2.3.3", "Static index 61 remains valid when the dynamic table is empty.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("www-authenticate" -> ""))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("bd")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.server.hpack-static-last-index",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "2.3.3", "Static index 61 remains valid when the dynamic table is empty.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("bd")
            )
            expected = Vector(Vector("www-authenticate" -> ""))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-static-name-prefix-boundary",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "An index equal to the four-bit prefix maximum uses a following zero octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("accept-charset" -> "utf-8"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("0f00057574662d38")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-static-name-prefix-boundary",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "An index equal to the four-bit prefix maximum uses a following zero octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("0f00057574662d38")
            )
            expected = Vector(Vector("accept-charset" -> "utf-8"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-huffman-value",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.2",
        "Huffman value length counts encoded octets and the RFC code reconstructs the original value.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "www.example.com"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("0001788cf1e3c2e5f23a6ba0ab90f4ff")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-huffman-value",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.2",
        "Huffman value length counts encoded octets and the RFC code reconstructs the original value.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("0001788cf1e3c2e5f23a6ba0ab90f4ff")
            )
            expected = Vector(Vector("x" -> "www.example.com"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-huffman-name",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.2",
        "A literal name can be Huffman encoded independently of its raw value.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("custom-key" -> "custom-value"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex(
                "008825a849e95ba97d7f0c637573746f6d2d76616c7565"
              )
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-huffman-name",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.2",
        "A literal name can be Huffman encoded independently of its raw value.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex(
                "008825a849e95ba97d7f0c637573746f6d2d76616c7565"
              )
            )
            expected = Vector(Vector("custom-key" -> "custom-value"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-huffman-name-value",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "A literal can Huffman encode both name and value.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("custom-key" -> "custom-value"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("008825a849e95ba97d7f8925a849e95bb8e8b4bf")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-huffman-name-value",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "A literal can Huffman encode both name and value.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("008825a849e95ba97d7f8925a849e95bb8e8b4bf")
            )
            expected = Vector(Vector("custom-key" -> "custom-value"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-huffman-empty",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "A zero-octet Huffman value is an empty string.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> ""))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("00017880")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-huffman-empty",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "A zero-octet Huffman value is an empty string.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("00017880")
            )
            expected = Vector(Vector("x" -> ""))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-huffman-no-padding",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "An octet-aligned Huffman symbol requires no padding.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "&"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("00017881f8")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-huffman-no-padding",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "An octet-aligned Huffman symbol requires no padding.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("00017881f8")
            )
            expected = Vector(Vector("x" -> "&"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-huffman-seven-bit-padding",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "Seven most-significant EOS bits are permitted as Huffman padding.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "00000"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("000178840000007f")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-huffman-seven-bit-padding",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "Seven most-significant EOS bits are permitted as Huffman padding.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("000178840000007f")
            )
            expected = Vector(Vector("x" -> "00000"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-string-empty",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "A zero-length raw string produces an empty field value.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> ""))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("00017800")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-string-empty",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "A zero-length raw string produces an empty field value.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("00017800")
            )
            expected = Vector(Vector("x" -> ""))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-string-raw-octets",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.2",
        "Non-Huffman string data is a sequence of octets, including permitted obs-text.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "\u0080\u00ff"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("0001780280ff")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-string-raw-octets",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.2",
        "Non-Huffman string data is a sequence of octets, including permitted obs-text.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("0001780280ff")
            )
            expected = Vector(Vector("x" -> "\u0080\u00ff"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-string-length-126",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "A string length immediately below the seven-bit prefix maximum is encoded in that prefix.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(
          Vector(
            "x" -> "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
          )
        )
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("0001787e") ++ octets("a" * 126)
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-string-length-126",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "A string length immediately below the seven-bit prefix maximum is encoded in that prefix.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("0001787e") ++ octets("a" * 126)
            )
            expected = Vector(
              Vector(
                "x" -> "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              )
            )
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-string-length-127",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "A string length equal to the seven-bit prefix maximum includes a zero continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(
          Vector(
            "x" -> "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
          )
        )
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("0001787f00") ++ octets("a" * 127)
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-string-length-127",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "A string length equal to the seven-bit prefix maximum includes a zero continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("0001787f00") ++ octets("a" * 127)
            )
            expected = Vector(
              Vector(
                "x" -> "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              )
            )
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-string-length-128",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "A string length above the seven-bit prefix maximum adds the continuation value.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(
          Vector(
            "x" -> "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
          )
        )
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("0001787f01") ++ octets("a" * 128)
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-string-length-128",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "A string length above the seven-bit prefix maximum adds the continuation value.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("0001787f01") ++ octets("a" * 128)
            )
            expected = Vector(
              Vector(
                "x" -> "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              )
            )
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-string-length-255",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "A multi-octet string length combines least-significant groups before most-significant groups.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(
          Vector(
            "x" -> "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
          )
        )
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("0001787f8001") ++ octets("a" * 255)
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-string-length-255",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "A multi-octet string length combines least-significant groups before most-significant groups.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("0001787f8001") ++ octets("a" * 255)
            )
            expected = Vector(
              Vector(
                "x" -> "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              )
            )
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-string-name-length-127",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "The same seven-bit integer boundary applies to literal name lengths.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(
          Vector(
            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" -> "v"
          )
        )
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(0L) ++ ResponsePrefix ++ hex("007f00") ++ octets("x" * 127) ++ hex("0176")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-string-name-length-127",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "The same seven-bit integer boundary applies to literal name lengths.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(0L) ++ RequestPrefix ++ hex("007f00") ++ octets("x" * 127) ++ hex("0176")
            )
            expected = Vector(
              Vector(
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" -> "v"
              )
            )
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 0)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-3.2
    WireCase(
      "h2.client.hpack-indexed-same-block",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "3.2",
        "Incrementally indexed fields are immediately available to later representations in the same block.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a", "x" -> "a"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(34L) ++ ResponsePrefix ++ hex("4001780161be")
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 34)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-3.2
    WireCase(
      "h2.server.hpack-indexed-same-block",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "3.2",
        "Incrementally indexed fields are immediately available to later representations in the same block.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(34L) ++ RequestPrefix ++ hex("4001780161be")
            )
            expected = Vector(Vector("x" -> "a", "x" -> "a"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 34)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.2
    WireCase(
      "h2.client.hpack-dynamic-stream-reuse",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "2.2",
        "One connection decoding context retains dynamic entries across successive streams.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a"), Vector("x" -> "a"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(34L) ++ ResponsePrefix ++ hex("4001780161"),
              ResponsePrefix ++ hex("be"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 34)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.2
    WireCase(
      "h2.server.hpack-dynamic-stream-reuse",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "2.2",
        "One connection decoding context retains dynamic entries across successive streams.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(34L) ++ RequestPrefix ++ hex("4001780161"),
              RequestPrefix ++ hex("be"),
            )
            expected = Vector(Vector("x" -> "a"), Vector("x" -> "a"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 34)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.2
    WireCase(
      "h2.client.hpack-dynamic-order",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "2.3.2",
        "The newest dynamic entry has the lowest dynamic index and older entries move to higher indexes.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a", "x" -> "b"), Vector("x" -> "b", "x" -> "a"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(68L) ++ ResponsePrefix ++ hex("40017801614001780162"),
              ResponsePrefix ++ hex("bebf"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 68)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.2
    WireCase(
      "h2.server.hpack-dynamic-order",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "2.3.2",
        "The newest dynamic entry has the lowest dynamic index and older entries move to higher indexes.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(68L) ++ RequestPrefix ++ hex("40017801614001780162"),
              RequestPrefix ++ hex("bebf"),
            )
            expected = Vector(Vector("x" -> "a", "x" -> "b"), Vector("x" -> "b", "x" -> "a"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 68)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.2
    WireCase(
      "h2.client.hpack-dynamic-duplicates",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "2.3.2",
        "Duplicate dynamic entries are legal and both indexed values remain observable, including ordered HTTP combination.",
        "MUST NOT",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a", "x" -> "a"), Vector("x" -> "a", "x" -> "a"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(68L) ++ ResponsePrefix ++ hex("40017801614001780161"),
              ResponsePrefix ++ hex("bebf"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 68)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.2
    WireCase(
      "h2.server.hpack-dynamic-duplicates",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "2.3.2",
        "Duplicate dynamic entries are legal and both indexed values remain observable, including ordered HTTP combination.",
        "MUST NOT",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(68L) ++ RequestPrefix ++ hex("40017801614001780161"),
              RequestPrefix ++ hex("bebf"),
            )
            expected = Vector(Vector("x" -> "a", "x" -> "a"), Vector("x" -> "a", "x" -> "a"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 68)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.client.hpack-without-preserves-table",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "6.2.2",
        "A non-indexing literal does not insert, reorder, or replace a retained dynamic entry.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a"), Vector("x" -> "b", "x" -> "a"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(34L) ++ ResponsePrefix ++ hex("4001780161"),
              ResponsePrefix ++ hex("0001780162be"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 34)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.server.hpack-without-preserves-table",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "6.2.2",
        "A non-indexing literal does not insert, reorder, or replace a retained dynamic entry.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(34L) ++ RequestPrefix ++ hex("4001780161"),
              RequestPrefix ++ hex("0001780162be"),
            )
            expected = Vector(Vector("x" -> "a"), Vector("x" -> "b", "x" -> "a"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 34)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.client.hpack-never-preserves-table",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "6.2.3",
        "A never-indexed literal does not insert, reorder, or replace a retained dynamic entry.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a"), Vector("x" -> "b", "x" -> "a"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(34L) ++ ResponsePrefix ++ hex("4001780161"),
              ResponsePrefix ++ hex("1001780162be"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 34)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.server.hpack-never-preserves-table",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "6.2.3",
        "A never-indexed literal does not insert, reorder, or replace a retained dynamic entry.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(34L) ++ RequestPrefix ++ hex("4001780161"),
              RequestPrefix ++ hex("1001780162be"),
            )
            expected = Vector(Vector("x" -> "a"), Vector("x" -> "b", "x" -> "a"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 34)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.4
    WireCase(
      "h2.client.hpack-dynamic-name-evicted-source",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.4",
        "An incrementally indexed name survives eviction of its source entry while adding the new field.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a"), Vector("x" -> "b", "x" -> "b"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(34L) ++ ResponsePrefix ++ hex("4001780161"),
              ResponsePrefix ++ hex("7e0162be"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 34)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.4
    WireCase(
      "h2.server.hpack-dynamic-name-evicted-source",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.4",
        "An incrementally indexed name survives eviction of its source entry while adding the new field.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(34L) ++ RequestPrefix ++ hex("4001780161"),
              RequestPrefix ++ hex("7e0162be"),
            )
            expected = Vector(Vector("x" -> "a"), Vector("x" -> "b", "x" -> "b"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 34)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.client.hpack-dynamic-name-without",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "6.2.2",
        "A non-indexing literal can reference a dynamic name while leaving its original entry unchanged.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a"), Vector("x" -> "b", "x" -> "a"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(34L) ++ ResponsePrefix ++ hex("4001780161"),
              ResponsePrefix ++ hex("0f2f0162be"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 34)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.server.hpack-dynamic-name-without",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "6.2.2",
        "A non-indexing literal can reference a dynamic name while leaving its original entry unchanged.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(34L) ++ RequestPrefix ++ hex("4001780161"),
              RequestPrefix ++ hex("0f2f0162be"),
            )
            expected = Vector(Vector("x" -> "a"), Vector("x" -> "b", "x" -> "a"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 34)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.client.hpack-dynamic-name-never",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "6.2.3",
        "A never-indexed literal can reference a dynamic name while leaving its original entry unchanged.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a"), Vector("x" -> "b", "x" -> "a"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(34L) ++ ResponsePrefix ++ hex("4001780161"),
              ResponsePrefix ++ hex("1f2f0162be"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 34)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.server.hpack-dynamic-name-never",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "6.2.3",
        "A never-indexed literal can reference a dynamic name while leaving its original entry unchanged.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(34L) ++ RequestPrefix ++ hex("4001780161"),
              RequestPrefix ++ hex("1f2f0162be"),
            )
            expected = Vector(Vector("x" -> "a"), Vector("x" -> "b", "x" -> "a"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 34)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-name-index-six-bit-boundary",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "A dynamic name index of 63 uses a six-bit saturated prefix and a zero continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a", "y" -> "b"), Vector("x" -> "c", "x" -> "c"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(102L) ++ ResponsePrefix ++ hex("40017801614001790162"),
              ResponsePrefix ++ hex("7f000163be"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 102)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-name-index-six-bit-boundary",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "A dynamic name index of 63 uses a six-bit saturated prefix and a zero continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(102L) ++ RequestPrefix ++ hex("40017801614001790162"),
              RequestPrefix ++ hex("7f000163be"),
            )
            expected = Vector(Vector("x" -> "a", "y" -> "b"), Vector("x" -> "c", "x" -> "c"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 102)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-indexed-seven-bit-boundary",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "A dynamic field index of 127 uses a seven-bit saturated prefix and a zero continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(boundaryEntries, Vector("x-0" -> "v"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(4096L) ++ ResponsePrefix ++ boundaryEntries.foldLeft(ByteVector.empty) {
                case (bytes, (name, value)) => bytes ++ literal(0x40, name, value)
              },
              ResponsePrefix ++ hex("ff00"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 4096)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-indexed-seven-bit-boundary",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "A dynamic field index of 127 uses a seven-bit saturated prefix and a zero continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(4096L) ++ RequestPrefix ++ boundaryEntries.foldLeft(ByteVector.empty) {
                case (bytes, (name, value)) => bytes ++ literal(0x40, name, value)
              },
              RequestPrefix ++ hex("ff00"),
            )
            expected = Vector(boundaryEntries, Vector("x-0" -> "v"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 4096)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.4
    WireCase(
      "h2.client.hpack-table-oldest-eviction",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.4",
        "Insertion evicts the oldest entry before adding a new entry that exactly fills the chosen capacity.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a"), Vector("y" -> "b", "y" -> "b"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(34L) ++ ResponsePrefix ++ hex("4001780161"),
              ResponsePrefix ++ hex("4001790162be"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 34)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.4
    WireCase(
      "h2.server.hpack-table-oldest-eviction",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.4",
        "Insertion evicts the oldest entry before adding a new entry that exactly fills the chosen capacity.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(34L) ++ RequestPrefix ++ hex("4001780161"),
              RequestPrefix ++ hex("4001790162be"),
            )
            expected = Vector(Vector("x" -> "a"), Vector("y" -> "b", "y" -> "b"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 34)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.3
    WireCase(
      "h2.client.hpack-table-shrink-retains-newest",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.3",
        "A size reduction evicts oldest entries and preserves a newest entry that exactly fits.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a", "y" -> "b"), Vector("y" -> "b"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(68L) ++ ResponsePrefix ++ hex("40017801614001790162"),
              hex("3f03") ++ ResponsePrefix ++ hex("be"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 68)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.3
    WireCase(
      "h2.server.hpack-table-shrink-retains-newest",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.3",
        "A size reduction evicts oldest entries and preserves a newest entry that exactly fits.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(68L) ++ RequestPrefix ++ hex("40017801614001790162"),
              hex("3f03") ++ RequestPrefix ++ hex("be"),
            )
            expected = Vector(Vector("x" -> "a", "y" -> "b"), Vector("y" -> "b"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 68)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
    WireCase(
      "h2.client.hpack-table-zero-restore-insert",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.2",
        "Two leading updates can clear the table, restore capacity, and permit fresh insertions.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a"), Vector("y" -> "b", "y" -> "b"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(34L) ++ ResponsePrefix ++ hex("4001780161"),
              hex("203f03") ++ ResponsePrefix ++ hex("4001790162be"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 34)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
    WireCase(
      "h2.server.hpack-table-zero-restore-insert",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.2",
        "Two leading updates can clear the table, restore capacity, and permit fresh insertions.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(34L) ++ RequestPrefix ++ hex("4001780161"),
              hex("203f03") ++ RequestPrefix ++ hex("4001790162be"),
            )
            expected = Vector(Vector("x" -> "a"), Vector("y" -> "b", "y" -> "b"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 34)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.4
    WireCase(
      "h2.client.hpack-table-oversized-entry-accepted",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.4",
        "Inserting an entry larger than the capacity is legal and still appends the decoded field.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "a"), Vector("xx" -> "b"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(34L) ++ ResponsePrefix ++ hex("4001780161"),
              ResponsePrefix ++ hex("400278780162"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 34)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.4
    WireCase(
      "h2.server.hpack-table-oversized-entry-accepted",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.4",
        "Inserting an entry larger than the capacity is legal and still appends the decoded field.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(34L) ++ RequestPrefix ++ hex("4001780161"),
              RequestPrefix ++ hex("400278780162"),
            )
            expected = Vector(Vector("x" -> "a"), Vector("xx" -> "b"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 34)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.1
    WireCase(
      "h2.client.hpack-table-huffman-exact-size",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.1",
        "Dynamic entry size is 32 plus the uncompressed name and value octet lengths.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val expected = Vector(Vector("x" -> "www.example.com"), Vector("x" -> "www.example.com"))
        context.client(Protocol.Http2)(driver =>
          expected
            .traverse_(fields =>
              driver.exchange(EmptyRequest).use(response => checkResponse(response, fields))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            blocks = Vector(
              tableSize(48L) ++ ResponsePrefix ++ hex("4001788cf1e3c2e5f23a6ba0ab90f4ff"),
              ResponsePrefix ++ hex("be"),
            )
            _ <- blocks.traverse_ { block =>
              for {
                request <- readRequest(session)
                _ <- writeBlock(session, request.streamId, block, 48)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.1
    WireCase(
      "h2.server.hpack-table-huffman-exact-size",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.1",
        "Dynamic entry size is 32 plus the uncompressed name and value octet lengths.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            blocks = Vector(
              tableSize(48L) ++ RequestPrefix ++ hex("4001788cf1e3c2e5f23a6ba0ab90f4ff"),
              RequestPrefix ++ hex("be"),
            )
            expected = Vector(Vector("x" -> "www.example.com"), Vector("x" -> "www.example.com"))
            _ <- blocks.zip(expected).zipWithIndex.traverse_ { case ((block, fields), index) =>
              val streamId = index * 2 + 1
              for {
                _ <- writeBlock(session, streamId, block, 48)
                response <- readResponse(session, streamId)
                _ <- Check.equal(response.body, ByteVector.empty)
                received <- server.received
                _ <- Check.equal(received.method, "GET")
                _ <- Check.equal(received.path, "/")
                _ <- checkFields(received.headers, fields)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.1
    WireCase(
      "h2.client.hpack-reject-index-zero",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "6.1", "Index zero in an indexed representation is a decoding error.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("80")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.1
    WireCase(
      "h2.server.hpack-reject-index-zero",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "6.1", "Index zero in an indexed representation is a decoding error.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("80")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.client.hpack-reject-index-empty-dynamic",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "2.3.3",
        "A dynamic index above the combined table length is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("be")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.server.hpack-reject-index-empty-dynamic",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "2.3.3",
        "A dynamic index above the combined table length is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("be")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-reject-index-integer-truncated",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "A saturated indexed prefix requires its terminating continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("ff")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-reject-index-integer-truncated",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "A saturated indexed prefix requires its terminating continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("ff")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-reject-incremental-name-integer-truncated",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "A saturated six-bit incremental name index requires its terminating continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("7f")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-reject-incremental-name-integer-truncated",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "A saturated six-bit incremental name index requires its terminating continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("7f")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-reject-without-name-integer-truncated",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "A saturated four-bit non-indexing name index requires its terminating continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("0f")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-reject-without-name-integer-truncated",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "A saturated four-bit non-indexing name index requires its terminating continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("0f")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-reject-never-name-integer-truncated",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "A saturated four-bit never-indexed name index requires its terminating continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("1f")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-reject-never-name-integer-truncated",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "A saturated four-bit never-indexed name index requires its terminating continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("1f")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-reject-index-integer-huge",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "An index larger than any possible table address must not wrap into a valid index.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("ffffffffffffffffffff7f")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-reject-index-integer-huge",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "An index larger than any possible table address must not wrap into a valid index.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("ffffffffffffffffffff7f")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-name-truncated",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "A literal name cannot extend beyond the completed field block.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("000378")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-reject-name-truncated",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "A literal name cannot extend beyond the completed field block.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("000378")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-reject-name-length-truncated",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "A saturated literal name length prefix requires its terminating continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("007f")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-reject-name-length-truncated",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "A saturated literal name length prefix requires its terminating continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("007f")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-value-truncated",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "A literal value cannot extend beyond the completed field block.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("0001780261")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-reject-value-truncated",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "A literal value cannot extend beyond the completed field block.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("0001780261")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.client.hpack-reject-value-missing",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "6.2.2",
        "A literal name must be followed by a complete value string representation.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("000178")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.server.hpack-reject-value-missing",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "6.2.2",
        "A literal name must be followed by a complete value string representation.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("000178")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-reject-string-length-truncated",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.1",
        "A saturated string length prefix requires its terminating continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("0001787f")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-reject-string-length-truncated",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.1",
        "A saturated string length prefix requires its terminating continuation octet.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("0001787f")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-huffman-string-truncated",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.2",
        "A Huffman length cannot advertise more encoded octets than the field block contains.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("000178828c")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-reject-huffman-string-truncated",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.2",
        "A Huffman length cannot advertise more encoded octets than the field block contains.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("000178828c")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.client.hpack-reject-incremental-name-index",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "2.3.3",
        "An incremental literal name index beyond both tables is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("7e0161")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.server.hpack-reject-incremental-name-index",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "2.3.3",
        "An incremental literal name index beyond both tables is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("7e0161")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.client.hpack-reject-without-name-index",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "2.3.3",
        "A non-indexing literal name index beyond both tables is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("0f2f0161")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.server.hpack-reject-without-name-index",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "2.3.3",
        "A non-indexing literal name index beyond both tables is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("0f2f0161")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.client.hpack-reject-never-name-index",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "2.3.3",
        "A never-indexed literal name index beyond both tables is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("1f2f0161")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.server.hpack-reject-never-name-index",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "2.3.3",
        "A never-indexed literal name index beyond both tables is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("1f2f0161")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-huffman-value-eos",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.2",
        "A Huffman-encoded value containing the EOS symbol is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("00017884ffffffff")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-reject-huffman-value-eos",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.2",
        "A Huffman-encoded value containing the EOS symbol is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("00017884ffffffff")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-huffman-name-eos",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "5.2",
        "A Huffman-encoded name containing the EOS symbol is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              // 0xf8 encodes the legal field name '&' before the forbidden EOS symbol.
              block = tableSize(0L) ++ ResponsePrefix ++ hex("0085f8ffffffff0161")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-reject-huffman-name-eos",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "5.2",
        "A Huffman-encoded name containing the EOS symbol is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            // 0xf8 encodes the legal field name '&' before the forbidden EOS symbol.
            block = tableSize(0L) ++ RequestPrefix ++ hex("0085f8ffffffff0161")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-huffman-padding-eight",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "Huffman padding longer than seven bits is a decoding error.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("00017881ff")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-reject-huffman-padding-eight",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "Huffman padding longer than seven bits is a decoding error.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("00017881ff")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-huffman-name-padding-eight",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "Huffman name padding longer than seven bits is a decoding error.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              // A padding-blind decoder still obtains a nonempty legal field name '&'.
              block = tableSize(0L) ++ ResponsePrefix ++ hex("0082f8ff0161")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-reject-huffman-name-padding-eight",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "Huffman name padding longer than seven bits is a decoding error.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            // A padding-blind decoder still obtains a nonempty legal field name '&'.
            block = tableSize(0L) ++ RequestPrefix ++ hex("0082f8ff0161")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-huffman-value-padding",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "Huffman value padding must match the most significant EOS bits.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("0001788100")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-reject-huffman-value-padding",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "Huffman value padding must match the most significant EOS bits.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("0001788100")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-huffman-name-padding",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "Huffman name padding must match the most significant EOS bits.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("0081000161")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-reject-huffman-name-padding",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "Huffman name padding must match the most significant EOS bits.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("0081000161")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
    WireCase(
      "h2.client.hpack-reject-update-after-index",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.2",
        "A dynamic table size update after an indexed field is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("bd20")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
    WireCase(
      "h2.server.hpack-reject-update-after-index",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.2",
        "A dynamic table size update after an indexed field is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("bd20")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
    WireCase(
      "h2.client.hpack-reject-update-after-literal",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.2",
        "A dynamic table size update after a literal field is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = tableSize(0L) ++ ResponsePrefix ++ hex("000178016120")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
    WireCase(
      "h2.server.hpack-reject-update-after-literal",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.2",
        "A dynamic table size update after a literal field is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = tableSize(0L) ++ RequestPrefix ++ hex("000178016120")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-reject-update-integer-truncated",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.1", "A saturated dynamic-table size prefix requires a complete integer.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              // END_HEADERS ends this integer. Appending pseudo-fields would complete it.
              // Generic closure cannot isolate this fault from missing pseudo-fields (inventory gap).
              block = hex("3f")
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.server.hpack-reject-update-integer-truncated",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.1", "A saturated dynamic-table size prefix requires a complete integer.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            // END_HEADERS ends this integer. Appending pseudo-fields would complete it.
            // Generic closure cannot isolate this fault from missing pseudo-fields (inventory gap).
            block = hex("3f")
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            unaccepted = server.pollReceived
              .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
            _ <- requireConnectionError(session, 1, unaccepted)
            _ <- unaccepted
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.3
    WireCase(
      "h2.client.hpack-reject-update-above-advertised",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "6.3",
        "A dynamic table size update above the acknowledged SETTINGS_HEADER_TABLE_SIZE is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              limit <- session.peerTableLimit.get
              block = tableSize(limit + 1L) ++ ResponsePrefix
              _ <- writeBlock(session, request.streamId, block, 0, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.3
    WireCase(
      "h2.server.hpack-reject-update-above-advertised",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "6.3",
        "A dynamic table size update above the acknowledged SETTINGS_HEADER_TABLE_SIZE is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            limit <- session.peerTableLimit.get
            block = tableSize(limit + 1L) ++ RequestPrefix
            _ <- writeBlock(session, 1, block, 0, compressionNegative = true)
            unaccepted = server.pollReceived
              .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
            _ <- requireConnectionError(session, 1, unaccepted)
            _ <- unaccepted
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.client.hpack-reject-index-beyond-populated",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "2.3.3",
        "An index beyond the last populated dynamic entry is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            driver
              .exchange(EmptyRequest)
              .use(response => checkResponse(response, Vector("x" -> "a")))
              .handleErrorWith(unexpectedRejection) *>
              ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
                result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                  requireRejected(result) *> rejectedOnWire.get
              }
          ) { peer =>
            for {
              session <- accept(peer)
              warmup0 <- readRequest(session)
              _ <- writeBlock(
                session,
                warmup0.streamId,
                tableSize(34L) ++ ResponsePrefix ++ hex("4001780161"),
                34,
              )
              request <- readRequest(session)
              block = ResponsePrefix ++ hex("bf")
              _ <- writeBlock(session, request.streamId, block, 34, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.server.hpack-reject-index-beyond-populated",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "2.3.3",
        "An index beyond the last populated dynamic entry is a decoding error.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            _ <- writeBlock(session, 1, tableSize(34L) ++ RequestPrefix ++ hex("4001780161"), 34)
            _ <- readResponse(session, 1)
            accepted0 <- server.received
            _ <- checkFields(accepted0.headers, Vector("x" -> "a"))
            block = RequestPrefix ++ hex("bf")
            _ <- writeBlock(session, 3, block, 34, compressionNegative = true)
            _ <- requireConnectionError(session, 3, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.client.hpack-reject-without-not-stored",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "6.2.2",
        "A literal without indexing must not create a subsequently addressable dynamic entry.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            driver
              .exchange(EmptyRequest)
              .use(response => checkResponse(response, Vector("x" -> "a")))
              .handleErrorWith(unexpectedRejection) *>
              ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
                result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                  requireRejected(result) *> rejectedOnWire.get
              }
          ) { peer =>
            for {
              session <- accept(peer)
              warmup0 <- readRequest(session)
              _ <- writeBlock(
                session,
                warmup0.streamId,
                tableSize(34L) ++ ResponsePrefix ++ hex("0001780161"),
                34,
              )
              request <- readRequest(session)
              block = ResponsePrefix ++ hex("be")
              _ <- writeBlock(session, request.streamId, block, 34, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.server.hpack-reject-without-not-stored",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "6.2.2",
        "A literal without indexing must not create a subsequently addressable dynamic entry.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            _ <- writeBlock(session, 1, tableSize(34L) ++ RequestPrefix ++ hex("0001780161"), 34)
            _ <- readResponse(session, 1)
            accepted0 <- server.received
            _ <- checkFields(accepted0.headers, Vector("x" -> "a"))
            block = RequestPrefix ++ hex("be")
            _ <- writeBlock(session, 3, block, 34, compressionNegative = true)
            _ <- requireConnectionError(session, 3, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.client.hpack-reject-never-not-stored",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "6.2.3",
        "A never-indexed literal must not create a subsequently addressable dynamic entry.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            driver
              .exchange(EmptyRequest)
              .use(response => checkResponse(response, Vector("x" -> "a")))
              .handleErrorWith(unexpectedRejection) *>
              ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
                result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                  requireRejected(result) *> rejectedOnWire.get
              }
          ) { peer =>
            for {
              session <- accept(peer)
              warmup0 <- readRequest(session)
              _ <- writeBlock(
                session,
                warmup0.streamId,
                tableSize(34L) ++ ResponsePrefix ++ hex("1001780161"),
                34,
              )
              request <- readRequest(session)
              block = ResponsePrefix ++ hex("be")
              _ <- writeBlock(session, request.streamId, block, 34, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.server.hpack-reject-never-not-stored",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "6.2.3",
        "A never-indexed literal must not create a subsequently addressable dynamic entry.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            _ <- writeBlock(session, 1, tableSize(34L) ++ RequestPrefix ++ hex("1001780161"), 34)
            _ <- readResponse(session, 1)
            accepted0 <- server.received
            _ <- checkFields(accepted0.headers, Vector("x" -> "a"))
            block = RequestPrefix ++ hex("be")
            _ <- writeBlock(session, 3, block, 34, compressionNegative = true)
            _ <- requireConnectionError(session, 3, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.4
    WireCase(
      "h2.client.hpack-reject-evicted-oldest-index",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "4.4", "An entry evicted during insertion is no longer addressable.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            driver
              .exchange(EmptyRequest)
              .use(response => checkResponse(response, Vector("x" -> "a")))
              .handleErrorWith(unexpectedRejection) *>
              driver
                .exchange(EmptyRequest)
                .use(response => checkResponse(response, Vector("y" -> "b")))
                .handleErrorWith(unexpectedRejection) *>
              ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
                result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                  requireRejected(result) *> rejectedOnWire.get
              }
          ) { peer =>
            for {
              session <- accept(peer)
              warmup0 <- readRequest(session)
              _ <- writeBlock(
                session,
                warmup0.streamId,
                tableSize(34L) ++ ResponsePrefix ++ hex("4001780161"),
                34,
              )
              warmup1 <- readRequest(session)
              _ <- writeBlock(session, warmup1.streamId, ResponsePrefix ++ hex("4001790162"), 34)
              request <- readRequest(session)
              block = ResponsePrefix ++ hex("bf")
              _ <- writeBlock(session, request.streamId, block, 34, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.4
    WireCase(
      "h2.server.hpack-reject-evicted-oldest-index",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "4.4", "An entry evicted during insertion is no longer addressable.", "MUST"),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            _ <- writeBlock(session, 1, tableSize(34L) ++ RequestPrefix ++ hex("4001780161"), 34)
            _ <- readResponse(session, 1)
            accepted0 <- server.received
            _ <- checkFields(accepted0.headers, Vector("x" -> "a"))
            _ <- writeBlock(session, 3, RequestPrefix ++ hex("4001790162"), 34)
            _ <- readResponse(session, 3)
            accepted1 <- server.received
            _ <- checkFields(accepted1.headers, Vector("y" -> "b"))
            block = RequestPrefix ++ hex("bf")
            _ <- writeBlock(session, 5, block, 34, compressionNegative = true)
            _ <- requireConnectionError(session, 5, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.3
    WireCase(
      "h2.client.hpack-reject-shrink-evicted-index",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.3",
        "An entry evicted by a table-size reduction is no longer addressable.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            driver
              .exchange(EmptyRequest)
              .use(response => checkResponse(response, Vector("x" -> "a", "y" -> "b")))
              .handleErrorWith(unexpectedRejection) *>
              driver
                .exchange(EmptyRequest)
                .use(response => checkResponse(response, Vector("y" -> "b")))
                .handleErrorWith(unexpectedRejection) *>
              ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
                result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                  requireRejected(result) *> rejectedOnWire.get
              }
          ) { peer =>
            for {
              session <- accept(peer)
              warmup0 <- readRequest(session)
              _ <- writeBlock(
                session,
                warmup0.streamId,
                tableSize(68L) ++ ResponsePrefix ++ hex("40017801614001790162"),
                68,
              )
              warmup1 <- readRequest(session)
              _ <- writeBlock(
                session,
                warmup1.streamId,
                hex("3f03") ++ ResponsePrefix ++ hex("be"),
                68,
              )
              request <- readRequest(session)
              block = ResponsePrefix ++ hex("bf")
              _ <- writeBlock(session, request.streamId, block, 68, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.3
    WireCase(
      "h2.server.hpack-reject-shrink-evicted-index",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.3",
        "An entry evicted by a table-size reduction is no longer addressable.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            _ <- writeBlock(
              session,
              1,
              tableSize(68L) ++ RequestPrefix ++ hex("40017801614001790162"),
              68,
            )
            _ <- readResponse(session, 1)
            accepted0 <- server.received
            _ <- checkFields(accepted0.headers, Vector("x" -> "a", "y" -> "b"))
            _ <- writeBlock(session, 3, hex("3f03") ++ RequestPrefix ++ hex("be"), 68)
            _ <- readResponse(session, 3)
            accepted1 <- server.received
            _ <- checkFields(accepted1.headers, Vector("y" -> "b"))
            block = RequestPrefix ++ hex("bf")
            _ <- writeBlock(session, 5, block, 68, compressionNegative = true)
            _ <- requireConnectionError(session, 5, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
    WireCase(
      "h2.client.hpack-reject-zero-restored-index",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.2",
        "Restoring capacity after a zero-size update does not restore previously evicted entries.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            driver
              .exchange(EmptyRequest)
              .use(response => checkResponse(response, Vector("x" -> "a")))
              .handleErrorWith(unexpectedRejection) *>
              ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
                result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                  requireRejected(result) *> rejectedOnWire.get
              }
          ) { peer =>
            for {
              session <- accept(peer)
              warmup0 <- readRequest(session)
              _ <- writeBlock(
                session,
                warmup0.streamId,
                tableSize(34L) ++ ResponsePrefix ++ hex("4001780161"),
                34,
              )
              request <- readRequest(session)
              block = hex("203f03") ++ ResponsePrefix ++ hex("be")
              _ <- writeBlock(session, request.streamId, block, 34, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
    WireCase(
      "h2.server.hpack-reject-zero-restored-index",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.2",
        "Restoring capacity after a zero-size update does not restore previously evicted entries.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            _ <- writeBlock(session, 1, tableSize(34L) ++ RequestPrefix ++ hex("4001780161"), 34)
            _ <- readResponse(session, 1)
            accepted0 <- server.received
            _ <- checkFields(accepted0.headers, Vector("x" -> "a"))
            block = hex("203f03") ++ RequestPrefix ++ hex("be")
            _ <- writeBlock(session, 3, block, 34, compressionNegative = true)
            _ <- requireConnectionError(session, 3, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.4
    WireCase(
      "h2.client.hpack-reject-oversize-clears-table",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.4",
        "An oversized incremental entry empties the entire table and is not retained.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            driver
              .exchange(EmptyRequest)
              .use(response => checkResponse(response, Vector("x" -> "a")))
              .handleErrorWith(unexpectedRejection) *>
              driver
                .exchange(EmptyRequest)
                .use(response => checkResponse(response, Vector("xx" -> "b")))
                .handleErrorWith(unexpectedRejection) *>
              ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
                result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                  requireRejected(result) *> rejectedOnWire.get
              }
          ) { peer =>
            for {
              session <- accept(peer)
              warmup0 <- readRequest(session)
              _ <- writeBlock(
                session,
                warmup0.streamId,
                tableSize(34L) ++ ResponsePrefix ++ hex("4001780161"),
                34,
              )
              warmup1 <- readRequest(session)
              _ <- writeBlock(session, warmup1.streamId, ResponsePrefix ++ hex("400278780162"), 34)
              request <- readRequest(session)
              block = ResponsePrefix ++ hex("be")
              _ <- writeBlock(session, request.streamId, block, 34, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.4
    WireCase(
      "h2.server.hpack-reject-oversize-clears-table",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.4",
        "An oversized incremental entry empties the entire table and is not retained.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            _ <- writeBlock(session, 1, tableSize(34L) ++ RequestPrefix ++ hex("4001780161"), 34)
            _ <- readResponse(session, 1)
            accepted0 <- server.received
            _ <- checkFields(accepted0.headers, Vector("x" -> "a"))
            _ <- writeBlock(session, 3, RequestPrefix ++ hex("400278780162"), 34)
            _ <- readResponse(session, 3)
            accepted1 <- server.received
            _ <- checkFields(accepted1.headers, Vector("xx" -> "b"))
            block = RequestPrefix ++ hex("be")
            _ <- writeBlock(session, 5, block, 34, compressionNegative = true)
            _ <- requireConnectionError(session, 5, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.1
    WireCase(
      "h2.client.hpack-reject-huffman-compressed-size",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.1",
        "Encoded Huffman length cannot substitute for decoded octet length when calculating table size.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            driver
              .exchange(EmptyRequest)
              .use(response => checkResponse(response, Vector("x" -> "www.example.com")))
              .handleErrorWith(unexpectedRejection) *>
              ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
                result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                  requireRejected(result) *> rejectedOnWire.get
              }
          ) { peer =>
            for {
              session <- accept(peer)
              warmup0 <- readRequest(session)
              _ <- writeBlock(
                session,
                warmup0.streamId,
                tableSize(47L) ++ ResponsePrefix ++ hex("4001788cf1e3c2e5f23a6ba0ab90f4ff"),
                47,
              )
              request <- readRequest(session)
              block = ResponsePrefix ++ hex("be")
              _ <- writeBlock(session, request.streamId, block, 47, compressionNegative = true)
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.1
    WireCase(
      "h2.server.hpack-reject-huffman-compressed-size",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.1",
        "Encoded Huffman length cannot substitute for decoded octet length when calculating table size.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
          for {
            session <- connect(peer)
            _ <- writeBlock(
              session,
              1,
              tableSize(47L) ++ RequestPrefix ++ hex("4001788cf1e3c2e5f23a6ba0ab90f4ff"),
              47,
            )
            _ <- readResponse(session, 1)
            accepted0 <- server.received
            _ <- checkFields(accepted0.headers, Vector("x" -> "www.example.com"))
            block = RequestPrefix ++ hex("be")
            _ <- writeBlock(session, 3, block, 47, compressionNegative = true)
            _ <- requireConnectionError(session, 3, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.2
    WireCase(
      "h2.client.hpack-reject-initial-dynamic-index",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "2.3.2",
        "A dynamic index on a new connection cannot refer to preexisting state.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        Deferred[IO, Unit].flatMap { rejectedOnWire =>
          context.client(Protocol.Http2)(driver =>
            ClientObservation(driver.exchange(EmptyRequest))(_.body.compile.drain) { result =>
              result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *>
                requireRejected(result) *> rejectedOnWire.get
            }
          ) { peer =>
            for {
              session <- accept(peer)
              request <- readRequest(session)
              block = ResponsePrefix ++ hex("be")
              _ <- writeBlock(
                session,
                request.streamId,
                block,
                4096,
                compressionNegative = true,
                preserveInitialState = true,
              )
              _ <- requireConnectionError(session, request.streamId, IO.unit)
              _ <- rejectedOnWire.complete(()).void
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.2
    WireCase(
      "h2.server.hpack-reject-initial-dynamic-index",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "2.3.2",
        "A dynamic index on a new connection cannot refer to preexisting state.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, _) =>
          for {
            session <- connect(peer)
            block = RequestPrefix ++ hex("be")
            _ <- writeBlock(
              session,
              1,
              block,
              4096,
              compressionNegative = true,
              preserveInitialState = true,
            )
            _ <- requireConnectionError(session, 1, IO.unit)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.1
    WireCase(
      "h2.client.hpack-encoder-stateful-roundtrip",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "2.1",
        "Encoded fields preserve the supplied HTTP field values and remain independently decodable across streams; immediate codec input order is not exposed by the application API.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val fields = Vector("x-first" -> "one", "x-second" -> "two", "x-third" -> "three")
        val request = EmptyRequest.copy(headers = EmptyRequest.headers ++ fields)
        context.client(Protocol.Http2)(driver =>
          Vector(1, 2, 3)
            .traverse_(_ =>
              driver.exchange(request).use(response => checkResponse(response, Vector.empty))
            )
            .handleErrorWith(unexpectedRejection)
        ) { peer =>
          for {
            session <- accept(peer)
            _ <- Vector(1, 2, 3).traverse_ { _ =>
              for {
                captured <- readRequest(session)
                _ <- checkFields(captured.fields, fields)
                _ <- writeBlock(session, captured.streamId, tableSize(0L) ++ ResponsePrefix, 0)
              } yield ()
            }
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.1
    WireCase(
      "h2.server.hpack-encoder-stateful-roundtrip",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "2.1",
        "Encoded fields preserve the supplied HTTP field values and remain independently decodable across streams; immediate codec input order is not exposed by the application API.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        val fields = Vector("x-first" -> "one", "x-second" -> "two", "x-third" -> "three")
        context
          .server(Protocol.Http2, ServerScenario.Reply(headers = fields, body = ByteVector.empty))
          .use { case (peer, _) =>
            for {
              session <- connect(peer)
              _ <- Vector(1, 3, 5).traverse_ { streamId =>
                for {
                  _ <- writeBlock(session, streamId, tableSize(0L) ++ RequestPrefix, 0)
                  response <- readResponse(session, streamId)
                  _ <- Check.equal(response.body, ByteVector.empty)
                  _ <- checkFields(response.head.fields, fields)
                } yield ()
              }
            } yield ()
          }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
    WireCase(
      "h2.client.hpack-encoder-reduction-zero",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.2",
        "An acknowledged reduction to zero is signaled at the beginning of the next block and no dynamic entries remain usable.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val fields = Vector("x-first" -> "one", "x-second" -> "two", "x-third" -> "three")
        val request = EmptyRequest.copy(headers = EmptyRequest.headers ++ fields)
        Deferred[IO, Unit].flatMap { settingsApplied =>
          context.client(Protocol.Http2)(driver =>
            driver
              .exchange(request)
              .use(response => checkResponse(response, Vector.empty))
              .handleErrorWith(unexpectedRejection) *>
              settingsApplied.get *>
              driver
                .exchange(request)
                .use(response => checkResponse(response, Vector.empty))
                .handleErrorWith(unexpectedRejection)
          ) { peer =>
            for {
              session <- accept(peer)
              first <- readRequest(session)
              _ <- checkFields(first.fields, fields)
              _ <- session.decoder.tableCapacity
                .flatMap(capacity => requireCapacityChange[IO](capacity.toLong))
              _ <- writeBlock(session, first.streamId, tableSize(0L) ++ ResponsePrefix, 0)
              _ <- awaitAcknowledgments(session, 1)
              _ <- Http2PeerSettings.write(
                peer,
                Http2Wire.encode(Http2Wire.settings(Vector(1 -> 0L))),
              )
              _ <- awaitAcknowledgments(session, 2)
              decoder <- HpackWire.decoder[IO](maxTableSize = 0)
              constrained = session.copy(decoder = decoder)
              _ <- settingsApplied.complete(()).void
              second <- readRequest(constrained)
              updates = leadingSizes(second.block)
              _ <- Check(
                updates.headOption.contains(0L),
                "encoder omitted the required minimum-zero update",
              )
              _ <- Check(
                updates.size <= 2,
                "encoder emitted more than the required minimum/final updates",
              )
              _ <- checkFields(second.fields, fields)
              _ <- writeBlock(constrained, second.streamId, tableSize(0L) ++ ResponsePrefix, 0)
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
    WireCase(
      "h2.server.hpack-encoder-reduction-zero",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.2",
        "An acknowledged reduction to zero is signaled at the beginning of the next block and no dynamic entries remain usable.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        val fields = Vector("x-first" -> "one", "x-second" -> "two", "x-third" -> "three")
        context
          .server(Protocol.Http2, ServerScenario.Reply(headers = fields, body = ByteVector.empty))
          .use { case (peer, _) =>
            for {
              session <- connect(peer)
              _ <- writeBlock(session, 1, tableSize(0L) ++ RequestPrefix, 0)
              first <- readResponse(session, 1)
              _ <- checkFields(first.head.fields, fields)
              _ <- session.decoder.tableCapacity
                .flatMap(capacity => requireCapacityChange[IO](capacity.toLong))
              _ <- awaitAcknowledgments(session, 1)
              _ <- Http2PeerSettings
                .write(peer, Http2Wire.encode(Http2Wire.settings(Vector(1 -> 0L))))
              _ <- awaitAcknowledgments(session, 2)
              decoder <- HpackWire.decoder[IO](maxTableSize = 0)
              constrained = session.copy(decoder = decoder)
              _ <- writeBlock(constrained, 3, tableSize(0L) ++ RequestPrefix, 0)
              second <- readResponse(constrained, 3)
              updates = leadingSizes(second.firstBlock)
              _ <- Check(
                updates.headOption.contains(0L),
                "encoder omitted the required minimum-zero update",
              )
              _ <- Check(
                updates.size <= 2,
                "encoder emitted more than the required minimum/final updates",
              )
              _ <- checkFields(second.head.fields, fields)
              _ <- Check.equal(second.body, ByteVector.empty)
            } yield ()
          }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
    WireCase(
      "h2.client.hpack-encoder-minimum-before-restore",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "4.2",
        "If the peer limit falls to zero and rises before the next block, the encoder signals the minimum before any restored capacity.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
        val fields = Vector("x-first" -> "one", "x-second" -> "two", "x-third" -> "three")
        val request = EmptyRequest.copy(headers = EmptyRequest.headers ++ fields)
        Deferred[IO, Unit].flatMap { settingsApplied =>
          context.client(Protocol.Http2)(driver =>
            driver
              .exchange(request)
              .use(response => checkResponse(response, Vector.empty))
              .handleErrorWith(unexpectedRejection) *>
              settingsApplied.get *>
              driver
                .exchange(request)
                .use(response => checkResponse(response, Vector.empty))
                .handleErrorWith(unexpectedRejection)
          ) { peer =>
            for {
              session <- accept(peer)
              first <- readRequest(session)
              _ <- checkFields(first.fields, fields)
              _ <- session.decoder.tableCapacity
                .flatMap(capacity => requireCapacityChange[IO](capacity.toLong))
              _ <- writeBlock(session, first.streamId, tableSize(0L) ++ ResponsePrefix, 0)
              _ <- awaitAcknowledgments(session, 1)
              _ <- Http2PeerSettings.write(
                peer,
                Http2Wire.encode(Http2Wire.settings(Vector(1 -> 0L))) ++ Http2Wire.encode(
                  Http2Wire.settings(Vector(1 -> 128L))
                ),
              )
              _ <- awaitAcknowledgments(session, 3)
              decoder <- HpackWire.decoder[IO](maxTableSize = 128)
              constrained = session.copy(decoder = decoder)
              _ <- settingsApplied.complete(()).void
              second <- readRequest(constrained)
              updates = leadingSizes(second.block)
              _ <- Check(
                updates.headOption.contains(0L),
                "encoder omitted the required minimum-zero update",
              )
              _ <- Check(
                updates.size <= 2,
                "encoder emitted more than the required minimum/final updates",
              )
              _ <- checkFields(second.fields, fields)
              _ <- writeBlock(constrained, second.streamId, tableSize(0L) ++ ResponsePrefix, 0)
            } yield ()
          }
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
    WireCase(
      "h2.server.hpack-encoder-minimum-before-restore",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "4.2",
        "If the peer limit falls to zero and rises before the next block, the encoder signals the minimum before any restored capacity.",
        "MUST",
      ),
    ) { context =>
      Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
        val fields = Vector("x-first" -> "one", "x-second" -> "two", "x-third" -> "three")
        context
          .server(Protocol.Http2, ServerScenario.Reply(headers = fields, body = ByteVector.empty))
          .use { case (peer, _) =>
            for {
              session <- connect(peer)
              _ <- writeBlock(session, 1, tableSize(0L) ++ RequestPrefix, 0)
              first <- readResponse(session, 1)
              _ <- checkFields(first.head.fields, fields)
              _ <- session.decoder.tableCapacity
                .flatMap(capacity => requireCapacityChange[IO](capacity.toLong))
              _ <- awaitAcknowledgments(session, 1)
              _ <- Http2PeerSettings.write(
                peer,
                Http2Wire.encode(Http2Wire.settings(Vector(1 -> 0L))) ++ Http2Wire
                  .encode(Http2Wire.settings(Vector(1 -> 128L))),
              )
              _ <- awaitAcknowledgments(session, 3)
              decoder <- HpackWire.decoder[IO](maxTableSize = 128)
              constrained = session.copy(decoder = decoder)
              _ <- writeBlock(constrained, 3, tableSize(0L) ++ RequestPrefix, 0)
              second <- readResponse(constrained, 3)
              updates = leadingSizes(second.firstBlock)
              _ <- Check(
                updates.headOption.contains(0L),
                "encoder omitted the required minimum-zero update",
              )
              _ <- Check(
                updates.size <= 2,
                "encoder emitted more than the required minimum/final updates",
              )
              _ <- checkFields(second.head.fields, fields)
              _ <- Check.equal(second.body, ByteVector.empty)
            } yield ()
          }
      }
    },
  )

  private final case class Session[F[_]](
      peer: RawPeer[F],
      fromServer: Boolean,
      decoder: HpackWire.Decoder[F],
      peerTableLimit: Ref[F, Long],
      peerTableMinimum: Ref[F, Long],
      sentBlock: Ref[F, Boolean],
      acknowledgments: Ref[F, Int],
  )

  private final case class Head(streamId: Int, fields: Vector[(String, String)], block: ByteVector)
  private final case class Message(head: Head, body: ByteVector, firstBlock: ByteVector)

  private def hex(value: String): ByteVector = ByteVector.fromValidHex(value)
  private def octets(value: String): ByteVector =
    ByteVector.view(value.iterator.map(_.toByte).toArray)

  /** Pure stimulus construction, independent of the inspector and every target encoder. */
  private def integer(value: Long, prefix: Int, flags: Int): ByteVector = {
    require(value >= 0L && prefix >= 1 && prefix <= 8)
    val mask = (1 << prefix) - 1
    if (value < mask.toLong) ByteVector((flags | value.toInt).toByte)
    else {
      @annotation.tailrec
      def rest(remaining: Long, bytes: ByteVector): ByteVector =
        if (remaining < 128L) bytes ++ ByteVector(remaining.toByte)
        else rest(remaining >>> 7, bytes ++ ByteVector(((remaining & 127L) | 128L).toByte))
      rest(value - mask.toLong, ByteVector((flags | mask).toByte))
    }
  }

  private def tableSize(size: Long): ByteVector = integer(size, 5, 0x20)
  private def literal(kind: Int, name: String, value: String): ByteVector =
    ByteVector(kind.toByte) ++ integer(name.length.toLong, 7, 0) ++ octets(name) ++
      integer(value.length.toLong, 7, 0) ++ octets(value)

  private def verify[F[_]: ApplicativeThrow](condition: Boolean, detail: => String): F[Unit] =
    if (condition) ApplicativeThrow[F].unit
    else ApplicativeThrow[F].raiseError(new ProtocolFailure(detail))

  private def session[F[_]: Async](
      peer: RawPeer[F],
      decoderLimit: Int,
      fromServer: Boolean,
  ): F[Session[F]] =
    (
      HpackWire.decoder[F](maxTableSize = decoderLimit),
      Ref.of[F, Long](4096L),
      Ref.of[F, Long](4096L),
      Ref.of[F, Boolean](false),
      Ref.of[F, Int](0),
    )
      .mapN(Session(peer, fromServer, _, _, _, _, _))

  /** Raw client connecting to a target server. Each direction owns a separate compression table. */
  private def connect[F[_]: Async](peer: RawPeer[F], decoderLimit: Int = 4096)(implicit
      peerSettings: Http2PeerSettings[F]
  ): F[Session[F]] =
    for {
      result <- session(peer, decoderLimit, fromServer = true)
      _ <- Http2PeerSettings.write(
        peer,
        Http2Wire.clientPreface ++ Http2Wire.encode(
          Http2Wire.settings(Vector(1 -> decoderLimit.toLong))
        ),
      )
      initial <- Http2Wire.readFrame(peer)
      _ <- verify(
        initial.frameType == Http2Wire.Settings && !initial.hasFlag(Http2Wire.Ack),
        "missing initial target SETTINGS",
      )
      _ <- settings(result, initial)
    } yield result

  /** Raw server accepting a target client; initial request bytes are retained for independent decoding. */
  private def accept[F[_]: Async](
      peer: RawPeer[F]
  )(implicit peerSettings: Http2PeerSettings[F]): F[Session[F]] =
    for {
      result <- session(peer, 4096, fromServer = false)
      preface <- WireDecodingFailure.read(peer.readExact(Http2Wire.clientPreface.size.toInt))
      _ <- verify(preface == Http2Wire.clientPreface, "invalid target client preface")
      initial <- Http2Wire.readFrame(peer)
      _ <- verify(
        initial.frameType == Http2Wire.Settings && !initial.hasFlag(Http2Wire.Ack),
        "missing initial target SETTINGS",
      )
      // The server's first frame must be its own SETTINGS, before acknowledging the client.
      _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(1 -> 4096L)))
      _ <- settings(result, initial)
    } yield result

  private def settings[F[_]: Async](session: Session[F], frame: Http2Wire.Frame)(implicit
      peerSettings: Http2PeerSettings[F]
  ): F[Unit] =
    settings(session, frame, (write: F[Unit]) => write.as(true)).void

  private def settings[F[_]: Async](
      session: Session[F],
      frame: Http2Wire.Frame,
      reply: F[Unit] => F[Boolean],
  )(implicit
      peerSettings: Http2PeerSettings[F]
  ): F[Boolean] =
    Async[F].fromEither(Http2RejectionChecks.validate(frame, session.fromServer)) *>
      verify(frame.streamId == 0 && frame.payload.size % 6L == 0L, "malformed target SETTINGS") *>
      (if (frame.hasFlag(Http2Wire.Ack))
         verify(frame.payload.isEmpty, "SETTINGS ACK contains a payload") *> session.acknowledgments
           .update(_ + 1)
           .as(true)
       else
         (0 until (frame.payload.size / 6L).toInt).toVector.traverse_ { index =>
           val entry = frame.payload.slice(index.toLong * 6L, index.toLong * 6L + 6L)
           val key = ((entry(0) & 0xff) << 8) | (entry(1) & 0xff)
           val value = Http2Wire.readUint32(entry.drop(2))
           key match {
             case 1 =>
               session.peerTableLimit.set(value) *> session.peerTableMinimum.update(_.min(value))
             case 2 => verify(value <= 1L, "invalid target ENABLE_PUSH")
             case 4 => verify(value <= 0x7fffffffL, "invalid target INITIAL_WINDOW_SIZE")
             case 5 =>
               verify(value >= 16384L && value <= 0xffffffL, "invalid target MAX_FRAME_SIZE")
             case _ => Async[F].unit
           }
         } *> reply(Http2Wire.writeFrame(session.peer, Http2Wire.settings(ack = true))))

  private def control[F[_]: Async](session: Session[F], frame: Http2Wire.Frame)(implicit
      peerSettings: Http2PeerSettings[F]
  ): F[Unit] =
    peerSettings.positive(session.peer).flatMap { positive =>
      positive.observe(frame, session.fromServer) *>
        (if (frame.frameType == Http2Wire.GoAway) Async[F].unit
         else
           control(
             session,
             frame,
             (write: F[Unit]) => positive.incidental(write).map(_.writable),
           ).void)
    }

  private def control[F[_]: Async](
      session: Session[F],
      frame: Http2Wire.Frame,
      reply: F[Unit] => F[Boolean],
  )(implicit
      peerSettings: Http2PeerSettings[F]
  ): F[Boolean] =
    frame.frameType match {
      case Http2Wire.Settings => settings(session, frame, reply)
      case Http2Wire.Ping =>
        verify(frame.streamId == 0 && frame.payload.size == 8L, "invalid target PING") *>
          (if (frame.hasFlag(Http2Wire.Ack)) true.pure[F]
           else reply(Http2Wire.writeFrame(session.peer, frame.copy(flags = Http2Wire.Ack))))
      case Http2Wire.WindowUpdate =>
        verify(frame.payload.size == 4L, "invalid target WINDOW_UPDATE length") *>
          verify(
            (Http2Wire.readUint32(frame.payload) & 0x7fffffffL) != 0L,
            "zero target WINDOW_UPDATE",
          ).as(true)
      case Http2Wire.Priority =>
        verify(frame.streamId != 0 && frame.payload.size == 5L, "invalid target PRIORITY").as(true)
      case kind if kind > Http2Wire.Continuation => true.pure[F]
      case _ => verify(false, s"unexpected target frame: $frame").as(true)
    }

  private def next[F[_]: Async](
      session: Session[F]
  )(implicit peerSettings: Http2PeerSettings[F]): F[Http2Wire.Frame] =
    Http2FrameReader.readUntil(
      Http2Wire.readFrame(session.peer),
      1024,
      "too many control frames before HPACK traffic",
    )(
      frame => peerSettings.positive(session.peer).flatMap(_.observe(frame, session.fromServer)),
      frame =>
        frame.frameType == Http2Wire.Headers || frame.frameType == Http2Wire.Data ||
          frame.frameType == Http2Wire.RstStream,
      frame => control(session, frame),
    )

  private def awaitAcknowledgments[F[_]: Async](session: Session[F], count: Int)(implicit
      peerSettings: Http2PeerSettings[F]
  ): F[Unit] = {
    def loop(remaining: Int): F[Unit] =
      session.acknowledgments.get.flatMap { received =>
        if (received >= count) Async[F].unit
        else
          observationBound(remaining > 0, "SETTINGS acknowledgment frame bound exceeded") *>
            Http2Wire.readFrame(session.peer).flatMap(frame => control(session, frame)) *>
            loop(remaining - 1)
      }
    loop(1024)
  }

  /** Called only after the independent decoder validates the complete target block. */
  private def leadingSizes(block: ByteVector): Vector[Long] = {
    val values = Vector.newBuilder[Long]
    var cursor = 0L
    while (cursor < block.size && (block(cursor) & 0xe0) == 0x20) {
      var value = (block(cursor) & 0x1f).toLong
      cursor += 1L
      if (value == 31L) {
        var shift = 0
        var more = true
        while (more) {
          val octet = block(cursor) & 0xff
          cursor += 1L
          value += (octet & 0x7f).toLong << shift
          shift += 7
          more = (octet & 0x80) != 0
        }
      }
      values += value
    }
    values.result()
  }

  private def requireCapacityChange[F[_]: Async](capacity: Long): F[Unit] =
    if (capacity > 0L) Async[F].unit
    else
      Async[F].raiseError(
        new IllegalStateException(
          "HPACK encoder already chose zero capacity; a later capacity reduction cannot be exercised"
        )
      )

  private def writeBlock[F[_]: Async](
      session: Session[F],
      streamId: Int,
      block: ByteVector,
      capacity: Int,
      compressionNegative: Boolean = false,
      preserveInitialState: Boolean = false,
  )(implicit peerSettings: Http2PeerSettings[F]): F[Unit] =
    (session.peerTableLimit.get, session.peerTableMinimum.get, session.sentBlock.get).tupled
      .flatMap { case (limit, minimum, sent) =>
        val alreadyHonored = Either
          .catchNonFatal(leadingSizes(block))
          .toOption
          .flatMap(_.headOption)
          .exists(_ <= minimum)
        val incompatible = capacity.toLong > limit ||
          ((sent || preserveInitialState) && capacity.toLong > minimum && !alreadyHonored)
        val ready =
          if (!incompatible) Async[F].unit
          else
            Async[F].raiseError[Unit](
              new WireObservationLimit(
                s"HPACK witness needs table capacity $capacity; peer interval minimum $minimum and final limit $limit would evict its required state; scenario unexercised"
              )
            )
        // An initial-state witness must not clear a faulty prepopulated decoder. Other first blocks
        // may honor an initial decrease/restore; later minima must not erase a witness.
        val prepared =
          if (!sent && minimum < capacity.toLong && !alreadyHonored)
            tableSize(minimum) ++ block
          else block
        ready *> verify(
          prepared.size <= 16384L,
          "fixture HPACK block exceeds the baseline frame size",
        ) *>
          Http2PeerSettings.writeFrames(
            session.peer,
            Vector(
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream,
                streamId,
                prepared,
              )
            ),
            requireFieldAcceptance = !compressionNegative,
          ) *> session.peerTableMinimum.set(limit) *> session.sentBlock.set(true)
      }

  private def readHead[F[_]: Async](session: Session[F], first: Http2Wire.Frame)(implicit
      peerSettings: Http2PeerSettings[F]
  ): F[Head] = {
    def gather(bytes: ByteVector, frame: Http2Wire.Frame, remaining: Int): F[ByteVector] =
      (if (bytes.size <= 1024L * 1024L && remaining > 0) Async[F].unit
       else
         Async[F].raiseError[Unit](
           new WireObservationLimit("target header block exceeds observation bound")
         )) *>
        (if (frame.hasFlag(Http2Wire.EndHeaders)) bytes.pure[F]
         else
           Http2Wire.readFrame(session.peer).flatMap { continuation =>
             verify(
               continuation.frameType == Http2Wire.Continuation && continuation.streamId == first.streamId,
               "target interrupted a field block",
             ) *>
               observationBound[F](
                 continuation.payload.size <= 1024L * 1024L - bytes.size,
                 "target header block exceeds observation bound",
               ) *>
               Async[F].defer(gather(bytes ++ continuation.payload, continuation, remaining - 1))
           })
    for {
      _ <- verify(
        first.frameType == Http2Wire.Headers && first.streamId > 0,
        s"expected target HEADERS, observed $first",
      )
      initial <- Async[F]
        .delay {
          val bytes = Http2Wire.unpad(first)
          if (first.hasFlag(0x20)) {
            require(bytes.size >= 5L, "short target HEADERS priority")
            bytes.drop(5)
          } else bytes
        }
        .handleErrorWith(invalidTarget[F, ByteVector])
      block <- gather(initial, first, 1024)
      fields <- session.decoder
        .decode(block)
        .handleErrorWith(invalidTarget[F, Vector[(String, String)]])
    } yield Head(first.streamId, fields, block)
  }

  private def readBody[F[_]: Async](session: Session[F], first: Http2Wire.Frame)(implicit
      peerSettings: Http2PeerSettings[F]
  ): F[ByteVector] = {
    val policy = Http2ContentBudget.Policy(
      1024,
      65536L,
      "target content exceeds observation bound",
      "target content exceeds observation bound",
    )
    def loop(bytes: ByteVector, budget: Http2ContentBudget.State): F[ByteVector] =
      Async[F].fromEither(budget.beforeRead) *>
        next(session).flatMap { frame =>
          verify(
            frame.streamId == first.streamId,
            "unexpected target stream while reading content",
          ) *>
            (frame.frameType match {
              case Http2Wire.Data =>
                Async[F]
                  .delay(Http2Wire.unpad(frame))
                  .handleErrorWith(invalidTarget[F, ByteVector])
                  .flatMap { data =>
                    Async[F].fromEither(budget.reserve(data.size)).flatMap { next =>
                      Async[F].defer {
                        val all = bytes ++ data
                        if (frame.hasFlag(Http2Wire.EndStream)) all.pure[F]
                        else loop(all, next)
                      }
                    }
                  }
              case Http2Wire.Headers =>
                verify(
                  frame.hasFlag(Http2Wire.EndStream),
                  "target trailers did not terminate the stream",
                ) *>
                  readHead(session, frame).flatMap(head =>
                    verify(
                      !head.fields.exists(_._1.startsWith(":")),
                      "target trailers contain pseudo-fields",
                    ).as(bytes)
                  )
              case _ => verify[F](false, s"target rejected a valid HPACK block: $frame").as(bytes)
            })
        }
    if (first.hasFlag(Http2Wire.EndStream)) ByteVector.empty.pure[F]
    else loop(ByteVector.empty, policy.initial)
  }

  private def readRequest[F[_]: Async](
      session: Session[F]
  )(implicit peerSettings: Http2PeerSettings[F]): F[Head] =
    (for {
      first <- next(session)
      _ <- verify(first.streamId > 0 && (first.streamId & 1) == 1, "invalid target request stream")
      head <- readHead(session, first)
      _ <- readBody(session, first)
    } yield head).handleErrorWith(incompleteTarget[F, Head])

  private def readResponse[F[_]: Async](session: Session[F], streamId: Int)(implicit
      peerSettings: Http2PeerSettings[F]
  ): F[Message] = {
    def loop(remaining: Int, firstBlock: Option[ByteVector]): F[Message] =
      observationBound(remaining > 0, "too many informational response sections") *>
        (for {
          first <- next(session)
          _ <- verify(first.streamId == streamId, "target response used an unexpected stream")
          head <- readHead(session, first)
          statuses = head.fields.collect { case (":status", value) => value }
          informational = statuses match {
            case Vector(value) => value.length == 3 && value.head == '1' && value.forall(_.isDigit)
            case _ => false
          }
          response <-
            if (informational)
              verify(
                !first.hasFlag(Http2Wire.EndStream),
                "informational response ended the stream",
              ) *>
                verify(statuses != Vector("101"), "HTTP/2 does not permit status 101") *>
                Async[F].defer(loop(remaining - 1, firstBlock.orElse(Some(head.block))))
            else
              verify(statuses == Vector("200"), "valid HPACK request did not receive status 200") *>
                readBody(session, first)
                  .map(body => Message(head, body, firstBlock.getOrElse(head.block)))
        } yield response)
    peerSettings.positive(session.peer).flatMap { positive =>
      positive.expecting(Set(streamId)) *> loop(1024, None)
        .flatTap(_ => positive.completed(Set(streamId)))
        .handleErrorWith(incompleteTarget[F, Message])
    }
  }

  private def checkFields(
      actual: Vector[(String, String)],
      expected: Vector[(String, String)],
  ): IO[Unit] =
    // The API exposes native HTTP fields, not the immediate HPACK input/output.
    // RFC 9110 sections 5.2/5.3 permit distinct-name reordering and ordered
    // combination. Preserve each name's ordered values, including multiplicity.
    expected.map(_._1).distinct.traverse_ { name =>
      val values = expected.collect { case (`name`, value) => value }
      val observed = actual.collect { case (field, value) if field.toLowerCase == name => value }
      // Only these repeated x/y fixtures are lists of the single members a/b/c.
      // Normalize separator OWS, never arbitrary commas, significant leading/
      // trailing whitespace, or raw-octet/non-list field values.
      if (
        (name == "x" || name == "y") && values.size > 1 &&
        values.forall(value => value == "a" || value == "b" || value == "c")
      )
        Check.equal(
          observed.map(normalizeSeparatorOws).mkString(","),
          values.mkString(","),
        )
      else Check.equal(observed, values)
    }

  private def normalizeSeparatorOws(value: String): String = {
    val normalized = new StringBuilder(value.length)
    var index = 0
    // Each input character is scanned once and copied at most once. Whitespace
    // runs are omitted only when immediately adjacent to a comma; no backtracking
    // or rescanning is needed even for long comma-free runs.
    while (index < value.length)
      if (value.charAt(index) == ' ' || value.charAt(index) == '\t') {
        val start = index
        while (index < value.length && (value.charAt(index) == ' ' || value.charAt(index) == '\t'))
          index += 1
        val afterComma = start > 0 && value.charAt(start - 1) == ','
        val beforeComma = index < value.length && value.charAt(index) == ','
        if (!afterComma && !beforeComma) normalized.append(value.substring(start, index))
      } else {
        normalized.append(value.charAt(index))
        index += 1
      }
    normalized.toString
  }

  private def checkResponse(
      response: ResponseData[IO],
      fields: Vector[(String, String)],
  ): IO[Unit] =
    for {
      body <- response.body.compile.to(Chunk)
      _ <- Check.equal(response.status, 200)
      _ <- Check.equal(body.toByteVector, ByteVector.empty)
      _ <- checkFields(response.headers, fields)
    } yield ()

  /** RFC 9113 sections 4.3, 5.4.1, and 7: compression errors affect the whole connection.
    * Keep target client resources alive until this witness, so cleanup cannot fake rejection.
    * A GOAWAY is best effort; EOF is sufficient. RST_STREAM alone and timeout are insufficient.
    */
  private def requireConnectionError[F[_]: Async](
      session: Session[F],
      streamId: Int,
      before: F[Unit],
  )(implicit peerSettings: Http2PeerSettings[F]): F[Unit] =
    peerSettings.positive(session.peer).flatMap { observation =>
      def checked(
          frame: Http2Wire.Frame
      ): F[Http2RejectionChecks.State] =
        observation.observeControl(frame, session.fromServer).flatTap { _ =>
          frame.frameType match {
            case Http2Wire.GoAway =>
              val code = Http2Wire.readUint32(frame.payload.slice(4, 8))
              verify(
                code == 0L || Set(1L, 2L, 9L).contains(code),
                s"compression failure used error code $code",
              )
            case Http2Wire.RstStream =>
              verify(
                frame.streamId == streamId,
                "invalid RST_STREAM while awaiting a connection error",
              )
            case _ => Async[F].unit
          }
        }

      def loop(remaining: Int, closing: Boolean): F[Unit] =
        observationBound(remaining > 0, "no connection error within the frame bound") *>
          Http2Wire.readFrameOrClose(session.peer).flatMap {
            case None => before
            case Some(frame) =>
              before *> checked(frame).flatMap { _ =>
                frame.frameType match {
                  case Http2Wire.GoAway if Http2Wire.readUint32(frame.payload.slice(4, 8)) != 0L =>
                    for {
                      remainder <- session.peer.readToEnd(65536, allowReset = true)
                      frames <- Async[F]
                        .fromEither(Http2Wire.decodeAll(remainder))
                        .handleErrorWith(invalidTarget[F, Vector[Http2Wire.Frame]])
                      _ <- frames.traverse_(observed => before *> checked(observed))
                      _ <- before
                    } yield ()
                  case Http2Wire.GoAway | Http2Wire.RstStream => loop(remaining - 1, closing)
                  // An HTTP error response is insufficient; require the connection close.
                  case Http2Wire.Headers | Http2Wire.Continuation | Http2Wire.Data =>
                    loop(remaining - 1, closing)
                  case _ =>
                    control(
                      session,
                      frame,
                      (write: F[Unit]) =>
                        if (closing) false.pure[F] else Http2RejectionWrite.reply(write),
                    ).flatMap(written => loop(remaining - 1, closing || !written))
                }
              }
          }
      loop(1024, closing = false)
    }

  private def requireRejected(result: Either[Throwable, Unit]): IO[Unit] =
    result.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]) *> (result match {
      case Left(error)
          if error.isInstanceOf[ProtocolFailure] || error
            .isInstanceOf[WireDecodingFailure] || error.isInstanceOf[WireObservationLimit] =>
        IO.raiseError(error)
      case Left(error: WireDecodingFailure) => IO.raiseError(error)
      case Left(error) => RawPeer.raiseAdapterOrTimeout[IO](error)
      case Right(_) => Check(false, "target client consumed malformed HPACK")
    })

  private def unexpectedRejection(error: Throwable): IO[Unit] =
    RawPeer.raiseAdapterOrTimeout[IO](error) *> {
      if (
        error.isInstanceOf[ProtocolFailure] || error
          .isInstanceOf[WireDecodingFailure] || error.isInstanceOf[WireObservationLimit]
      ) IO.raiseError(error)
      else Check(false, s"target rejected valid HPACK: $error")
    }

  private def invalidTarget[F[_]: Async, A](error: Throwable): F[A] =
    RawPeer.raiseAdapterOrTimeout[F](error) *> {
      if (
        error.isInstanceOf[ProtocolFailure] || error
          .isInstanceOf[WireDecodingFailure] || error.isInstanceOf[WireObservationLimit]
      ) Async[F].raiseError(error)
      else
        Async[F].raiseError(new ProtocolFailure(s"target emitted invalid HPACK or framing: $error"))
    }

  private def incompleteTarget[F[_]: Async, A](error: Throwable): F[A] =
    RawPeer.raiseAdapterOrTimeout[F](error) *> {
      if (eof(error))
        Async[F].raiseError(
          new ProtocolFailure("target closed before completing a valid HPACK exchange")
        )
      else Async[F].raiseError(error)
    }

  private def eof(error: Throwable): Boolean = error match {
    case _: WireReadFailure => true
    case _ => false
  }

  private def observationBound[F[_]: Async](condition: Boolean, detail: String): F[Unit] =
    if (condition) Async[F].unit else Async[F].raiseError(new WireObservationLimit(detail))

}
