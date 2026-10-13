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

/** RFC-derived HTTP/1 body framing, chunk coding, and trailer boundaries. */
object Http1FramingCases {
  val all: Vector[WireCase] = Vector(
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.content-length-zero",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A valid decimal Content-Length, including leading zeroes, delimits exactly that many octets.",
        "MUST",
      ),
    ) { context =>
      clientResponse(
        context,
        Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"),
        ByteVector.empty,
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.content-length-zero",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "A valid decimal request Content-Length, including leading zeroes, determines the content length.",
        "MUST",
      ),
    ) { context =>
      serverEcho(
        context,
        Http1Wire.ascii("POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 0\r\n\r\n"),
        ByteVector.empty,
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.content-length-leading-zeroes",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A valid decimal Content-Length, including leading zeroes, delimits exactly that many octets.",
        "MUST",
      ),
    ) { context =>
      clientResponse(
        context,
        Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 0003\r\n\r\nabc"),
        Http1Wire.ascii("abc"),
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.content-length-leading-zeroes",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "A valid decimal request Content-Length, including leading zeroes, determines the content length.",
        "MUST",
      ),
    ) { context =>
      serverEcho(
        context,
        Http1Wire.ascii(
          "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 0003\r\n\r\nabc"
        ),
        Http1Wire.ascii("abc"),
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.content-length-wide-leading-zeroes",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A valid decimal Content-Length, including leading zeroes, delimits exactly that many octets.",
        "MUST",
      ),
    ) { context =>
      clientResponse(
        context,
        Http1Wire.ascii(
          "HTTP/1.1 200 OK\r\nContent-Length: 00000000000000000000000000000003\r\n\r\nabc"
        ),
        Http1Wire.ascii("abc"),
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.content-length-wide-leading-zeroes",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "A valid decimal request Content-Length, including leading zeroes, determines the content length.",
        "MUST",
      ),
    ) { context =>
      serverEcho(
        context,
        Http1Wire.ascii(
          "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 00000000000000000000000000000003\r\n\r\nabc"
        ),
        Http1Wire.ascii("abc"),
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.content-length-binary-octets",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "6.3", "Content-Length counts octets, including NUL and non-ASCII bytes.", "MUST"),
    ) { context =>
      clientResponse(
        context,
        Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\n") ++ ByteVector.fromValidHex(
          "00ff80fe"
        ),
        ByteVector.fromValidHex("00ff80fe"),
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.content-length-binary-octets",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "6.3", "Request Content-Length counts octets rather than characters.", "MUST"),
    ) { context =>
      serverEcho(
        context,
        Http1Wire.ascii(
          "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 4\r\n\r\n"
        ) ++ ByteVector.fromValidHex("00ff80fe"),
        ByteVector.fromValidHex("00ff80fe"),
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.unframed-post-is-empty",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "A request without Content-Length or Transfer-Encoding has zero body octets.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(Http1Wire.ascii("POST /framing HTTP/1.1\r\nHost: wire.test\r\n\r\n"))
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(response.body, ByteVector.empty)
          _ <- Check.equal(received.method, "POST")
          _ <- Check.equal(received.path, "/framing")
          _ <- Check.equal(received.body, ByteVector.empty)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.close-delimited-empty",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A response with no declared body length ends at the server connection close.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
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
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\n\r\n") ++ Http1Wire.ascii(""))
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.close-delimited-binary",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A response with no declared body length ends at the server connection close.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, ByteVector.fromValidHex("6100ff0d0a62"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii("HTTP/1.1 200 OK\r\n\r\n") ++ ByteVector.fromValidHex("6100ff0d0a62")
          )
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.head-content-length-is-metadata",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "HEAD, 204, and 304 response bodies end at the header boundary regardless of framing metadata.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(method = "HEAD", headers = Vector("Content-Length" -> "0")))
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
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "HEAD")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\n"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.head-transfer-encoding-is-metadata",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "HEAD, 204, and 304 response bodies end at the header boundary regardless of framing metadata.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(method = "HEAD", headers = Vector("Content-Length" -> "0")))
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
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "HEAD")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.no-content-ends-at-headers",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "HEAD, 204, and 304 response bodies end at the header boundary regardless of framing metadata.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
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
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(Http1Wire.ascii("HTTP/1.1 204 No Content\r\n\r\n"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.not-modified-content-length-is-metadata",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "HEAD, 204, and 304 response bodies end at the header boundary regardless of framing metadata.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
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
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(Http1Wire.ascii("HTTP/1.1 304 Not Modified\r\nContent-Length: 3\r\n\r\n"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.not-modified-transfer-encoding-is-metadata",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "HEAD, 204, and 304 response bodies end at the header boundary regardless of framing metadata.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
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
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii("HTTP/1.1 304 Not Modified\r\nTransfer-Encoding: chunked\r\n\r\n")
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.informational-does-not-use-close-delimitation",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "An API-visible informational response is bodyless; a visible final response retains its declared content.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              // ResponseData may expose a bodyless informational response.
              // Final-response continuation is not observable through this API in that branch.
              _ <-
                if (response.status == 103)
                  Check.equal(body.toByteVector, ByteVector.empty)
                else
                  Check.equal(response.status, 200) *>
                    Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 103 Early Hints\r\nLink: </wire>; rel=preload\r\n\r\nHTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabc"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.multiple-informational-boundaries",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "The visible informational response is bodyless, or the visible final response retains its declared content.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              // ResponseData may expose a bodyless informational response.
              // Final-response continuation is not observable through this API in that branch.
              _ <-
                if (response.status == 100 || response.status == 103)
                  Check.equal(body.toByteVector, ByteVector.empty)
                else
                  Check.equal(response.status, 200) *>
                    Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 103 Early Hints\r\n\r\nHTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabc"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.reject-content-length-empty",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "An invalid Content-Length without Transfer-Encoding is an unrecoverable response framing error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          // Only the target exchange and body consumption are attempted.
          driver
            .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
            .use(_.body.compile.drain)
            .attempt
            .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
            .flatMap {
              case Left(error) =>
                rejected(error)
              case Right(_) =>
                Check(false, "the target consumed the deliberately invalid framing")
            } *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- Check.equal(request.body, ByteVector.empty)
            _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: \r\n\r\nabc"))
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.reject-content-length-empty",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "An invalid request Content-Length requires a 400 response followed by connection closure.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- peer
            .writeAscii("POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: \r\n\r\nabc")
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.reject-content-length-negative",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "An invalid Content-Length without Transfer-Encoding is an unrecoverable response framing error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          // Only the target exchange and body consumption are attempted.
          driver
            .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
            .use(_.body.compile.drain)
            .attempt
            .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
            .flatMap {
              case Left(error) =>
                rejected(error)
              case Right(_) =>
                Check(false, "the target consumed the deliberately invalid framing")
            } *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- Check.equal(request.body, ByteVector.empty)
            _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: -1\r\n\r\nabc"))
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.reject-content-length-negative",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "An invalid request Content-Length requires a 400 response followed by connection closure.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: -1\r\n\r\nabc"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.reject-content-length-explicit-plus",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "An invalid Content-Length without Transfer-Encoding is an unrecoverable response framing error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          // Only the target exchange and body consumption are attempted.
          driver
            .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
            .use(_.body.compile.drain)
            .attempt
            .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
            .flatMap {
              case Left(error) =>
                rejected(error)
              case Right(_) =>
                Check(false, "the target consumed the deliberately invalid framing")
            } *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- Check.equal(request.body, ByteVector.empty)
            _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: +3\r\n\r\nabc"))
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.reject-content-length-explicit-plus",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "An invalid request Content-Length requires a 400 response followed by connection closure.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: +3\r\n\r\nabc"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.reject-content-length-hexadecimal",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "An invalid Content-Length without Transfer-Encoding is an unrecoverable response framing error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          // Only the target exchange and body consumption are attempted.
          driver
            .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
            .use(_.body.compile.drain)
            .attempt
            .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
            .flatMap {
              case Left(error) =>
                rejected(error)
              case Right(_) =>
                Check(false, "the target consumed the deliberately invalid framing")
            } *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- Check.equal(request.body, ByteVector.empty)
            _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 0x3\r\n\r\nabc"))
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.reject-content-length-hexadecimal",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "An invalid request Content-Length requires a 400 response followed by connection closure.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 0x3\r\n\r\nabc"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.reject-content-length-fractional",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "An invalid Content-Length without Transfer-Encoding is an unrecoverable response framing error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          // Only the target exchange and body consumption are attempted.
          driver
            .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
            .use(_.body.compile.drain)
            .attempt
            .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
            .flatMap {
              case Left(error) =>
                rejected(error)
              case Right(_) =>
                Check(false, "the target consumed the deliberately invalid framing")
            } *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- Check.equal(request.body, ByteVector.empty)
            _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 3.0\r\n\r\nabc"))
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.reject-content-length-fractional",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "An invalid request Content-Length requires a 400 response followed by connection closure.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 3.0\r\n\r\nabc"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.reject-content-length-embedded-space",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "An invalid Content-Length without Transfer-Encoding is an unrecoverable response framing error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          // Only the target exchange and body consumption are attempted.
          driver
            .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
            .use(_.body.compile.drain)
            .attempt
            .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
            .flatMap {
              case Left(error) =>
                rejected(error)
              case Right(_) =>
                Check(false, "the target consumed the deliberately invalid framing")
            } *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- Check.equal(request.body, ByteVector.empty)
            _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 3 0\r\n\r\nabc"))
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.reject-content-length-embedded-space",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "An invalid request Content-Length requires a 400 response followed by connection closure.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 3 0\r\n\r\nabc"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.reject-content-length-suffix",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "An invalid Content-Length without Transfer-Encoding is an unrecoverable response framing error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          // Only the target exchange and body consumption are attempted.
          driver
            .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
            .use(_.body.compile.drain)
            .attempt
            .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
            .flatMap {
              case Left(error) =>
                rejected(error)
              case Right(_) =>
                Check(false, "the target consumed the deliberately invalid framing")
            } *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- Check.equal(request.body, ByteVector.empty)
            _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 3x\r\n\r\nabc"))
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.reject-content-length-suffix",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "An invalid request Content-Length requires a 400 response followed by connection closure.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 3x\r\n\r\nabc"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.reject-content-length-list-invalid-member",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "An invalid Content-Length without Transfer-Encoding is an unrecoverable response framing error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          // Only the target exchange and body consumption are attempted.
          driver
            .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
            .use(_.body.compile.drain)
            .attempt
            .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
            .flatMap {
              case Left(error) =>
                rejected(error)
              case Right(_) =>
                Check(false, "the target consumed the deliberately invalid framing")
            } *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- Check.equal(request.body, ByteVector.empty)
            _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 3, +3\r\n\r\nabc"))
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.reject-content-length-list-invalid-member",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "An invalid request Content-Length requires a 400 response followed by connection closure.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 3, +3\r\n\r\nabc"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.reject-content-length-list-conflict",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "An invalid Content-Length without Transfer-Encoding is an unrecoverable response framing error.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          // Only the target exchange and body consumption are attempted.
          driver
            .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
            .use(_.body.compile.drain)
            .attempt
            .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
            .flatMap {
              case Left(error) =>
                rejected(error)
              case Right(_) =>
                Check(false, "the target consumed the deliberately invalid framing")
            } *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- Check.equal(request.body, ByteVector.empty)
            _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 2, 3\r\n\r\nabc"))
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.reject-content-length-list-conflict",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "An invalid request Content-Length requires a 400 response followed by connection closure.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 2, 3\r\n\r\nabc"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.reject-conflicting-content-length",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "Different Content-Length field values require a 400 response followed by connection closure.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 2\r\nContent-Length: 3\r\n\r\nabc"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-8.6
    WireCase(
      "h1.client.content-length-prevents-integer-wraparound",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9110,
        "8.6",
        "A Content-Length exceeding machine integer ranges must not wrap to a short complete message.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        // Only the target exchange and body consumption are attempted.
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(_.body.compile.drain)
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right(_) =>
              Check(false, "the target consumed the deliberately invalid framing")
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 18446744073709551619\r\n\r\nabc")
          )
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-8.6
    WireCase(
      "h1.server.content-length-prevents-integer-wraparound",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9110,
        "8.6",
        "A request Content-Length exceeding machine integer ranges must not wrap to a short complete message.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 18446744073709551619\r\n\r\nabc"
          )
          _ <- peer.endOfOutput
          status <- responseHeadOrEof(peer)
          _ <- Check(
            status.forall(value => value >= 400 && value <= 599),
            s"invalid framing was answered successfully: $status",
          )
          observed <- server.pollReceived
          _ <- Check(
            observed.isEmpty,
            s"EchoBody reported a complete request after an overflowing length: $observed",
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-8.6
    WireCase(
      "h1.client.equal-content-length-fields",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9110,
        "8.6",
        "Repeated identical decimal Content-Length values may be rejected or replaced with their single value.",
        "MAY",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(response =>
            response.body.compile.to(Chunk).map(body => (response.status, body.toByteVector))
          )
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right((status, body)) =>
              Check.equal(status, 200) *> Check.equal(body, Http1Wire.ascii("abc"))
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 3\r\nContent-Length: 3\r\n\r\nabc")
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-8.6
    WireCase(
      "h1.server.equal-content-length-fields",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9110,
        "8.6",
        "Repeated identical decimal Content-Length values may be rejected or replaced with their single value.",
        "MAY",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 3\r\nContent-Length: 3\r\n\r\nabc"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <-
            if (response.status == 400)
              expectClosedAfterResponse(peer)
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-8.6
    WireCase(
      "h1.client.equal-content-length-list",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9110,
        "8.6",
        "Repeated identical decimal Content-Length values may be rejected or replaced with their single value.",
        "MAY",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(response =>
            response.body.compile.to(Chunk).map(body => (response.status, body.toByteVector))
          )
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right((status, body)) =>
              Check.equal(status, 200) *> Check.equal(body, Http1Wire.ascii("abc"))
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 3, 3\r\n\r\nabc"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-8.6
    WireCase(
      "h1.server.equal-content-length-list",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9110,
        "8.6",
        "Repeated identical decimal Content-Length values may be rejected or replaced with their single value.",
        "MAY",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 3, 3\r\n\r\nabc"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <-
            if (response.status == 400)
              expectClosedAfterResponse(peer)
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-8.6
    WireCase(
      "h1.client.equal-content-length-numeric-values",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9110,
        "8.6",
        "Repeated identical decimal Content-Length values may be rejected or replaced with their single value.",
        "MAY",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(response =>
            response.body.compile.to(Chunk).map(body => (response.status, body.toByteVector))
          )
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right((status, body)) =>
              Check.equal(status, 200) *> Check.equal(body, Http1Wire.ascii("abc"))
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 3, 03\r\n\r\nabc"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-8.6
    WireCase(
      "h1.server.equal-content-length-numeric-values",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9110,
        "8.6",
        "Repeated identical decimal Content-Length values may be rejected or replaced with their single value.",
        "MAY",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 3, 03\r\n\r\nabc"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <-
            if (response.status == 400)
              expectClosedAfterResponse(peer)
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.transfer-encoding-overrides-content-length",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A response with Transfer-Encoding and Content-Length may be rejected; if processed, Transfer-Encoding controls framing.",
        "MAY",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(response =>
            response.body.compile.to(Chunk).map(body => (response.status, body.toByteVector))
          )
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right((status, body)) =>
              Check.equal(status, 200) *> Check.equal(body, Http1Wire.ascii("abc"))
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Length: 1\r\n\r\n3\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.1
    WireCase(
      "h1.server.transfer-encoding-overrides-content-length-and-closes",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.1",
        "A request with Transfer-Encoding and Content-Length may be rejected or decoded using Transfer-Encoding, and the connection must close.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\nContent-Length: 1\r\n\r\n3\r\nabc\r\n0\r\n\r\n"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <-
            if (response.status >= 400 && response.status <= 599) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.reject-nonchunked-request-transfer-coding",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "A request whose final transfer coding is not chunked requires a 400 response and connection closure.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          // Deterministic valid gzip of abc; only request framing is invalid.
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: gzip\r\n\r\n"
            ) ++
              ByteVector.fromValidHex("1f8b08000000000002ff4b4c4a0600c241243503000000")
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.reject-chunked-not-final-in-request",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "A request whose final transfer coding is not chunked requires a 400 response and connection closure.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          // Deterministic valid gzip of 3\r\nabc\r\n0\r\n\r\n; only request framing is invalid.
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked, gzip\r\n\r\n"
            ) ++
              ByteVector
                .fromValidHex("1f8b08000000000002ff33e6e54a4c4ae6e532e0e5e2e5020045cff8100d000000")
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.1
    WireCase(
      "h1.server.unsupported-transfer-coding",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.1",
        "An unknown request transfer coding should produce 501; a length-required policy may instead produce 411.",
        "SHOULD",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, _) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: wire-unknown, chunked\r\n\r\n3\r\nabc\r\n0\r\n\r\n"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <- Check(
            response.status == 501 || response.status == 411,
            s"expected unsupported coding or length-required policy, observed ${response.status}",
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.client.chunked-empty",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "The zero chunk and final empty line encode an empty chunked body.", "MUST"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii(""))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n")
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.server.chunked-empty",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "The zero chunk and final empty line encode an empty chunked body.", "MUST"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii(""))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii(""))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.client.chunked-multiple-terminal-zeroes",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "The last-chunk grammar permits one or more zero digits.", "MUST"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii(""))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n000\r\n\r\n")
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.server.chunked-multiple-terminal-zeroes",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "The last-chunk grammar permits one or more zero digits.", "MUST"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n000\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii(""))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii(""))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.client.chunked-leading-zeroes",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "A hexadecimal chunk size may contain leading zeroes.", "MUST"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n0003\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.server.chunked-leading-zeroes",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "A hexadecimal chunk size may contain leading zeroes.", "MUST"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n0003\r\nabc\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.client.chunked-lowercase-hex",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "Lowercase hexadecimal digits are valid chunk-size digits.", "MUST"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abcdefghij"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\na\r\nabcdefghij\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.server.chunked-lowercase-hex",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "Lowercase hexadecimal digits are valid chunk-size digits.", "MUST"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\na\r\nabcdefghij\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abcdefghij"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("abcdefghij"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.client.chunked-uppercase-hex",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "Uppercase hexadecimal digits are valid chunk-size digits.", "MUST"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abcdefghij"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nA\r\nabcdefghij\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.server.chunked-uppercase-hex",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "Uppercase hexadecimal digits are valid chunk-size digits.", "MUST"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\nA\r\nabcdefghij\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abcdefghij"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("abcdefghij"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.client.chunked-multiple-chunks",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "Decoded content is the ordered concatenation of all chunk data.", "MUST"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n1\r\na\r\n2\r\nbc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.server.chunked-multiple-chunks",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "Decoded content is the ordered concatenation of all chunk data.", "MUST"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n1\r\na\r\n2\r\nbc\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.client.chunked-data-contains-crlf",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "Chunk data is counted by size and can contain CRLF octets.", "MUST"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("a\r\nbc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\na\r\nbc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.server.chunked-data-contains-crlf",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "Chunk data is counted by size and can contain CRLF octets.", "MUST"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n5\r\na\r\nbc\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("a\r\nbc"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("a\r\nbc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.client.chunk-extension-bare",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1.1", "Recipients ignore an unrecognized chunk extension with no value.", "MUST"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3;wire-flag\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.server.chunk-extension-bare",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1.1", "Recipients ignore an unrecognized chunk extension with no value.", "MUST"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3;wire-flag\r\nabc\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.client.chunk-extension-token",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "7.1.1",
        "Recipients ignore an unrecognized chunk extension with a token value.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3;wire-token=alpha\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.server.chunk-extension-token",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "7.1.1",
        "Recipients ignore an unrecognized chunk extension with a token value.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3;wire-token=alpha\r\nabc\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.client.chunk-extension-quoted",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "7.1.1",
        "A quoted chunk extension value can contain semicolon and equals delimiters.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3;wire-quoted=\"a;b=c\"\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.server.chunk-extension-quoted",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "7.1.1",
        "A quoted chunk extension value can contain semicolon and equals delimiters.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3;wire-quoted=\"a;b=c\"\r\nabc\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.client.chunk-extension-empty-quoted",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1.1", "A quoted-string chunk extension value may be empty.", "MUST"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3;wire-empty=\"\"\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.server.chunk-extension-empty-quoted",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1.1", "A quoted-string chunk extension value may be empty.", "MUST"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3;wire-empty=\"\"\r\nabc\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.client.chunk-extension-quoted-pair",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "7.1.1",
        "Quoted-pair escaping is valid inside chunk extension quoted strings.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3;wire-quoted=\"a\\\"b\\\\c\"\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.server.chunk-extension-quoted-pair",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "7.1.1",
        "Quoted-pair escaping is valid inside chunk extension quoted strings.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3;wire-quoted=\"a\\\"b\\\\c\"\r\nabc\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.client.chunk-extension-bad-whitespace",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "7.1.1",
        "Recipients parse the BWS positions defined by the chunk extension grammar.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3 \t; \twire-token \t= \talpha\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.server.chunk-extension-bad-whitespace",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "7.1.1",
        "Recipients parse the BWS positions defined by the chunk extension grammar.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3 \t; \twire-token \t= \talpha\r\nabc\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.client.chunk-extension-multiple",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "7.1.1",
        "A chunk can carry several unrecognized extensions without changing content.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3;wire-flag;wire-token=alpha;wire-quoted=\"beta\"\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.server.chunk-extension-multiple",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "7.1.1",
        "A chunk can carry several unrecognized extensions without changing content.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3;wire-flag;wire-token=alpha;wire-quoted=\"beta\"\r\nabc\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.client.chunk-extension-on-last-chunk",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1.1", "The last zero chunk may itself carry chunk extensions.", "MUST"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n00;wire-end=\"done\"\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1
    WireCase(
      "h1.server.chunk-extension-on-last-chunk",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1.1", "The last zero chunk may itself carry chunk extensions.", "MUST"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n00;wire-end=\"done\"\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.client.chunked-binary-octets",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "7.1",
        "Chunk data accepts arbitrary octets, including NUL and non-ASCII bytes.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, ByteVector.fromValidHex("00ff80"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\n"
            ) ++ ByteVector.fromValidHex("00ff80") ++ Http1Wire.ascii("\r\n0\r\n\r\n")
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.server.chunked-binary-octets",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "7.1",
        "Chunk data accepts arbitrary octets, including NUL and non-ASCII bytes.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3\r\n"
            ) ++ ByteVector.fromValidHex("00ff80") ++ Http1Wire.ascii("\r\n0\r\n\r\n")
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, ByteVector.fromValidHex("00ff80"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, ByteVector.fromValidHex("00ff80"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7
    WireCase(
      "h1.client.transfer-coding-case-insensitive",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7", "Transfer coding names are case-insensitive.", "MUST"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: ChUnKeD\r\n\r\n3\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7
    WireCase(
      "h1.server.transfer-coding-case-insensitive",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7", "Transfer coding names are case-insensitive.", "MUST"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: ChUnKeD\r\n\r\n3\r\nabc\r\n0\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          // Section 6.3 permits a 411 policy even for a valid chunked request.
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.method, "POST")
                _ <- Check.equal(received.path, "/framing")
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recipient recovery; sender grammar alone does not mandate rejection.
    WireCase(
      "h1.client.reject-chunk-nonhex-size",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "A chunk-size contains hexadecimal digits only.", "syntax"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        // Only the target exchange and body consumption are attempted.
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(response => response.body.compile.drain.as(response.status))
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right(status) =>
              // RFC 9110 Section 2.4 permits repair; it does not prescribe recovered body bytes.
              Check.equal(status, 200)
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\ng\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recipient recovery; sender grammar alone does not mandate rejection.
    WireCase(
      "h1.server.reject-chunk-nonhex-size",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "A chunk-size contains hexadecimal digits only.", "syntax"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\ng\r\nabc\r\n0\r\n\r\n"
          )
          _ <- checkChunkRecoveryOrRejection(peer, server, expectedBody = None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recipient recovery; sender grammar alone does not mandate rejection.
    WireCase(
      "h1.client.reject-chunk-negative-size",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "A chunk-size cannot have a minus sign.", "syntax"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        // Only the target exchange and body consumption are attempted.
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(response => response.body.compile.drain.as(response.status))
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right(status) =>
              // RFC 9110 Section 2.4 permits repair; it does not prescribe recovered body bytes.
              Check.equal(status, 200)
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n-3\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recipient recovery; sender grammar alone does not mandate rejection.
    WireCase(
      "h1.server.reject-chunk-negative-size",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "A chunk-size cannot have a minus sign.", "syntax"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n-3\r\nabc\r\n0\r\n\r\n"
          )
          _ <- checkChunkRecoveryOrRejection(peer, server, expectedBody = None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recovery; the historical reject ID names invalid sender syntax.
    WireCase(
      "h1.client.reject-chunk-explicit-plus-size",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "A chunk-size cannot have a plus sign.", "syntax"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        // Historical reject ID: invalid sender syntax may be rejected or recovered (RFC 9110 Section 2.4).
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(response =>
            response.body.compile.to(Chunk).map(body => (response.status, body.toByteVector))
          )
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right((status, _)) =>
              // RFC 9110 Section 2.4 does not prescribe the repair algorithm or content.
              Check.equal(status, 200)
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n+3\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recovery; the historical reject ID names invalid sender syntax.
    WireCase(
      "h1.server.reject-chunk-explicit-plus-size",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "A chunk-size cannot have a plus sign.", "syntax"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n+3\r\nabc\r\n0\r\n\r\n"
          )
          _ <- checkChunkRecoveryOrRejection(peer, server, expectedBody = None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recovery; the historical reject ID names invalid sender syntax.
    WireCase(
      "h1.client.reject-chunk-hex-prefix-size",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "The chunk-size grammar does not permit a 0x prefix.", "syntax"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        // Historical reject ID: invalid sender syntax may be rejected or recovered (RFC 9110 Section 2.4).
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(response =>
            response.body.compile.to(Chunk).map(body => (response.status, body.toByteVector))
          )
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right((status, _)) =>
              // RFC 9110 Section 2.4 does not prescribe the repair algorithm or content.
              Check.equal(status, 200)
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n0x3\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recovery; the historical reject ID names invalid sender syntax.
    WireCase(
      "h1.server.reject-chunk-hex-prefix-size",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "The chunk-size grammar does not permit a 0x prefix.", "syntax"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n0x3\r\nabc\r\n0\r\n\r\n"
          )
          _ <- checkChunkRecoveryOrRejection(peer, server, expectedBody = None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recipient recovery; sender grammar alone does not mandate rejection.
    WireCase(
      "h1.client.reject-chunk-empty-size",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "A chunk-size requires at least one hexadecimal digit.", "syntax"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        // Only the target exchange and body consumption are attempted.
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(response => response.body.compile.drain.as(response.status))
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right(status) =>
              // RFC 9110 Section 2.4 permits repair; it does not prescribe recovered body bytes.
              Check.equal(status, 200)
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n\r\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recipient recovery; sender grammar alone does not mandate rejection.
    WireCase(
      "h1.server.reject-chunk-empty-size",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "A chunk-size requires at least one hexadecimal digit.", "syntax"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n\r\nabc\r\n0\r\n\r\n"
          )
          _ <- checkChunkRecoveryOrRejection(peer, server, expectedBody = None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recipient recovery; sender grammar alone does not mandate rejection.
    WireCase(
      "h1.client.reject-chunk-wrong-data-terminator",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "Chunk data must be followed by CRLF.", "syntax"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        // Only the target exchange and body consumption are attempted.
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(response => response.body.compile.drain.as(response.status))
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right(status) =>
              // RFC 9110 Section 2.4 permits repair; it does not prescribe recovered body bytes.
              Check.equal(status, 200)
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabcXY0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recipient recovery; sender grammar alone does not mandate rejection.
    WireCase(
      "h1.server.reject-chunk-wrong-data-terminator",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "Chunk data must be followed by CRLF.", "syntax"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabcXY0\r\n\r\n"
          )
          _ <- checkChunkRecoveryOrRejection(peer, server, expectedBody = None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recovery; the historical reject ID names invalid sender syntax.
    WireCase(
      "h1.client.reject-chunk-bare-lf-size-terminator",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "7.1",
        "Chunk-size senders use CRLF; recipients may recover under RFC 9110 Section 2.4.",
        "syntax",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        // Historical reject ID: invalid sender syntax may be rejected or recovered (RFC 9110 Section 2.4).
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(response =>
            response.body.compile.to(Chunk).map(body => (response.status, body.toByteVector))
          )
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right((status, _)) =>
              // RFC 9110 Section 2.4 does not prescribe the repair algorithm or content.
              Check.equal(status, 200)
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\nabc\r\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recovery; the historical reject ID names invalid sender syntax.
    WireCase(
      "h1.server.reject-chunk-bare-lf-size-terminator",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "7.1",
        "Chunk-size senders use CRLF; recipients may recover under RFC 9110 Section 2.4.",
        "syntax",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3\nabc\r\n0\r\n\r\n"
          )
          _ <- checkChunkRecoveryOrRejection(peer, server, expectedBody = None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recovery; the historical reject ID names invalid sender syntax.
    WireCase(
      "h1.client.reject-chunk-bare-lf-data-terminator",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "The chunk-data terminator is CRLF, not a bare LF.", "syntax"),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        // Historical reject ID: invalid sender syntax may be rejected or recovered (RFC 9110 Section 2.4).
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(response =>
            response.body.compile.to(Chunk).map(body => (response.status, body.toByteVector))
          )
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right((status, _)) =>
              // RFC 9110 Section 2.4 does not prescribe the repair algorithm or content.
              Check.equal(status, 200)
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\n0\r\n\r\n"
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // RFC 9110 Section 2.4 permits recovery; the historical reject ID names invalid sender syntax.
    WireCase(
      "h1.server.reject-chunk-bare-lf-data-terminator",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "The chunk-data terminator is CRLF, not a bare LF.", "syntax"),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\n0\r\n\r\n"
          )
          _ <- checkChunkRecoveryOrRejection(peer, server, expectedBody = None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.client.chunk-size-prevents-integer-wraparound",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "7.1",
        "A hexadecimal chunk size above machine integer ranges must not wrap to a short complete chunk.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        // Only the target exchange and body consumption are attempted.
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use(_.body.compile.drain)
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) =>
              rejected(error)
            case Right(_) =>
              Check(false, "the target consumed the deliberately invalid framing")
          }
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http1Wire.ascii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n10000000000000003\r\nabc\r\n0\r\n\r\n"
            )
          )
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.server.chunk-size-prevents-integer-wraparound",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "7.1",
        "A hexadecimal request chunk size above machine integer ranges must not wrap to a short complete chunk.",
        "MUST",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n10000000000000003\r\nabc\r\n0\r\n\r\n"
          )
          _ <- peer.endOfOutput
          status <- responseHeadOrEof(peer)
          _ <- Check(
            status.forall(value => value >= 400 && value <= 599),
            s"invalid framing was answered successfully: $status",
          )
          observed <- server.pollReceived
          _ <- Check(
            observed.isEmpty,
            s"EchoBody reported a complete request after an overflowing length: $observed",
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.2
    WireCase(
      "h1.client.chunk-trailer-separate-from-headers",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "7.1.2",
        "A recipient may discard trailers, but retained trailer fields without merge permission remain separate from headers.",
        "MUST",
      ),
    ) { context =>
      // X-Wire-Trace permits trailer use but has no rule permitting header/trailer merging.
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              trailers <- response.trailers
              retained = fieldValues(trailers, "X-Wire-Trace").flatMap(_.split(",").map(_.trim))
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
              _ <- Check.equal(fieldValues(response.headers, "X-Wire-Trace"), Vector("head"))
              _ <- Check(
                retained.isEmpty || retained == Vector("tail"),
                s"retained trailers changed their values or order: $retained",
              )
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- peer.writeAscii(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nX-Wire-Trace: head\r\nTrailer: X-Wire-Trace\r\n\r\n3\r\nabc\r\n0\r\nX-Wire-Trace: tail\r\n\r\n"
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.2
    WireCase(
      "h1.server.chunk-trailer-separate-from-headers",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "7.1.2",
        "Trailer fields with no merge permission cannot alter the received header section or content.",
        "MUST",
      ),
    ) { context =>
      // RequestData cannot expose trailers; the observable invariant is non-merging.
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\nX-Wire-Trace: head\r\nTrailer: X-Wire-Trace\r\n\r\n3\r\nabc\r\n0\r\nX-Wire-Trace: tail\r\n\r\n"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
                _ <- Check.equal(fieldValues(received.headers, "X-Wire-Trace"), Vector("head"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.2
    WireCase(
      "h1.client.chunk-trailers-preserve-occurrence-order",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "7.1.2",
        "A recipient may discard trailers, but retained trailer fields without merge permission remain separate from headers.",
        "MUST",
      ),
    ) { context =>
      // X-Wire-Trace permits trailer use but has no rule permitting header/trailer merging.
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              trailers <- response.trailers
              retained = fieldValues(trailers, "X-Wire-Trace").flatMap(_.split(",").map(_.trim))
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
              _ <- Check.equal(fieldValues(response.headers, "X-Wire-Trace"), Vector("head"))
              _ <- Check(
                retained.isEmpty || retained == Vector("first", "second"),
                s"retained trailers changed their values or order: $retained",
              )
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- peer.writeAscii(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nX-Wire-Trace: head\r\nTrailer: X-Wire-Trace\r\n\r\n3\r\nabc\r\n0\r\nX-Wire-Trace: first\r\nX-Wire-Trace: second\r\n\r\n"
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.2
    WireCase(
      "h1.server.chunk-trailers-preserve-occurrence-order",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "7.1.2",
        "Trailer fields with no merge permission cannot alter the received header section or content.",
        "MUST",
      ),
    ) { context =>
      // RequestData cannot expose trailers; the observable invariant is non-merging.
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\nX-Wire-Trace: head\r\nTrailer: X-Wire-Trace\r\n\r\n3\r\nabc\r\n0\r\nX-Wire-Trace: first\r\nX-Wire-Trace: second\r\n\r\n"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
                _ <- Check.equal(fieldValues(received.headers, "X-Wire-Trace"), Vector("head"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.2
    WireCase(
      "h1.client.chunk-trailer-unannounced",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "7.1.2",
        "A recipient may discard trailers, but retained trailer fields without merge permission remain separate from headers.",
        "MUST",
      ),
    ) { context =>
      // X-Wire-Trace permits trailer use but has no rule permitting header/trailer merging.
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              trailers <- response.trailers
              retained = fieldValues(trailers, "X-Wire-Trace").flatMap(_.split(",").map(_.trim))
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
              _ <- Check.equal(fieldValues(response.headers, "X-Wire-Trace"), Vector("head"))
              _ <- Check(
                retained.isEmpty || retained == Vector("tail"),
                s"retained trailers changed their values or order: $retained",
              )
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- peer.writeAscii(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nX-Wire-Trace: head\r\n\r\n3\r\nabc\r\n0\r\nX-Wire-Trace: tail\r\n\r\n"
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.2
    WireCase(
      "h1.server.chunk-trailer-unannounced",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "7.1.2",
        "Trailer fields with no merge permission cannot alter the received header section or content.",
        "MUST",
      ),
    ) { context =>
      // RequestData cannot expose trailers; the observable invariant is non-merging.
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /framing HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\nX-Wire-Trace: head\r\n\r\n3\r\nabc\r\n0\r\nX-Wire-Trace: tail\r\n\r\n"
          )
          response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
          _ <-
            if (response.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                received <- server.received
                _ <- Check.equal(received.body, Http1Wire.ascii("abc"))
                _ <- Check.equal(fieldValues(received.headers, "X-Wire-Trace"), Vector("head"))
              } yield ()
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-10.1.4
    WireCase(
      "h1.client.te-trailers-preserves-trailers",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9110,
        "10.1.4",
        "A client that sends TE: trailers declares that it will not discard trailer fields.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(
            RequestData(headers =
              Vector("TE" -> "trailers", "Connection" -> "TE", "Content-Length" -> "0")
            )
          )
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              trailers <- response.trailers
              values = fieldValues(trailers, "X-Wire-Trace").flatMap(_.split(",").map(_.trim))
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
              _ <- Check.equal(fieldValues(response.headers, "X-Wire-Trace"), Vector.empty[String])
              _ <- Check.equal(values, Vector("first", "second"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(fieldValues(request.headers, "TE"), Vector("trailers"))
          connectionTokens = fieldValues(request.headers, "Connection").flatMap(
            _.split(",").map(_.trim)
          )
          _ <- Check(
            connectionTokens.exists(_.equalsIgnoreCase("TE")),
            "TE requires a Connection option",
          )
          _ <- peer.writeAscii(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nTrailer: X-Wire-Trace\r\n\r\n3\r\nabc\r\n0\r\nX-Wire-Trace: first\r\nX-Wire-Trace: second\r\n\r\n"
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.sender-known-binary-length",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A client sending a nonempty request body uses valid Content-Length or chunked transfer coding.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(
            RequestData(
              method = "POST",
              path = "/framing",
              body = ByteVector.fromValidHex("00ff80"),
            )
          )
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readGeneratedRequest(peer)
          lengths = fieldValues(request.headers, "Content-Length")
          transfers = fieldValues(request.headers, "Transfer-Encoding")
          _ <- Check.equal(request.method, "POST")
          _ <- Check.equal(request.target, "/framing")
          _ <- Check.equal(request.body, ByteVector.fromValidHex("00ff80"))
          _ <- Check(
            lengths.nonEmpty != transfers.nonEmpty,
            "a nonempty request requires exactly one framing mechanism",
          )
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.sender-streamed-binary-content",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A streamed request body uses explicit valid framing and preserves the content octets.",
        "MUST",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchangeStream(
            RequestData(method = "POST", path = "/framing"),
            _ =>
              Stream.chunk(Chunk.byteVector(ByteVector.fromValidHex("00ff"))).covary[IO] ++
                Stream.chunk(Chunk.byteVector(ByteVector.fromValidHex("80fe"))).covary[IO],
          )
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))
            } yield ()
          }
          .handleErrorWith(unexpectedTargetRejection)
      ) { peer =>
        for {
          request <- Http1Wire.readGeneratedRequest(peer)
          lengths = fieldValues(request.headers, "Content-Length")
          transfers = fieldValues(request.headers, "Transfer-Encoding")
          _ <- Check.equal(request.method, "POST")
          _ <- Check.equal(request.target, "/framing")
          _ <- Check.equal(request.body, ByteVector.fromValidHex("00ff80fe"))
          _ <- Check(
            lengths.nonEmpty != transfers.nonEmpty,
            "a streamed request requires exactly one framing mechanism",
          )
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.2
    WireCase(
      "h1.server.sender-known-binary-content",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.2",
        "A sender does not combine Content-Length with Transfer-Encoding and preserves the framed content.",
        "MUST",
      ),
    ) { context =>
      context
        .server(Protocol.Http1, ServerScenario.Reply(body = ByteVector.fromValidHex("00ff80")))
        .use { case (peer, _) =>
          for {
            _ <- peer
              .writeAscii("GET /framing HTTP/1.1\r\nHost: wire.test\r\nConnection: close\r\n\r\n")
            response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
            _ <- IO(Http1Wire.validateGeneratedContentLength(response.headers))
            lengths = fieldValues(response.headers, "Content-Length")
            transfers = fieldValues(response.headers, "Transfer-Encoding")
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.fromValidHex("00ff80"))
            _ <- Check(
              lengths.isEmpty || transfers.isEmpty,
              "a sender must not emit both CL and TE",
            )
            _ <- Check(
              transfers.isEmpty || transfers == Vector("") ||
                transfers
                  .flatMap(_.split(",", -1).map(_.trim))
                  .forall(_.equalsIgnoreCase("chunked")),
              s"only chunked is acceptable without a TE request field: $transfers",
            )
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
          } yield ()
        }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.2
    WireCase(
      "h1.server.sender-streamed-content",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.2",
        "A sender does not combine Content-Length with Transfer-Encoding and preserves the framed content.",
        "MUST",
      ),
    ) { context =>
      context
        .server(
          Protocol.Http1,
          ServerScenario.ReplyChunks(
            Vector(Http1Wire.ascii("a"), ByteVector.fromValidHex("00ff"), Http1Wire.ascii("bc"))
          ),
        )
        .use { case (peer, _) =>
          for {
            _ <- peer
              .writeAscii("GET /framing HTTP/1.1\r\nHost: wire.test\r\nConnection: close\r\n\r\n")
            response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
            _ <- IO(Http1Wire.validateGeneratedContentLength(response.headers))
            lengths = fieldValues(response.headers, "Content-Length")
            transfers = fieldValues(response.headers, "Transfer-Encoding")
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(
              response.body,
              Http1Wire.ascii("a") ++ ByteVector.fromValidHex("00ff") ++ Http1Wire.ascii("bc"),
            )
            _ <- Check(
              lengths.isEmpty || transfers.isEmpty,
              "a sender must not emit both CL and TE",
            )
            _ <- Check(
              transfers.isEmpty || transfers == Vector("") ||
                transfers
                  .flatMap(_.split(",", -1).map(_.trim))
                  .forall(_.equalsIgnoreCase("chunked")),
              s"only chunked is acceptable without a TE request field: $transfers",
            )
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
          } yield ()
        }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.1
    WireCase(
      "h1.server.http10-response-has-no-transfer-encoding",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.1",
        "A server must not emit Transfer-Encoding when the corresponding request indicates HTTP/1.0.",
        "MUST",
      ),
    ) { context =>
      context
        .server(
          Protocol.Http1,
          ServerScenario.ReplyChunks(Vector(Http1Wire.ascii("a"), Http1Wire.ascii("bc"))),
        )
        .use { case (peer, _) =>
          for {
            _ <- peer
              .writeAscii("GET /framing HTTP/1.0\r\nHost: wire.test\r\nConnection: close\r\n\r\n")
            response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
            _ <- Check
              .equal(fieldValues(response.headers, "Transfer-Encoding"), Vector.empty[String])
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
          } yield ()
        }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.head-response-has-no-content",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "HEAD, 204, and 304 responses terminate at the end of headers and cannot send content or trailers.",
        "MUST",
      ),
    ) { context =>
      context
        .server(Protocol.Http1, ServerScenario.Reply(status = 200, body = Http1Wire.ascii("abc")))
        .use { case (peer, _) =>
          for {
            _ <- peer
              .writeAscii("HEAD /framing HTTP/1.1\r\nHost: wire.test\r\nConnection: close\r\n\r\n")
            response <- Http1Wire
              .readFinalResponse(peer, requestMethod = "HEAD")
              .handleErrorWith(unexpectedEof)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(response.body, ByteVector.empty)
            lengths = fieldValues(response.headers, "Content-Length")
            _ <- Check(
              lengths
                .forall(value => value.nonEmpty && value.forall(_.isDigit) && BigInt(value) == 3),
              s"HEAD Content-Length must equal the GET content length: $lengths",
            )
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
          } yield ()
        }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.no-content-response-has-no-content",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "HEAD, 204, and 304 responses terminate at the end of headers and cannot send content or trailers.",
        "MUST",
      ),
    ) { context =>
      context
        .server(Protocol.Http1, ServerScenario.Reply(status = 204, body = Http1Wire.ascii("abc")))
        .use { case (peer, _) =>
          for {
            _ <- peer
              .writeAscii("GET /framing HTTP/1.1\r\nHost: wire.test\r\nConnection: close\r\n\r\n")
            response <- Http1Wire
              .readFinalResponse(peer, requestMethod = "GET")
              .handleErrorWith(unexpectedEof)
            _ <- Check.equal(response.status, 204)
            _ <- Check.equal(response.body, ByteVector.empty)
            _ <- Check.equal(fieldValues(response.headers, "Content-Length"), Vector.empty[String])
            _ <- Check
              .equal(fieldValues(response.headers, "Transfer-Encoding"), Vector.empty[String])
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
          } yield ()
        }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.not-modified-response-has-no-content",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "HEAD, 204, and 304 responses terminate at the end of headers and cannot send content or trailers.",
        "MUST",
      ),
    ) { context =>
      context
        .server(Protocol.Http1, ServerScenario.Reply(status = 304, body = Http1Wire.ascii("abc")))
        .use { case (peer, _) =>
          for {
            _ <- peer
              .writeAscii("GET /framing HTTP/1.1\r\nHost: wire.test\r\nConnection: close\r\n\r\n")
            response <- Http1Wire
              .readFinalResponse(peer, requestMethod = "GET")
              .handleErrorWith(unexpectedEof)
            _ <- Check.equal(response.status, 304)
            _ <- Check.equal(response.body, ByteVector.empty)
            _ <- Http1FramingCases.expectClosedAfterResponse(peer)
          } yield ()
        }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.1
    WireCase(
      "h1.client.http10-transfer-encoding-closes",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.1",
        "An HTTP/1.0 message with Transfer-Encoding has faulty framing and the recipient closes the connection after processing.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          driver
            .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
            .use(response =>
              response.body.compile.to(Chunk).map(body => (response.status, body.toByteVector))
            )
            .attempt
            .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
            .flatMap {
              case Left(error) =>
                rejected(error)
              case Right((status, body)) =>
                Check.equal(status, 200) *> Check.equal(body, Http1Wire.ascii("abc"))
            } *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- peer.writeAscii(
              "HTTP/1.0 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n0\r\n\r\n"
            )
            _ <- expectClosedAfterResponse(peer)
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.1
    WireCase(
      "h1.client.http10-transfer-encoding-with-length-closes",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.1",
        "An HTTP/1.0 message with Transfer-Encoding has faulty framing and the recipient closes the connection after processing.",
        "MUST",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          driver
            .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
            .use(response =>
              response.body.compile.to(Chunk).map(body => (response.status, body.toByteVector))
            )
            .attempt
            .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
            .flatMap {
              case Left(error) =>
                rejected(error)
              case Right((status, body)) =>
                Check.equal(status, 200) *> Check.equal(body, Http1Wire.ascii("abc"))
            } *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- peer.writeAscii(
              "HTTP/1.0 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Length: 1\r\n\r\n3\r\nabc\r\n0\r\n\r\n"
            )
            _ <- expectClosedAfterResponse(peer)
            _ <- closed.complete(())
          } yield ()
        }
      }
    },
  )

  /** RFC 9110 Section 2.4 permits recipient recovery from invalid sender syntax.
    * Check intended content only when specified by the probe; ambiguous repairs have no prescribed body.
    */
  private def checkChunkRecoveryOrRejection(
      peer: RawPeer[IO],
      server: ServerHandle[IO],
      expectedBody: Option[ByteVector],
  ): IO[Unit] =
    responseHeadOrEof(peer).flatMap {
      case None => IO.unit
      case Some(status) if status >= 400 && status <= 599 => IO.unit
      case Some(status) =>
        for {
          _ <- Check.equal(status, 200)
          request <- server.received
          _ <- Check.equal(request.method, "POST")
          _ <- Check.equal(request.path, "/framing")
          _ <- expectedBody.traverse_(Check.equal(request.body, _))
        } yield ()
    }

  /** A complete response was already decoded; EOF or a recognized reset establishes closure. */
  private[wire] def expectClosedAfterResponse(peer: RawPeer[IO]): IO[Unit] =
    peer
      .readToEnd(0)
      .attempt
      .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
      .flatMap {
        case Right(bytes) => Check.equal(bytes, ByteVector.empty)
        case Left(WireBodyLimit(0)) =>
          Check(false, "additional bytes followed a response requiring connection closure")
        case Left(error) if RuntimeInfo.isConnectionReset(error) => IO.unit
        case Left(error) => IO.raiseError(error)
      }

  private def fieldValues(headers: Vector[(String, String)], name: String): Vector[String] =
    headers.collect { case (key, value) if key.equalsIgnoreCase(name) => value }

  private def unexpectedTargetRejection(error: Throwable): IO[Unit] =
    RawPeer.raiseAdapterOrTimeout[IO](error) *>
      (if (
         error.isInstanceOf[AssertionError] || error
           .isInstanceOf[WireObservationLimit]
       ) IO.raiseError(error)
       else Check(false, s"the target rejected a valid response: $error"))

  private def unexpectedEof[A](error: Throwable): IO[A] =
    RawPeer.raiseAdapterOrTimeout[IO](error) *> (error match {
      case eof: WireReadFailure =>
        IO.raiseError(
          new ProtocolFailure(s"the target closed before completing a required response: $eof")
        )
      case other => IO.raiseError(other)
    })

  private def clientResponse(
      context: CaseContext,
      bytes: ByteVector,
      expected: ByteVector,
  ): IO[Unit] =
    context.client(Protocol.Http1)(driver =>
      driver
        .exchange(RequestData(headers = Vector("Content-Length" -> "0")))
        .use { response =>
          response.body.compile.to(Chunk).flatMap { body =>
            Check.equal(response.status, 200) *> Check.equal(body.toByteVector, expected)
          }
        }
        .handleErrorWith(unexpectedTargetRejection)
    ) { peer =>
      Http1Wire.readRequest(peer).flatMap { request =>
        Check.equal(request.method, "GET") *> Check.equal(request.body, ByteVector.empty) *>
          peer.write(bytes)
      }
    }

  private def serverEcho(context: CaseContext, bytes: ByteVector, expected: ByteVector): IO[Unit] =
    context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
      for {
        _ <- peer.write(bytes)
        response <- Http1Wire.readFinalResponse(peer).handleErrorWith(unexpectedEof)
        _ <- Check.equal(response.status, 200)
        received <- server.received
        _ <- Check.equal(response.body, expected)
        _ <- Check.equal(received.method, "POST")
        _ <- Check.equal(received.path, "/framing")
        _ <- Check.equal(received.body, expected)
      } yield ()
    }

  private def responseHeadOrEof(peer: RawPeer[IO]): IO[Option[Int]] =
    Http1Wire.readFinalStatusOrClosed(peer)

  private def rejected(error: Throwable): IO[Unit] =
    RawPeer.raiseAdapterOrTimeout[IO](error) *> (error match {
      case limit: WireObservationLimit => IO.raiseError(limit)
      case assertion: AssertionError => IO.raiseError(assertion)
      case _ => IO.unit
    })

}
