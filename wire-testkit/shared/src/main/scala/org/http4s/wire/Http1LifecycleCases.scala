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

/** HTTP/1 message completion, persistence, response ordering, and connection closure. */
object Http1LifecycleCases {
  val all: Vector[WireCase] = Vector(
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.client.incomplete-status-line",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "8", "A response ending inside its status line is incomplete."),
    ) { context =>
      context.client(Protocol.Http1)(driver => rejectIncompleteResponse(driver)) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "POST")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.writeAscii("HTTP/1.1 200")
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.client.incomplete-response-field",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "8", "EOF in a response field does not complete the response."),
    ) { context =>
      context.client(Protocol.Http1)(driver => rejectIncompleteResponse(driver)) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "POST")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 3\r\nX-Wire: partial")
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.client.incomplete-response-head",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "8", "A response head requires its terminating empty line before EOF."),
    ) { context =>
      context.client(Protocol.Http1)(driver => rejectIncompleteResponse(driver)) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "POST")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n")
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.client.incomplete-chunk-size",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "8", "EOF within a chunk-size line leaves the response incomplete."),
    ) { context =>
      context.client(Protocol.Http1)(driver => rejectIncompleteResponse(driver)) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "POST")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3")
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.client.incomplete-chunk-data",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "8", "EOF before the declared chunk data ends leaves the response incomplete."),
    ) { context =>
      context.client(Protocol.Http1)(driver => rejectIncompleteResponse(driver)) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "POST")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nabc")
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.client.incomplete-chunk-delimiter",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "8", "EOF within the chunk-data CRLF leaves the response incomplete."),
    ) { context =>
      context.client(Protocol.Http1)(driver => rejectIncompleteResponse(driver)) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "POST")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r")
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.client.incomplete-terminal-chunk",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "8", "A chunked response without a zero-sized last chunk is incomplete."),
    ) { context =>
      context.client(Protocol.Http1)(driver => rejectIncompleteResponse(driver)) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "POST")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n")
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.client.incomplete-trailer-terminator",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "A last chunk still requires the final empty trailer-section line."),
    ) { context =>
      context.client(Protocol.Http1)(driver => rejectIncompleteResponse(driver)) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "POST")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.writeAscii(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n0\r\n"
          )
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.client.incomplete-trailer-field",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "7.1", "An unfinished trailer field does not complete a chunked response."),
    ) { context =>
      context.client(Protocol.Http1)(driver => rejectIncompleteResponse(driver)) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "POST")
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.writeAscii(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nX-Wire: partial"
          )
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.incomplete-fixed-length-empty",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A short Content-Length response is incomplete and requires closing the connection.",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          // Keep the client resource alive until its own closure has been observed.
          rejectIncompleteResponse(driver) *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "POST")
            _ <- Check.equal(request.body, ByteVector.empty)
            _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 1\r\n\r\n")
            _ <- peer.endOfOutput
            _ <- expectTransportClose(peer)
            _ <- closed.complete(()).void
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.incomplete-fixed-length-prefix",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A short Content-Length response is incomplete and requires closing the connection.",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          // Keep the client resource alive until its own closure has been observed.
          rejectIncompleteResponse(driver) *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "POST")
            _ <- Check.equal(request.body, ByteVector.empty)
            _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nabc")
            _ <- peer.endOfOutput
            _ <- expectTransportClose(peer)
            _ <- closed.complete(()).void
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.client.complete-fixed-length-at-eof",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "8", "EOF after exactly Content-Length octets does not make a response incomplete."),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabc")
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.complete-zero-length-at-eof",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A zero Content-Length response is complete at the end of its header section.",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii(""))
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.client.complete-chunked-at-eof",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "8", "A complete chunked response remains complete when followed by orderly EOF."),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- peer.writeAscii(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n0\r\n\r\n"
          )
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.client.close-delimited-empty-response",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "8",
        "A complete header section and orderly EOF delimit an empty unframed response.",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii(""))
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n")
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.http10-close-delimited-response",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "An HTTP/1.0 response without length framing is delimited by orderly closure.",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- peer.writeAscii("HTTP/1.0 200 OK\r\n\r\nabc")
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.client.close-delimited-binary-response",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "8",
        "Orderly EOF completes an unframed response without altering its binary content.",
      ),
    ) { context =>
      val expected = ByteVector(0, 1, 13, 10, 127, 128, 255)
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, expected)
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n")
          _ <- peer.write(expected)
          _ <- peer.endOfOutput
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.client.response-close-fixed-length",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "9.6", "The client closes after reading a fixed-length response carrying close."),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          driver
            .exchange(RequestData())
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
              } yield ()
            }
            .handleErrorWith(requiredAcceptanceFailure) *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- peer.writeAscii(
              "HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc"
            )
            // Do not half-close the peer or release the client to manufacture this EOF.
            _ <- expectTransportClose(peer)
            _ <- closed.complete(()).void
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.client.response-close-chunked",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "9.6", "The client closes after reading a chunked response carrying close."),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          driver
            .exchange(RequestData())
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
              } yield ()
            }
            .handleErrorWith(requiredAcceptanceFailure) *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- peer.writeAscii(
              "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n3\r\nabc\r\n0\r\n\r\n"
            )
            // Do not half-close the peer or release the client to manufacture this EOF.
            _ <- expectTransportClose(peer)
            _ <- closed.complete(()).void
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-7.6.1
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.client.response-close-case-insensitive",
      Protocol.Http1,
      Role.Client,
      Rfc(9110, "7.6.1", "Connection field names and close options are case-insensitive."),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          driver
            .exchange(RequestData())
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
              } yield ()
            }
            .handleErrorWith(requiredAcceptanceFailure) *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- peer.writeAscii(
              "HTTP/1.1 200 OK\r\nContent-Length: 3\r\ncOnNeCtIoN: ClOsE\r\n\r\nabc"
            )
            // Do not half-close the peer or release the client to manufacture this EOF.
            _ <- expectTransportClose(peer)
            _ <- closed.complete(()).void
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.client.response-close-option-list",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "9.3", "The close option takes precedence over keep-alive in an option list."),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          driver
            .exchange(RequestData())
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
              } yield ()
            }
            .handleErrorWith(requiredAcceptanceFailure) *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- peer.writeAscii(
              "HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: keep-alive, close\r\n\r\nabc"
            )
            // Do not half-close the peer or release the client to manufacture this EOF.
            _ <- expectTransportClose(peer)
            _ <- closed.complete(()).void
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-7.6.1
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.client.response-close-repeated-field",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9110,
        "7.6.1",
        "A close option in a subsequent Connection field still closes the connection.",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          driver
            .exchange(RequestData())
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
              } yield ()
            }
            .handleErrorWith(requiredAcceptanceFailure) *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- peer.writeAscii(
              "HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: keep-alive\r\nConnection: close\r\n\r\nabc"
            )
            // Do not half-close the peer or release the client to manufacture this EOF.
            _ <- expectTransportClose(peer)
            _ <- closed.complete(()).void
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.client.request-close-final-response",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "9.6",
        "A client sending close closes after the final response, even without a response close option.",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { closed =>
        context.client(Protocol.Http1)(driver =>
          driver
            .exchange(RequestData(headers = Vector("Connection" -> "close")))
            .use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Http1Wire.ascii("abc"))
              } yield ()
            }
            .handleErrorWith(requiredAcceptanceFailure) *> closed.get
        ) { peer =>
          for {
            request <- Http1Wire.readRequest(peer)
            _ <- Check.equal(request.method, "GET")
            _ <- Check(hasOption(request.headers, "close"), "the request must carry close")
            _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabc")
            _ <- expectTransportClose(peer)
            _ <- closed.complete(()).void
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.2
    WireCase(
      "h1.client.informational-before-final-response",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "9.2",
        "A visible informational response is bodyless; a visible final response retains its declared content.",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              // A visible 1xx is bodyless; this API cannot establish final-response continuation.
              _ <-
                if (response.status == 100 || response.status == 103)
                  Check.equal(body.toByteVector, ByteVector.empty)
                else
                  Check.equal(response.status, 200) *>
                    Check.equal(body.toByteVector, Http1Wire.ascii("final"))
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- peer.writeAscii(
            "HTTP/1.1 100 Continue\r\n\r\n" +
              "HTTP/1.1 103 Early Hints\r\nX-Wire: interim\r\n\r\n" +
              "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nfinal"
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.server.incomplete-request-head",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "8",
        "An incomplete request head may receive an error response or a silent connection close.",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          // Establish a working connection before the truncated message is introduced.
          _ <- peer.writeAscii(
            "POST /ready HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 2\r\n\r\nok"
          )
          ready <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(ready.status, 200)
          _ <- Check.equal(ready.body, Http1Wire.ascii("ok"))
          _ <-
            if (hasOption(ready.headers, "close"))
              IO.raiseError(
                new WireObservationLimit(
                  "coverage prerequisite unavailable: /ready closed the connection before the incomplete request"
                )
              )
            else IO.unit
          observed <- server.received
          _ <- Check.equal(observed.path, "/ready")
          _ <- Check.equal(observed.body, Http1Wire.ascii("ok"))
          _ <- peer
            .writeAscii("POST /incomplete HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 3\r\n")
          // Terminate the upload before fixture release; never abandon an EchoBody stream.
          _ <- peer.endOfOutput
          _ <- expectIncompleteRequestClosed(peer, server)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.server.incomplete-fixed-length-empty",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "6.3", "EOF before a declared request body arrives leaves the request incomplete."),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          // Establish a working connection before the truncated message is introduced.
          _ <- peer.writeAscii(
            "POST /ready HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 2\r\n\r\nok"
          )
          ready <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(ready.status, 200)
          _ <- Check.equal(ready.body, Http1Wire.ascii("ok"))
          _ <-
            if (hasOption(ready.headers, "close"))
              IO.raiseError(
                new WireObservationLimit(
                  "coverage prerequisite unavailable: /ready closed the connection before the incomplete request"
                )
              )
            else IO.unit
          observed <- server.received
          _ <- Check.equal(observed.path, "/ready")
          _ <- Check.equal(observed.body, Http1Wire.ascii("ok"))
          _ <- peer
            .writeAscii("POST /incomplete HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 1\r\n\r\n")
          // Terminate the upload before fixture release; never abandon an EchoBody stream.
          _ <- peer.endOfOutput
          _ <- expectIncompleteRequestClosed(peer, server)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.server.incomplete-fixed-length-prefix",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "A request shorter than Content-Length must remain incomplete and close the connection.",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          // Establish a working connection before the truncated message is introduced.
          _ <- peer.writeAscii(
            "POST /ready HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 2\r\n\r\nok"
          )
          ready <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(ready.status, 200)
          _ <- Check.equal(ready.body, Http1Wire.ascii("ok"))
          _ <-
            if (hasOption(ready.headers, "close"))
              IO.raiseError(
                new WireObservationLimit(
                  "coverage prerequisite unavailable: /ready closed the connection before the incomplete request"
                )
              )
            else IO.unit
          observed <- server.received
          _ <- Check.equal(observed.path, "/ready")
          _ <- Check.equal(observed.body, Http1Wire.ascii("ok"))
          _ <- peer.writeAscii(
            "POST /incomplete HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 4\r\n\r\nabc"
          )
          // Terminate the upload before fixture release; never abandon an EchoBody stream.
          _ <- peer.endOfOutput
          _ <- expectIncompleteRequestClosed(peer, server)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.server.incomplete-chunk-size",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "8", "EOF in the chunk-size line cannot complete an uploaded request body."),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          // Establish a working connection before the truncated message is introduced.
          _ <- peer.writeAscii(
            "POST /ready HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 2\r\n\r\nok"
          )
          ready <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(ready.status, 200)
          _ <- Check.equal(ready.body, Http1Wire.ascii("ok"))
          _ <-
            if (hasOption(ready.headers, "close"))
              IO.raiseError(
                new WireObservationLimit(
                  "coverage prerequisite unavailable: /ready closed the connection before the incomplete request"
                )
              )
            else IO.unit
          observed <- server.received
          _ <- Check.equal(observed.path, "/ready")
          _ <- Check.equal(observed.body, Http1Wire.ascii("ok"))
          _ <- peer.writeAscii(
            "POST /incomplete HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3"
          )
          // Terminate the upload before fixture release; never abandon an EchoBody stream.
          _ <- peer.endOfOutput
          _ <- expectIncompleteRequestClosed(peer, server)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.server.incomplete-chunk-data",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "8", "EOF before the chunk data ends cannot complete an uploaded request body."),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          // Establish a working connection before the truncated message is introduced.
          _ <- peer.writeAscii(
            "POST /ready HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 2\r\n\r\nok"
          )
          ready <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(ready.status, 200)
          _ <- Check.equal(ready.body, Http1Wire.ascii("ok"))
          _ <-
            if (hasOption(ready.headers, "close"))
              IO.raiseError(
                new WireObservationLimit(
                  "coverage prerequisite unavailable: /ready closed the connection before the incomplete request"
                )
              )
            else IO.unit
          observed <- server.received
          _ <- Check.equal(observed.path, "/ready")
          _ <- Check.equal(observed.body, Http1Wire.ascii("ok"))
          _ <- peer.writeAscii(
            "POST /incomplete HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nabc"
          )
          // Terminate the upload before fixture release; never abandon an EchoBody stream.
          _ <- peer.endOfOutput
          _ <- expectIncompleteRequestClosed(peer, server)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.server.incomplete-chunk-delimiter",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "8", "EOF inside a chunk-data delimiter cannot complete an uploaded request body."),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          // Establish a working connection before the truncated message is introduced.
          _ <- peer.writeAscii(
            "POST /ready HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 2\r\n\r\nok"
          )
          ready <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(ready.status, 200)
          _ <- Check.equal(ready.body, Http1Wire.ascii("ok"))
          _ <-
            if (hasOption(ready.headers, "close"))
              IO.raiseError(
                new WireObservationLimit(
                  "coverage prerequisite unavailable: /ready closed the connection before the incomplete request"
                )
              )
            else IO.unit
          observed <- server.received
          _ <- Check.equal(observed.path, "/ready")
          _ <- Check.equal(observed.body, Http1Wire.ascii("ok"))
          _ <- peer.writeAscii(
            "POST /incomplete HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r"
          )
          // Terminate the upload before fixture release; never abandon an EchoBody stream.
          _ <- peer.endOfOutput
          _ <- expectIncompleteRequestClosed(peer, server)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.server.incomplete-terminal-chunk",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "8", "EOF without a zero-sized last chunk leaves the uploaded request incomplete."),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          // Establish a working connection before the truncated message is introduced.
          _ <- peer.writeAscii(
            "POST /ready HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 2\r\n\r\nok"
          )
          ready <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(ready.status, 200)
          _ <- Check.equal(ready.body, Http1Wire.ascii("ok"))
          _ <-
            if (hasOption(ready.headers, "close"))
              IO.raiseError(
                new WireObservationLimit(
                  "coverage prerequisite unavailable: /ready closed the connection before the incomplete request"
                )
              )
            else IO.unit
          observed <- server.received
          _ <- Check.equal(observed.path, "/ready")
          _ <- Check.equal(observed.body, Http1Wire.ascii("ok"))
          _ <- peer.writeAscii(
            "POST /incomplete HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n"
          )
          // Terminate the upload before fixture release; never abandon an EchoBody stream.
          _ <- peer.endOfOutput
          _ <- expectIncompleteRequestClosed(peer, server)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.server.incomplete-trailer-terminator",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "A request ending before the final trailer-section CRLF is incomplete."),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          // Establish a working connection before the truncated message is introduced.
          _ <- peer.writeAscii(
            "POST /ready HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 2\r\n\r\nok"
          )
          ready <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(ready.status, 200)
          _ <- Check.equal(ready.body, Http1Wire.ascii("ok"))
          _ <-
            if (hasOption(ready.headers, "close"))
              IO.raiseError(
                new WireObservationLimit(
                  "coverage prerequisite unavailable: /ready closed the connection before the incomplete request"
                )
              )
            else IO.unit
          observed <- server.received
          _ <- Check.equal(observed.path, "/ready")
          _ <- Check.equal(observed.body, Http1Wire.ascii("ok"))
          _ <- peer.writeAscii(
            "POST /incomplete HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n0\r\n"
          )
          // Terminate the upload before fixture release; never abandon an EchoBody stream.
          _ <- peer.endOfOutput
          _ <- expectIncompleteRequestClosed(peer, server)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-8
    WireCase(
      "h1.server.incomplete-trailer-field",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "7.1", "A request ending inside a trailer field is incomplete."),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          // Establish a working connection before the truncated message is introduced.
          _ <- peer.writeAscii(
            "POST /ready HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 2\r\n\r\nok"
          )
          ready <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(ready.status, 200)
          _ <- Check.equal(ready.body, Http1Wire.ascii("ok"))
          _ <-
            if (hasOption(ready.headers, "close"))
              IO.raiseError(
                new WireObservationLimit(
                  "coverage prerequisite unavailable: /ready closed the connection before the incomplete request"
                )
              )
            else IO.unit
          observed <- server.received
          _ <- Check.equal(observed.path, "/ready")
          _ <- Check.equal(observed.body, Http1Wire.ascii("ok"))
          _ <- peer.writeAscii(
            "POST /incomplete HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nX-Wire: partial"
          )
          // Terminate the upload before fixture release; never abandon an EchoBody stream.
          _ <- peer.endOfOutput
          _ <- expectIncompleteRequestClosed(peer, server)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.server.complete-fixed-length-half-close",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "9.6", "A client half-close does not cancel the response to its complete request."),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /complete HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 3\r\n\r\nabc"
          )
          _ <- peer.endOfOutput
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
          observed <- server.received
          _ <- Check.equal(observed.path, "/complete")
          _ <- Check.equal(observed.body, Http1Wire.ascii("abc"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.server.complete-chunked-half-close",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "9.6", "A client half-close does not cancel the response to its complete request."),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /complete HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n0\r\n\r\n"
          )
          _ <- peer.endOfOutput
          response <- Http1Wire.readFinalResponse(peer)
          // RFC 9112 Section 6.3 permits 411 without establishing chunk decoding coverage.
          _ <-
            if (response.status == 411) IO.unit
            else {
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("abc"))
                observed <- server.received
                _ <- Check.equal(observed.path, "/complete")
                _ <- Check.equal(observed.body, Http1Wire.ascii("abc"))
              } yield ()
            }
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.server.complete-bodyless-half-close",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "9.6", "A client half-close does not cancel the response to its complete request."),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET /complete HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          _ <- peer.endOfOutput
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          _ <- Check.equal(response.body, Http1Wire.ascii(""))
          observed <- server.received
          _ <- Check.equal(observed.path, "/complete")
          _ <- Check.equal(observed.body, Http1Wire.ascii(""))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3
    WireCase(
      "h1.server.sequential-fixed-length-requests",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "9.3",
        "HTTP/1.1 persistence supports successive complete requests on one connection.",
        "SHOULD",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "POST /first HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 3\r\n\r\none"
          )
          first <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(first.status, 200)
          _ <- Check.equal(first.body, Http1Wire.ascii("one"))
          firstRequest <- server.received
          _ <- Check.equal(firstRequest.path, "/first")
          _ <- peer.writeAscii(
            "POST /second HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 5\r\n" +
              "Connection: close\r\n\r\nthree"
          )
          second <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(second.status, 200)
          _ <- Check.equal(second.body, Http1Wire.ascii("three"))
          secondRequest <- server.received
          _ <- Check.equal(secondRequest.path, "/second")
          _ <- expectTransportClose(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3.2
    WireCase(
      "h1.server.pipeline-fixed-length-requests",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "9.3.2",
        "Pipelined requests are delimited independently and their responses preserve request order.",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          // Both requests are sent before either response is read; bodies identify their order.
          _ <- peer.writeAscii(
            "POST /first HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 3\r\n\r\nonePOST /second HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 5\r\nConnection: close\r\n\r\nthree"
          )
          first <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(first.status, 200)
          _ <- Check.equal(first.body, Http1Wire.ascii("one"))
          second <- nextPipelineResponse(peer)
          _ <- Check.equal(second.status, 200)
          _ <- Check.equal(second.body, Http1Wire.ascii("three"))
          firstRequest <- server.received
          secondRequest <- server.received
          _ <- Check.equal(firstRequest.path, "/first")
          _ <- Check.equal(secondRequest.path, "/second")
          _ <- expectTransportClose(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3.2
    WireCase(
      "h1.server.pipeline-chunked-then-fixed-length",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "9.3.2",
        "Pipelined requests are delimited independently and their responses preserve request order.",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          // Both requests are sent before either response is read; bodies identify their order.
          _ <- peer.writeAscii(
            "POST /first HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\n\r\n3\r\none\r\n0\r\n\r\nPOST /second HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 5\r\nConnection: close\r\n\r\nthree"
          )
          first <- Http1Wire.readFinalResponse(peer)
          // A 411 policy may close without processing the pipelined suffix.
          _ <-
            if (first.status == 411) IO.unit
            else {
              for {
                _ <- Check.equal(first.status, 200)
                _ <- Check.equal(first.body, Http1Wire.ascii("one"))
                second <- nextPipelineResponse(peer)
                _ <- Check.equal(second.status, 200)
                _ <- Check.equal(second.body, Http1Wire.ascii("three"))
                firstRequest <- server.received
                secondRequest <- server.received
                _ <- Check.equal(firstRequest.path, "/first")
                _ <- Check.equal(secondRequest.path, "/second")
                _ <- expectTransportClose(peer)
              } yield ()
            }
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3.2
    WireCase(
      "h1.server.pipeline-fixed-length-then-chunked",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "9.3.2",
        "Pipelined requests are delimited independently and their responses preserve request order.",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          // Both requests are sent before either response is read; bodies identify their order.
          _ <- peer.writeAscii(
            "POST /first HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 3\r\n\r\nonePOST /second HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n5\r\nthree\r\n0\r\n\r\n"
          )
          first <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(first.status, 200)
          _ <- Check.equal(first.body, Http1Wire.ascii("one"))
          second <- nextPipelineResponse(peer)

          firstRequest <- server.received

          _ <- Check.equal(firstRequest.path, "/first")
          _ <-
            if (second.status == 411) IO.unit
            else
              for {
                _ <- Check.equal(second.status, 200)
                _ <- Check.equal(second.body, Http1Wire.ascii("three"))
                secondRequest <- server.received
                _ <- Check.equal(secondRequest.path, "/second")
              } yield ()
          _ <- expectTransportClose(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.server.request-close-stops-pipeline",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "9.6",
        "A server receiving close completes that response, closes, and processes no later request.",
      ),
    ) { context =>
      context.server(Protocol.Http1).use { case (peer, server) =>
        for {
          // Sending both heads together makes the forbidden request available before closure.
          _ <- peer.writeAscii(
            "GET /last HTTP/1.1\r\nHost: wire.test\r\nConnection: close\r\n\r\nGET /forbidden HTTP/1.1\r\nHost: wire.test\r\n\r\n"
          )
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          _ <- Check.equal(response.body, Http1Wire.ascii("accepted"))
          observed <- server.received
          _ <- Check.equal(observed.path, "/last")
          _ <- expectTransportClose(peer)
          extra <- server.pollReceived
          _ <- Check(extra.isEmpty, s"a request after close reached the application: $extra")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-7.6.1
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.server.request-close-case-insensitive",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9110,
        "7.6.1",
        "Connection options are case-insensitive when deciding to stop request processing.",
      ),
    ) { context =>
      context.server(Protocol.Http1).use { case (peer, server) =>
        for {
          // Sending both heads together makes the forbidden request available before closure.
          _ <- peer.writeAscii(
            "GET /last HTTP/1.1\r\nHost: wire.test\r\ncOnNeCtIoN: ClOsE\r\n\r\nGET /forbidden HTTP/1.1\r\nHost: wire.test\r\n\r\n"
          )
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          _ <- Check.equal(response.body, Http1Wire.ascii("accepted"))
          observed <- server.received
          _ <- Check.equal(observed.path, "/last")
          _ <- expectTransportClose(peer)
          extra <- server.pollReceived
          _ <- Check(extra.isEmpty, s"a request after close reached the application: $extra")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.server.request-close-option-list",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "9.3",
        "The close option takes precedence over keep-alive in a request option list.",
      ),
    ) { context =>
      context.server(Protocol.Http1).use { case (peer, server) =>
        for {
          // Sending both heads together makes the forbidden request available before closure.
          _ <- peer.writeAscii(
            "GET /last HTTP/1.1\r\nHost: wire.test\r\nConnection: keep-alive, close\r\n\r\nGET /forbidden HTTP/1.1\r\nHost: wire.test\r\n\r\n"
          )
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          _ <- Check.equal(response.body, Http1Wire.ascii("accepted"))
          observed <- server.received
          _ <- Check.equal(observed.path, "/last")
          _ <- expectTransportClose(peer)
          extra <- server.pollReceived
          _ <- Check(extra.isEmpty, s"a request after close reached the application: $extra")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-7.6.1
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.server.request-close-repeated-field",
      Protocol.Http1,
      Role.Server,
      Rfc(9110, "7.6.1", "A close option in a later Connection field stops request processing."),
    ) { context =>
      context.server(Protocol.Http1).use { case (peer, server) =>
        for {
          // Sending both heads together makes the forbidden request available before closure.
          _ <- peer.writeAscii(
            "GET /last HTTP/1.1\r\nHost: wire.test\r\nConnection: keep-alive\r\nConnection: close\r\n\r\nGET /forbidden HTTP/1.1\r\nHost: wire.test\r\n\r\n"
          )
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          _ <- Check.equal(response.body, Http1Wire.ascii("accepted"))
          observed <- server.received
          _ <- Check.equal(observed.path, "/last")
          _ <- expectTransportClose(peer)
          extra <- server.pollReceived
          _ <- Check(extra.isEmpty, s"a request after close reached the application: $extra")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6
    WireCase(
      "h1.server.response-close-stops-pipeline",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "9.6",
        "A server sending close completes its response and processes no further requests.",
      ),
    ) { context =>
      val scenario = ServerScenario.Reply(headers = Vector("Connection" -> "close"))
      context.server(Protocol.Http1, scenario).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "GET /last HTTP/1.1\r\nHost: wire.test\r\n\r\n" +
              "GET /forbidden HTTP/1.1\r\nHost: wire.test\r\n\r\n"
          )
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          _ <- Check.equal(response.body, Http1Wire.ascii("accepted"))
          _ <- Check(hasOption(response.headers, "close"), "the response must carry close")
          observed <- server.received
          _ <- Check.equal(observed.path, "/last")
          _ <- expectTransportClose(peer)
          extra <- server.pollReceived
          _ <- Check(
            extra.isEmpty,
            s"a request after response close reached the application: $extra",
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3
    WireCase(
      "h1.server.http10-default-close",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "9.3", "HTTP/1.0 without keep-alive closes after the response."),
    ) { context =>
      context.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET /last HTTP/1.0\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          _ <- Check.equal(response.body, Http1Wire.ascii("accepted"))
          observed <- server.received
          _ <- Check.equal(observed.path, "/last")
          _ <- expectTransportClose(peer)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3
    WireCase(
      "h1.server.http10-close-overrides-keep-alive",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "9.3", "An HTTP/1.0 close option takes precedence over keep-alive."),
    ) { context =>
      context.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "GET /last HTTP/1.0\r\nHost: wire.test\r\nConnection: keep-alive, close\r\n\r\n"
          )
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          _ <- Check.equal(response.body, Http1Wire.ascii("accepted"))
          observed <- server.received
          _ <- Check.equal(observed.path, "/last")
          _ <- expectTransportClose(peer)
        } yield ()
      }
    },

    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3
    WireCase(
      "h1.client.sequential-fixed-length-responses",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "9.3",
        "Persistent clients consume each entire response before reusing the connection.",
        "SHOULD",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        (
          for {
            _ <- driver.exchange(RequestData(path = "/first")).use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Http1Wire.ascii("one"))
              } yield ()
            }
            _ <- driver.exchange(RequestData(path = "/second")).use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Http1Wire.ascii("three"))
              } yield ()
            }
          } yield ()
        ).handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          first <- Http1Wire.readRequest(peer)
          _ <- Check.equal(first.target, "/first")
          _ <- Check(
            !hasOption(first.headers, "close"),
            "persistent support requires no request close",
          )
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\none")
          second <- Http1Wire.readRequest(peer)
          _ <- Check.equal(second.target, "/second")
          _ <- peer.writeAscii(
            "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nthree"
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3
    WireCase(
      "h1.client.sequential-chunked-then-fixed-length",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "9.3",
        "Persistent clients consume each entire response before reusing the connection.",
        "SHOULD",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        (
          for {
            _ <- driver.exchange(RequestData(path = "/first")).use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Http1Wire.ascii("one"))
              } yield ()
            }
            _ <- driver.exchange(RequestData(path = "/second")).use { response =>
              for {
                body <- response.body.compile.to(Chunk)
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(body.toByteVector, Http1Wire.ascii("three"))
              } yield ()
            }
          } yield ()
        ).handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          first <- Http1Wire.readRequest(peer)
          _ <- Check.equal(first.target, "/first")
          _ <- Check(
            !hasOption(first.headers, "close"),
            "persistent support requires no request close",
          )
          _ <- peer.writeAscii(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\none\r\n0\r\nX-Wire: trailer\r\n\r\n"
          )
          second <- Http1Wire.readRequest(peer)
          _ <- Check.equal(second.target, "/second")
          _ <- peer.writeAscii(
            "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nthree"
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3
    WireCase(
      "h1.server.drain-unread-fixed-length-or-close",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "9.3", "A server responding before reading the body must drain that body or close."),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        val body = Http1Wire.ascii("GET /forbidden HTTP/1.1\r\nHost: wire.test\r\n\r\n")
        val first = Http1Wire.message(
          "POST /first HTTP/1.1",
          Vector("Host" -> "wire.test", "Content-Length" -> body.size.toString),
          body,
        )
        for {
          // The application ignores the body. A complete request-shaped body must stay body data.
          _ <- peer.write(
            first ++ Http1Wire.ascii(
              "GET /second HTTP/1.1\r\nHost: wire.test\r\nConnection: close\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          _ <- Check.equal(response.body, Http1Wire.ascii("accepted"))
          observed <- server.received
          _ <- Check.equal(observed.path, "/first")
          next <- Http1Wire
            .readFinalResponse(peer)
            .attempt
            .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          processedSecond <- next match {
            case Right(second) =>
              for {
                _ <- Check.equal(second.status, 200)
                _ <- Check.equal(second.body, Http1Wire.ascii("accepted"))
                nextRequest <- server.received
                _ <- Check.equal(nextRequest.path, "/second")
              } yield true
            // Closing instead of draining is expressly permitted by Section 9.3.
            case Left(error: WireDecodingFailure.Truncated)
                if error.boundary.isInstanceOf[WireReadFailure.Delimiter] =>
              IO.pure(false)
            case Left(error) if RuntimeInfo.isConnectionReset(error) => IO.pure(false)
            case Left(error) => IO.raiseError(error)
          }
          _ <- expectTransportClose(peer)
          extra <- server.pollReceived
          _ <- Check(
            if (processedSecond) extra.isEmpty else extra.forall(_.path == "/second"),
            s"request-body bytes were processed as another request: $extra",
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3
    WireCase(
      "h1.server.drain-unread-chunked-or-close",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "9.3", "A server responding before reading the body must drain that body or close."),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        val body = Http1Wire.ascii("GET /forbidden HTTP/1.1\r\nHost: wire.test\r\n\r\n")
        val chunks = Http1Wire.ascii(body.size.toHexString + "\r\n") ++ body ++
          Http1Wire.ascii("\r\n0\r\nX-Wire: trailer\r\n\r\n")
        val first = Http1Wire.message(
          "POST /first HTTP/1.1",
          Vector("Host" -> "wire.test", "Transfer-Encoding" -> "chunked"),
          chunks,
        )
        for {
          // The application ignores the body. A complete request-shaped body must stay body data.
          _ <- peer.write(
            first ++ Http1Wire.ascii(
              "GET /second HTTP/1.1\r\nHost: wire.test\r\nConnection: close\r\n\r\n"
            )
          )
          response <- Http1Wire.readFinalResponse(peer)
          // RFC 9112 Section 6.3 permits 411 without establishing chunk decoding coverage.
          _ <-
            if (response.status == 411)
              checkLengthRequiredBoundary(peer, server, Http1Wire.ascii("accepted"))
            else {
              for {
                _ <- Check.equal(response.status, 200)
                _ <- Check.equal(response.body, Http1Wire.ascii("accepted"))
                observed <- server.received
                _ <- Check.equal(observed.path, "/first")
                next <- Http1Wire
                  .readFinalResponse(peer)
                  .attempt
                  .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
                processedSecond <- next match {
                  case Right(second) =>
                    for {
                      _ <- Check.equal(second.status, 200)
                      _ <- Check.equal(second.body, Http1Wire.ascii("accepted"))
                      nextRequest <- server.received
                      _ <- Check.equal(nextRequest.path, "/second")
                    } yield true
                  // Closing instead of draining is expressly permitted by Section 9.3.
                  case Left(error: WireDecodingFailure.Truncated)
                      if error.boundary.isInstanceOf[WireReadFailure.Delimiter] =>
                    IO.pure(false)
                  case Left(error) if RuntimeInfo.isConnectionReset(error) => IO.pure(false)
                  case Left(error) => IO.raiseError(error)
                }
                _ <- expectTransportClose(peer)
                extra <- server.pollReceived
                _ <- Check(
                  if (processedSecond) extra.isEmpty else extra.forall(_.path == "/second"),
                  s"request-body bytes were processed as another request: $extra",
                )
              } yield ()
            }
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.5
    WireCase(
      "h1.client.error-response-during-upload",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "9.5",
        "A client monitors responses during upload and stops sending when an error closes the connection.",
        "SHOULD",
      ),
    ) { context =>
      // A fixed bounded prefix crosses ordinary write coalescing without requiring tiny
      // application chunks to flush. Larger buffers can still leave the stimulus unobserved;
      // RFC 9112 Section 9.5 recommends monitoring, not a particular flushing threshold.
      val uploadPrefix = Http1Wire.ascii("a" * (32 * 1024))
      val uploadSuffix = Http1Wire.ascii("def")
      val contentLength = (uploadPrefix.size + uploadSuffix.size).toString
      for {
        releaseBody <- Deferred[IO, Unit]
        bodyStopped <- Deferred[IO, Unit]
        closed <- Deferred[IO, Unit]
        _ <- context.client(Protocol.Http1) { driver =>
          val body = (
            Stream.chunk(Chunk.byteVector(uploadPrefix)) ++
              Stream.eval(releaseBody.get).drain ++
              Stream.chunk(Chunk.byteVector(uploadSuffix))
          ).onFinalize(bodyStopped.complete(()).void)
          driver
            .exchangeStream(
              RequestData(method = "POST", headers = Vector("Content-Length" -> contentLength)),
              _ => body,
            )
            .use { response =>
              response.body.compile.drain *> Check.equal(response.status, 413)
            }
            .handleErrorWith(requiredAcceptanceFailure) *> bodyStopped.get *> closed.get
        } { peer =>
          for {
            head <- peer.readUntil(Http1Wire.ascii("\r\n\r\n"))
            rendered = head.toArray.iterator.map(b => (b & 0xff).toChar).mkString
            _ <- Check(
              rendered.startsWith("POST / HTTP/1.1\r\n"),
              "expected the upload request head",
            )
            _ <- Check(
              rendered.split("\r\n").exists { line =>
                val colon = line.indexOf(':')
                colon > 0 && line.take(colon).equalsIgnoreCase("Content-Length") &&
                line.drop(colon + 1).trim == contentLength
              },
              "the upload must declare its prefix plus the withheld suffix",
            )
            prefix <- peer.readExact(uploadPrefix.size.toInt)
            _ <- Check.equal(prefix, uploadPrefix)
            // The remaining three octets stay blocked: no path releases releaseBody. Receipt
            // of the complete prefix establishes the stimulus before sending 413. A deadline
            // remains inconclusive; the transcript distinguishes an unestablished stimulus
            // from a post-response observation limit, not a buffering or monitoring verdict.
            _ <- peer.writeAscii(
              "HTTP/1.1 413 Content Too Large\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            )
            _ <- expectTransportClose(peer)
            _ <- closed.complete(()).void
          } yield ()
        }
      } yield ()
    },
  )

  private def requiredAcceptanceFailure(error: Throwable): IO[Unit] =
    RawPeer.raiseAdapterOrTimeout[IO](error) *>
      (if (
         error.isInstanceOf[AssertionError] || error
           .isInstanceOf[WireObservationLimit]
       ) IO.raiseError(error)
       else Check(false, s"the target rejected a valid HTTP/1 exchange: $error"))

  /** RFC 9112 Sections 9.3.1 and 9.6 permit closure; it cannot establish pipeline ordering. */
  private def nextPipelineResponse(peer: RawPeer[IO]): IO[Http1Wire.Response] =
    Http1Wire.readFinalResponse(peer).handleErrorWith { error =>
      RawPeer.raiseAdapterOrTimeout[IO](error) *> (error match {
        case _: WireDecodingFailure.Truncated =>
          IO.raiseError(
            new IllegalStateException(
              "coverage prerequisite unavailable: pipeline connection closed",
              error,
            )
          )
        case failure if RuntimeInfo.isConnectionReset(failure) =>
          IO.raiseError(
            new IllegalStateException(
              "coverage prerequisite unavailable: pipeline connection reset",
              error,
            )
          )
        case other => IO.raiseError(other)
      })
    }

  /** A 411 policy does not waive Section 9.3's request boundary requirement.
    * Closure or a correctly delimited second response is followed by application observations.
    */
  private[wire] def checkLengthRequiredBoundary(
      peer: RawPeer[IO],
      server: ServerHandle[IO],
      secondBody: ByteVector,
  ): IO[Unit] = {
    def observations(previous: Int): IO[Unit] = server.pollReceived.flatMap {
      case None => IO.unit
      case Some(request) =>
        val index = if (request.path == "/first") 1 else if (request.path == "/second") 2 else 0
        Check(
          index > previous,
          s"request-body bytes or duplicate requests reached the application: $request",
        ) *>
          IO.defer(observations(index))
    }
    for {
      next <- Http1Wire.readFinalResponse(peer).attempt
      _ <- next match {
        case Right(response) =>
          Check.equal(response.status, 200) *> Check.equal(response.body, secondBody) *>
            expectTransportClose(peer)
        case Left(error) =>
          RawPeer.raiseAdapterOrTimeout[IO](error) *> (error match {
            case _: WireDecodingFailure.Truncated =>
              IO.unit
            case failure if RuntimeInfo.isConnectionReset(failure) => IO.unit
            case other => IO.raiseError(other)
          })
      }
      _ <- observations(0)
    } yield ()
  }

  private def hasOption(headers: Vector[(String, String)], option: String): Boolean =
    headers.exists { case (name, value) =>
      name.equalsIgnoreCase("connection") &&
      value.split(",", -1).exists(_.trim.equalsIgnoreCase(option))
    }

  private def rejectIncompleteResponse(driver: ClientDriver[IO]): IO[Unit] =
    // A non-idempotent, empty request avoids relying on automatic GET replay after EOF.
    // Only the target operation is attempted; scripted-peer failures remain fixture errors.
    driver
      .exchange(RequestData(method = "POST", headers = Vector("Content-Length" -> "0")))
      .use(response => response.body.compile.drain *> response.trailers.void)
      .attempt
      .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
      .flatMap {
        case Left(error) =>
          RawPeer.raiseAdapterOrTimeout[IO](error) *> (error match {
            case limit: WireObservationLimit => IO.raiseError(limit)
            case assertion: AssertionError => IO.raiseError(assertion)
            case _ => IO.unit
          })
        case Right(_) => Check(false, "the target completed an incomplete response successfully")
      }

  private def expectTransportClose(peer: RawPeer[IO]): IO[Unit] =
    // Used only after the complete request/response stimulus, never around fixture setup.
    // A reset is transport closure; a timeout or arbitrary read error is not.
    peer
      .readToEnd(0)
      .attempt
      .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
      .flatMap {
        case Right(bytes) => Check.equal(bytes, ByteVector.empty)
        case Left(WireBodyLimit(0)) =>
          Check(false, "received additional bytes where connection closure was required")
        case Left(error) if RuntimeInfo.isConnectionReset(error) => IO.unit
        case Left(error) => IO.raiseError(error)
      }

  private def expectIncompleteRequestClosed(
      peer: RawPeer[IO],
      server: ServerHandle[IO],
  ): IO[Unit] =
    for {
      bytes <- readThroughClose(peer)
      _ <- checkOptionalErrorResponse(bytes)
      observed <- server.pollReceived
      _ <- Check(
        observed.isEmpty,
        s"EchoBody reported a complete request after an incomplete upload: $observed",
      )
    } yield ()

  /** Retains bytes preceding closure, in bounded chunks, so reset cannot hide a successful response. */
  private[wire] def readThroughClose(peer: RawPeer[IO]): IO[ByteVector] =
    peer.readToEnd(65536, allowReset = true)

  private def checkOptionalErrorResponse(bytes: ByteVector): IO[Unit] = {
    // Section 8 permits silent closure or an error response. Informational responses may
    // precede that error. The body of an error response is outside this completion assertion.
    def loop(remaining: ByteVector, informational: Int): IO[Unit] =
      if (remaining.isEmpty) IO.unit
      else {
        val end = remaining.indexOfSlice(Http1Wire.ascii("\r\n\r\n"))
        val lineEnd = remaining.indexOfSlice(Http1Wire.crlf)
        for {
          _ <- Check(end >= 0L && lineEnd >= 0L, "server emitted an incomplete error response head")
          line = remaining.take(lineEnd).toArray.iterator.map(b => (b & 0xff).toChar).mkString
          valid = line.length >= 13 &&
            (line.startsWith("HTTP/1.1 ") || line.startsWith("HTTP/1.0 ")) && line(12) == ' ' &&
            line.substring(9, 12).forall(c => c >= '0' && c <= '9') &&
            line.drop(13).forall(c => c == '\t' || c >= ' ' && c != 127)
          _ <- Check(valid, s"invalid response status line after incomplete request: $line")
          fields = remaining
            .take(end)
            .toArray
            .iterator
            .map(b => (b & 0xff).toChar)
            .mkString
            .split("\r\n", -1)
            .toVector
            .tail
          _ <- fields.traverse_(field => IO(Http1Wire.parseHeader(field)).void)
          status = line.substring(9, 12).toInt
          _ <-
            if (status >= 100 && status < 200 && status != 101)
              if (informational >= 16)
                IO.raiseError(new WireObservationLimit("informational response observation limit"))
              else IO.defer(loop(remaining.drop(end + 4L), informational + 1))
            else
              Check(
                status >= 400 && status <= 599,
                s"expected an error or silent close for an incomplete request, received $status",
              )
        } yield ()
      }
    loop(bytes, 0)
  }
}
