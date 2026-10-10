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

import cats.effect.Async
import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all._
import fs2.Chunk
import scodec.bits.ByteVector

import java.util.concurrent.TimeoutException

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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.client.hpack-literal-without-static-name",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "6.2.2", "A four-bit indexed literal name can refer to the static table.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2
    WireCase(
      "h2.server.hpack-literal-without-static-name",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "6.2.2", "A four-bit indexed literal name can refer to the static table.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.client.hpack-literal-never-new-name",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "6.2.3", "A never-indexed literal appends the supplied new name and value.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.server.hpack-literal-never-new-name",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "6.2.3", "A never-indexed literal appends the supplied new name and value.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.client.hpack-literal-never-static-name",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "6.2.3", "A never-indexed literal can use a static indexed name.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.3
    WireCase(
      "h2.server.hpack-literal-never-static-name",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "6.2.3", "A never-indexed literal can use a static indexed name.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.1
    WireCase(
      "h2.client.hpack-literal-incremental-static-name",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "6.2.1", "A six-bit indexed literal name can refer to the static table.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.1
    WireCase(
      "h2.server.hpack-literal-incremental-static-name",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "6.2.1", "A six-bit indexed literal name can refer to the static table.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.client.hpack-static-last-index",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "2.3.3", "Static index 61 remains valid when the dynamic table is empty.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.3
    WireCase(
      "h2.server.hpack-static-last-index",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "2.3.3", "Static index 61 remains valid when the dynamic table is empty.", "MUST"),
    ) { context =>
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
            tableSize(0L) ++ ResponsePrefix ++ hex("008825a849e95ba97d7f0c637573746f6d2d76616c7565")
          )
          _ <- blocks.traverse_ { block =>
            for {
              request <- readRequest(session)
              _ <- writeBlock(session, request.streamId, block, 0)
            } yield ()
          }
        } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          blocks = Vector(
            tableSize(0L) ++ RequestPrefix ++ hex("008825a849e95ba97d7f0c637573746f6d2d76616c7565")
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-huffman-name-value",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "A literal can Huffman encode both name and value.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-huffman-name-value",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "A literal can Huffman encode both name and value.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-huffman-empty",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "A zero-octet Huffman value is an empty string.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-huffman-empty",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "A zero-octet Huffman value is an empty string.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-huffman-no-padding",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "An octet-aligned Huffman symbol requires no padding.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-huffman-no-padding",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "An octet-aligned Huffman symbol requires no padding.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-huffman-seven-bit-padding",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "Seven most-significant EOS bits are permitted as Huffman padding.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-huffman-seven-bit-padding",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "Seven most-significant EOS bits are permitted as Huffman padding.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-string-empty",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "A zero-length raw string produces an empty field value.", "MUST"),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.server.hpack-string-empty",
      Protocol.Http2,
      Role.Server,
      Rfc(7541, "5.2", "A zero-length raw string produces an empty field value.", "MUST"),
    ) { context =>
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
      val expected = Vector(Vector("x" -> "a", "y" -> "b"), Vector("y" -> "b", "x" -> "a"))
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          blocks = Vector(
            tableSize(68L) ++ RequestPrefix ++ hex("40017801614001790162"),
            RequestPrefix ++ hex("bebf"),
          )
          expected = Vector(Vector("x" -> "a", "y" -> "b"), Vector("y" -> "b", "x" -> "a"))
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.2
    WireCase(
      "h2.client.hpack-dynamic-duplicates",
      Protocol.Http2,
      Role.Client,
      Rfc(
        7541,
        "2.3.2",
        "Duplicate dynamic entries are legal and both indexed occurrences remain in header-list order.",
        "MUST NOT",
      ),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.3.2
    WireCase(
      "h2.server.hpack-dynamic-duplicates",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "2.3.2",
        "Duplicate dynamic entries are legal and both indexed occurrences remain in header-list order.",
        "MUST NOT",
      ),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-6.1
    WireCase(
      "h2.client.hpack-reject-index-zero",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "6.1", "Index zero in an indexed representation is a decoding error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("80")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("80")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("be")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("be")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("ff")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("ff")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("7f")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("7f")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("0f")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("0f")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("1f")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("1f")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("ffffffffffffffffffff7f")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("ffffffffffffffffffff7f")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-name-truncated",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "A literal name cannot extend beyond the completed field block.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("000378")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("000378")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("007f")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("007f")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-value-truncated",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "A literal value cannot extend beyond the completed field block.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("0001780261")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("0001780261")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("000178")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("000178")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("0001787f")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("0001787f")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("000178828c")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("000178828c")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("7e0161")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("7e0161")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("0f2f0161")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("0f2f0161")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("1f2f0161")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("1f2f0161")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("00017884ffffffff")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("00017884ffffffff")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("0084ffffffff0161")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("0084ffffffff0161")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-huffman-padding-eight",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "Huffman padding longer than seven bits is a decoding error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("00017881ff")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("00017881ff")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-huffman-name-padding-eight",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "Huffman name padding longer than seven bits is a decoding error.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("0081ff0161")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("0081ff0161")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-huffman-value-padding",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "Huffman value padding must match the most significant EOS bits.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("0001788100")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("0001788100")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2
    WireCase(
      "h2.client.hpack-reject-huffman-name-padding",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.2", "Huffman name padding must match the most significant EOS bits.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("0081000161")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("0081000161")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("bd20")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("bd20")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = tableSize(0L) ++ ResponsePrefix ++ hex("000178016120")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = tableSize(0L) ++ RequestPrefix ++ hex("000178016120")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1
    WireCase(
      "h2.client.hpack-reject-update-integer-truncated",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "5.1", "A saturated dynamic-table size prefix requires a complete integer.", "MUST"),
    ) { context =>
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = hex("3f")
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = hex("3f")
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            limit <- session.peerTableLimit.get
            block = tableSize(limit + 1L)
            _ <- writeBlock(session, request.streamId, block, 0)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          limit <- session.peerTableLimit.get
          block = tableSize(limit + 1L)
          _ <- writeBlock(session, 1, block, 0)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(response => checkResponse(response, Vector("x" -> "a")))
            .handleErrorWith(unexpectedRejection) *>
            driver
              .exchange(EmptyRequest)
              .use(_.body.compile.drain)
              .attempt
              .flatMap(requireRejected) *>
            rejectedOnWire.get
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
            _ <- writeBlock(session, request.streamId, block, 34)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          _ <- writeBlock(session, 1, tableSize(34L) ++ RequestPrefix ++ hex("4001780161"), 34)
          _ <- readResponse(session, 1)
          accepted0 <- server.received
          _ <- checkFields(accepted0.headers, Vector("x" -> "a"))
          block = RequestPrefix ++ hex("bf")
          _ <- writeBlock(session, 3, block, 34)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 3, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(response => checkResponse(response, Vector("x" -> "a")))
            .handleErrorWith(unexpectedRejection) *>
            driver
              .exchange(EmptyRequest)
              .use(_.body.compile.drain)
              .attempt
              .flatMap(requireRejected) *>
            rejectedOnWire.get
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
            _ <- writeBlock(session, request.streamId, block, 34)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          _ <- writeBlock(session, 1, tableSize(34L) ++ RequestPrefix ++ hex("0001780161"), 34)
          _ <- readResponse(session, 1)
          accepted0 <- server.received
          _ <- checkFields(accepted0.headers, Vector("x" -> "a"))
          block = RequestPrefix ++ hex("be")
          _ <- writeBlock(session, 3, block, 34)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 3, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(response => checkResponse(response, Vector("x" -> "a")))
            .handleErrorWith(unexpectedRejection) *>
            driver
              .exchange(EmptyRequest)
              .use(_.body.compile.drain)
              .attempt
              .flatMap(requireRejected) *>
            rejectedOnWire.get
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
            _ <- writeBlock(session, request.streamId, block, 34)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          _ <- writeBlock(session, 1, tableSize(34L) ++ RequestPrefix ++ hex("1001780161"), 34)
          _ <- readResponse(session, 1)
          accepted0 <- server.received
          _ <- checkFields(accepted0.headers, Vector("x" -> "a"))
          block = RequestPrefix ++ hex("be")
          _ <- writeBlock(session, 3, block, 34)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 3, unaccepted)
          _ <- unaccepted
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.4
    WireCase(
      "h2.client.hpack-reject-evicted-oldest-index",
      Protocol.Http2,
      Role.Client,
      Rfc(7541, "4.4", "An entry evicted during insertion is no longer addressable.", "MUST"),
    ) { context =>
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
            driver
              .exchange(EmptyRequest)
              .use(_.body.compile.drain)
              .attempt
              .flatMap(requireRejected) *>
            rejectedOnWire.get
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
            _ <- writeBlock(session, request.streamId, block, 34)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
          _ <- writeBlock(session, 5, block, 34)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 5, unaccepted)
          _ <- unaccepted
        } yield ()
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
            driver
              .exchange(EmptyRequest)
              .use(_.body.compile.drain)
              .attempt
              .flatMap(requireRejected) *>
            rejectedOnWire.get
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
            _ <- writeBlock(session, request.streamId, block, 68)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
          _ <- writeBlock(session, 5, block, 68)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 5, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(response => checkResponse(response, Vector("x" -> "a")))
            .handleErrorWith(unexpectedRejection) *>
            driver
              .exchange(EmptyRequest)
              .use(_.body.compile.drain)
              .attempt
              .flatMap(requireRejected) *>
            rejectedOnWire.get
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
            _ <- writeBlock(session, request.streamId, block, 34)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          _ <- writeBlock(session, 1, tableSize(34L) ++ RequestPrefix ++ hex("4001780161"), 34)
          _ <- readResponse(session, 1)
          accepted0 <- server.received
          _ <- checkFields(accepted0.headers, Vector("x" -> "a"))
          block = hex("203f03") ++ RequestPrefix ++ hex("be")
          _ <- writeBlock(session, 3, block, 34)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 3, unaccepted)
          _ <- unaccepted
        } yield ()
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
            driver
              .exchange(EmptyRequest)
              .use(_.body.compile.drain)
              .attempt
              .flatMap(requireRejected) *>
            rejectedOnWire.get
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
            _ <- writeBlock(session, request.streamId, block, 34)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
          _ <- writeBlock(session, 5, block, 34)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 5, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(response => checkResponse(response, Vector("x" -> "www.example.com")))
            .handleErrorWith(unexpectedRejection) *>
            driver
              .exchange(EmptyRequest)
              .use(_.body.compile.drain)
              .attempt
              .flatMap(requireRejected) *>
            rejectedOnWire.get
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
            _ <- writeBlock(session, request.streamId, block, 47)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
          _ <- writeBlock(session, 3, block, 47)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 3, unaccepted)
          _ <- unaccepted
        } yield ()
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
      Deferred[IO, Unit].flatMap { rejectedOnWire =>
        context.client(Protocol.Http2)(driver =>
          driver
            .exchange(EmptyRequest)
            .use(_.body.compile.drain)
            .attempt
            .flatMap(requireRejected) *>
            rejectedOnWire.get
        ) { peer =>
          for {
            session <- accept(peer)
            request <- readRequest(session)
            block = ResponsePrefix ++ hex("be")
            _ <- writeBlock(session, request.streamId, block, 4096)
            _ <- requireConnectionError(session, request.streamId, IO.unit)
            _ <- rejectedOnWire.complete(()).void
          } yield ()
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
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          session <- connect(peer)
          block = RequestPrefix ++ hex("be")
          _ <- writeBlock(session, 1, block, 4096)
          unaccepted = server.pollReceived
            .flatMap(value => Check(value.isEmpty, "malformed HPACK reached the application"))
          _ <- requireConnectionError(session, 1, unaccepted)
          _ <- unaccepted
        } yield ()
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
        "Encoded fields preserve the supplied field order and remain independently decodable across streams; any valid indexing strategy is permitted.",
        "MUST",
      ),
    ) { context =>
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
    },
    // https://www.rfc-editor.org/rfc/rfc7541.html#section-2.1
    WireCase(
      "h2.server.hpack-encoder-stateful-roundtrip",
      Protocol.Http2,
      Role.Server,
      Rfc(
        7541,
        "2.1",
        "Encoded fields preserve the supplied field order and remain independently decodable across streams; any valid indexing strategy is permitted.",
        "MUST",
      ),
    ) { context =>
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
      val fields = Vector("x-first" -> "one", "x-second" -> "two", "x-third" -> "three")
      val request = EmptyRequest.copy(headers = EmptyRequest.headers ++ fields)
      Deferred[IO, Unit].flatMap { settingsApplied =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(request).use(response => checkResponse(response, Vector.empty)) *>
            settingsApplied.get *>
            driver.exchange(request).use(response => checkResponse(response, Vector.empty))
        ) { peer =>
          for {
            session <- accept(peer)
            first <- readRequest(session)
            _ <- checkFields(first.fields, fields)
            _ <- requireCapacityChange[IO](first.block)
            _ <- writeBlock(session, first.streamId, tableSize(0L) ++ ResponsePrefix, 0)
            _ <- awaitAcknowledgments(session, 1)
            _ <- peer.write(Http2Wire.encode(Http2Wire.settings(Vector(1 -> 0L))))
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
      val fields = Vector("x-first" -> "one", "x-second" -> "two", "x-third" -> "three")
      context
        .server(Protocol.Http2, ServerScenario.Reply(headers = fields, body = ByteVector.empty))
        .use { case (peer, _) =>
          for {
            session <- connect(peer)
            _ <- writeBlock(session, 1, tableSize(0L) ++ RequestPrefix, 0)
            first <- readResponse(session, 1)
            _ <- checkFields(first.head.fields, fields)
            _ <- requireCapacityChange[IO](first.head.block)
            _ <- awaitAcknowledgments(session, 1)
            _ <- peer.write(Http2Wire.encode(Http2Wire.settings(Vector(1 -> 0L))))
            _ <- awaitAcknowledgments(session, 2)
            decoder <- HpackWire.decoder[IO](maxTableSize = 0)
            constrained = session.copy(decoder = decoder)
            _ <- writeBlock(constrained, 3, tableSize(0L) ++ RequestPrefix, 0)
            second <- readResponse(constrained, 3)
            updates = leadingSizes(second.head.block)
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
      val fields = Vector("x-first" -> "one", "x-second" -> "two", "x-third" -> "three")
      val request = EmptyRequest.copy(headers = EmptyRequest.headers ++ fields)
      Deferred[IO, Unit].flatMap { settingsApplied =>
        context.client(Protocol.Http2)(driver =>
          driver.exchange(request).use(response => checkResponse(response, Vector.empty)) *>
            settingsApplied.get *>
            driver.exchange(request).use(response => checkResponse(response, Vector.empty))
        ) { peer =>
          for {
            session <- accept(peer)
            first <- readRequest(session)
            _ <- checkFields(first.fields, fields)
            _ <- requireCapacityChange[IO](first.block)
            _ <- writeBlock(session, first.streamId, tableSize(0L) ++ ResponsePrefix, 0)
            _ <- awaitAcknowledgments(session, 1)
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.settings(Vector(1 -> 0L))) ++ Http2Wire.encode(
                Http2Wire.settings(Vector(1 -> 128L))
              )
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
      val fields = Vector("x-first" -> "one", "x-second" -> "two", "x-third" -> "three")
      context
        .server(Protocol.Http2, ServerScenario.Reply(headers = fields, body = ByteVector.empty))
        .use { case (peer, _) =>
          for {
            session <- connect(peer)
            _ <- writeBlock(session, 1, tableSize(0L) ++ RequestPrefix, 0)
            first <- readResponse(session, 1)
            _ <- checkFields(first.head.fields, fields)
            _ <- requireCapacityChange[IO](first.head.block)
            _ <- awaitAcknowledgments(session, 1)
            _ <- peer.write(
              Http2Wire.encode(Http2Wire.settings(Vector(1 -> 0L))) ++ Http2Wire
                .encode(Http2Wire.settings(Vector(1 -> 128L)))
            )
            _ <- awaitAcknowledgments(session, 3)
            decoder <- HpackWire.decoder[IO](maxTableSize = 128)
            constrained = session.copy(decoder = decoder)
            _ <- writeBlock(constrained, 3, tableSize(0L) ++ RequestPrefix, 0)
            second <- readResponse(constrained, 3)
            updates = leadingSizes(second.head.block)
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
    },
  )

  private final case class Session[F[_]](
      peer: RawPeer[F],
      decoder: HpackWire.Decoder[F],
      peerTableLimit: Ref[F, Long],
      acknowledgments: Ref[F, Int],
  )

  private final case class Head(streamId: Int, fields: Vector[(String, String)], block: ByteVector)
  private final case class Message(head: Head, body: ByteVector)

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

  private def verify[F[_]: Async](condition: Boolean, detail: => String): F[Unit] =
    if (condition) Async[F].unit else Async[F].raiseError(new ProtocolFailure(detail))

  private def session[F[_]: Async](peer: RawPeer[F], decoderLimit: Int): F[Session[F]] =
    (HpackWire.decoder[F](maxTableSize = decoderLimit), Ref.of[F, Long](4096L), Ref.of[F, Int](0))
      .mapN(Session(peer, _, _, _))

  /** Raw client connecting to a target server. Each direction owns a separate compression table. */
  private def connect[F[_]: Async](peer: RawPeer[F], decoderLimit: Int = 4096): F[Session[F]] =
    for {
      result <- session(peer, decoderLimit)
      _ <- peer.write(
        Http2Wire.clientPreface ++ Http2Wire.encode(
          Http2Wire.settings(Vector(1 -> decoderLimit.toLong))
        )
      )
      initial <- Http2Wire.readFrame(peer)
      _ <- verify(
        initial.frameType == Http2Wire.Settings && !initial.hasFlag(Http2Wire.Ack),
        "missing initial target SETTINGS",
      )
      _ <- settings(result, initial)
    } yield result

  /** Raw server accepting a target client; initial request bytes are retained for independent decoding. */
  private def accept[F[_]: Async](peer: RawPeer[F]): F[Session[F]] =
    for {
      result <- session(peer, 4096)
      preface <- peer.readExact(Http2Wire.clientPreface.size.toInt)
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

  private def settings[F[_]: Async](session: Session[F], frame: Http2Wire.Frame): F[Unit] =
    verify(frame.streamId == 0 && frame.payload.size % 6L == 0L, "malformed target SETTINGS") *>
      (if (frame.hasFlag(Http2Wire.Ack))
         verify(frame.payload.isEmpty, "SETTINGS ACK contains a payload") *> session.acknowledgments
           .update(_ + 1)
       else
         (0 until (frame.payload.size / 6L).toInt).toVector.traverse_ { index =>
           val entry = frame.payload.slice(index.toLong * 6L, index.toLong * 6L + 6L)
           val key = ((entry(0) & 0xff) << 8) | (entry(1) & 0xff)
           val value = Http2Wire.readUint32(entry.drop(2))
           key match {
             case 1 => session.peerTableLimit.set(value)
             case 2 => verify(value <= 1L, "invalid target ENABLE_PUSH")
             case 4 => verify(value <= 0x7fffffffL, "invalid target INITIAL_WINDOW_SIZE")
             case 5 =>
               verify(value >= 16384L && value <= 0xffffffL, "invalid target MAX_FRAME_SIZE")
             case _ => Async[F].unit
           }
         } *> Http2Wire.writeFrame(session.peer, Http2Wire.settings(ack = true)))

  private def control[F[_]: Async](session: Session[F], frame: Http2Wire.Frame): F[Unit] =
    frame.frameType match {
      case Http2Wire.Settings => settings(session, frame)
      case Http2Wire.Ping =>
        verify(frame.streamId == 0 && frame.payload.size == 8L, "invalid target PING") *>
          (if (frame.hasFlag(Http2Wire.Ack)) Async[F].unit
           else Http2Wire.writeFrame(session.peer, frame.copy(flags = Http2Wire.Ack)))
      case Http2Wire.WindowUpdate =>
        verify(frame.payload.size == 4L, "invalid target WINDOW_UPDATE length") *>
          verify(
            (Http2Wire.readUint32(frame.payload) & 0x7fffffffL) != 0L,
            "zero target WINDOW_UPDATE",
          )
      case Http2Wire.Priority =>
        verify(frame.streamId != 0 && frame.payload.size == 5L, "invalid target PRIORITY") *>
          verify(
            (Http2Wire.readUint32(frame.payload.take(4)) & 0x7fffffffL) != frame.streamId.toLong,
            "self-dependent target PRIORITY",
          )
      case kind if kind > Http2Wire.Continuation => Async[F].unit
      case _ => verify(false, s"unexpected target frame: $frame")
    }

  private def next[F[_]: Async](session: Session[F]): F[Http2Wire.Frame] = {
    def loop(remaining: Int): F[Http2Wire.Frame] =
      verify(remaining > 0, "too many control frames before HPACK traffic") *>
        Http2Wire.readFrame(session.peer).flatMap { frame =>
          frame.frameType match {
            case Http2Wire.Headers | Http2Wire.Data | Http2Wire.RstStream | Http2Wire.GoAway =>
              frame.pure[F]
            case _ => control(session, frame) *> loop(remaining - 1)
          }
        }
    loop(1024)
  }

  private def awaitAcknowledgments[F[_]: Async](session: Session[F], count: Int): F[Unit] = {
    def loop(remaining: Int): F[Unit] =
      session.acknowledgments.get.flatMap { received =>
        if (received >= count) Async[F].unit
        else
          verify(remaining > 0, "SETTINGS acknowledgment frame bound exceeded") *>
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

  private def requireCapacityChange[F[_]: Async](firstBlock: ByteVector): F[Unit] =
    if (leadingSizes(firstBlock).lastOption.getOrElse(4096L) > 0L) Async[F].unit
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
  ): F[Unit] =
    session.peerTableLimit.get.flatMap { limit =>
      // This is a fixture capability precondition, not a conformance verdict or skipped assertion.
      val ready =
        if (capacity.toLong <= limit) Async[F].unit
        else
          Async[F].raiseError[Unit](
            new IllegalStateException(
              s"HPACK case needs peer table capacity $capacity, but SETTINGS_HEADER_TABLE_SIZE is $limit; scenario was not exercised"
            )
          )
      ready *> verify(
        block.size <= 16384L,
        "fixture HPACK block exceeds the baseline frame size",
      ) *>
        Http2Wire.writeFrame(
          session.peer,
          Http2Wire.Frame(
            Http2Wire.Headers,
            Http2Wire.EndHeaders | Http2Wire.EndStream,
            streamId,
            block,
          ),
        )
    }

  private def readHead[F[_]: Async](session: Session[F], first: Http2Wire.Frame): F[Head] = {
    def gather(bytes: ByteVector, frame: Http2Wire.Frame, remaining: Int): F[ByteVector] =
      verify(
        bytes.size <= 1024L * 1024L && remaining > 0,
        "target header block exceeds observation bound",
      ) *>
        (if (frame.hasFlag(Http2Wire.EndHeaders)) bytes.pure[F]
         else
           Http2Wire.readFrame(session.peer).flatMap { continuation =>
             verify(
               continuation.frameType == Http2Wire.Continuation && continuation.streamId == first.streamId,
               "target interrupted a field block",
             ) *>
               gather(bytes ++ continuation.payload, continuation, remaining - 1)
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
            require(
              (Http2Wire.readUint32(bytes.take(4)) & 0x7fffffffL) != first.streamId.toLong,
              "self-dependent target HEADERS",
            )
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

  private def readBody[F[_]: Async](session: Session[F], first: Http2Wire.Frame): F[ByteVector] = {
    def loop(bytes: ByteVector, remaining: Int): F[ByteVector] =
      verify(remaining > 0 && bytes.size <= 65536L, "target content exceeds observation bound") *>
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
                    val all = bytes ++ data
                    if (frame.hasFlag(Http2Wire.EndStream)) all.pure[F]
                    else loop(all, remaining - 1)
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
    else loop(ByteVector.empty, 1024)
  }

  private def readRequest[F[_]: Async](session: Session[F]): F[Head] =
    (for {
      first <- next(session)
      _ <- verify(first.streamId > 0 && (first.streamId & 1) == 1, "invalid target request stream")
      head <- readHead(session, first)
      _ <- readBody(session, first)
    } yield head).handleErrorWith(incompleteTarget[F, Head])

  private def readResponse[F[_]: Async](session: Session[F], streamId: Int): F[Message] =
    (for {
      first <- next(session)
      _ <- verify(first.streamId == streamId, "target response used an unexpected stream")
      head <- readHead(session, first)
      _ <- verify(
        head.fields.collect { case (":status", value) => value } == Vector("200"),
        "valid HPACK request did not receive status 200",
      )
      body <- readBody(session, first)
    } yield Message(head, body)).handleErrorWith(incompleteTarget[F, Message])

  private def checkFields(
      actual: Vector[(String, String)],
      expected: Vector[(String, String)],
  ): IO[Unit] = {
    val names = expected.iterator.map(_._1).toSet
    Check.equal(
      actual.filter(field => names.contains(field._1.toLowerCase)).map { case (name, value) =>
        name.toLowerCase -> value
      },
      expected,
    )
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
  ): F[Unit] = {
    def loop(remaining: Int): F[Unit] =
      verify(remaining > 0, "no connection error within the frame bound") *>
        Http2Wire.readFrameOrClose(session.peer).flatMap {
          case None => before
          case Some(frame) =>
            before *> (frame.frameType match {
              case Http2Wire.GoAway =>
                for {
                  _ <- verify(
                    frame.streamId == 0 && frame.payload.size >= 8L,
                    "invalid GOAWAY for compression error",
                  )
                  code = Http2Wire.readUint32(frame.payload.slice(4, 8))
                  _ <- verify(
                    Set(1L, 2L, 9L).contains(code),
                    s"compression failure used error code $code",
                  )
                  remainder <- session.peer.readToEnd(65536, allowReset = true)
                  _ <- Async[F]
                    .fromEither(Http2Wire.decodeAll(remainder))
                    .handleErrorWith(invalidTarget[F, Vector[Http2Wire.Frame]])
                  _ <- before
                } yield ()
              case Http2Wire.RstStream =>
                verify(
                  frame.streamId == streamId && frame.payload.size == 4L,
                  "invalid RST_STREAM while awaiting a connection error",
                ) *>
                  loop(remaining - 1)
              // An HTTP error response is not enough; continue until the mandatory connection close.
              case Http2Wire.Headers | Http2Wire.Continuation | Http2Wire.Data =>
                loop(remaining - 1)
              case _ => control(session, frame) *> loop(remaining - 1)
            })
        }
    loop(1024)
  }

  private def requireRejected(result: Either[Throwable, Unit]): IO[Unit] = result match {
    case Left(error) if timeout(error) || error.isInstanceOf[ProtocolFailure] =>
      IO.raiseError(error)
    case Left(_) => IO.unit
    case Right(_) => Check(false, "target client consumed malformed HPACK")
  }

  private def unexpectedRejection(error: Throwable): IO[Unit] =
    if (timeout(error) || error.isInstanceOf[ProtocolFailure]) IO.raiseError(error)
    else Check(false, s"target rejected valid HPACK: $error")

  private def invalidTarget[F[_]: Async, A](error: Throwable): F[A] =
    if (timeout(error) || error.isInstanceOf[ProtocolFailure]) Async[F].raiseError(error)
    else
      Async[F].raiseError(new ProtocolFailure(s"target emitted invalid HPACK or framing: $error"))

  private def incompleteTarget[F[_]: Async, A](error: Throwable): F[A] =
    if (eof(error))
      Async[F].raiseError(
        new ProtocolFailure("target closed before completing a valid HPACK exchange")
      )
    else Async[F].raiseError(error)

  private def eof(error: Throwable): Boolean = error match {
    case _: IllegalArgumentException =>
      Option(error.getMessage).exists(_.startsWith("unexpected EOF"))
    case _ => false
  }

  private def timeout(error: Throwable): Boolean = {
    @annotation.tailrec
    def loop(current: Throwable, remaining: Int): Boolean =
      if (current == null || remaining == 0) false
      else if (current.isInstanceOf[TimeoutException]) true
      else loop(current.getCause, remaining - 1)
    loop(error, 16)
  }
}
