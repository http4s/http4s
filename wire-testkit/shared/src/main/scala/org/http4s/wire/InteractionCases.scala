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

/** Message boundaries and connection/stream sequencing across the individual protocol rules. */
object InteractionCases {
  private val Binary = ByteVector(0, 13, 10, 127, 128, 255)
  private val Following = Http1Wire.ascii("following")
  private val RequestShaped =
    Http1Wire.ascii("GET /inside-body HTTP/1.1\r\nHost: wire.test\r\n\r\n") ++ Binary
  private val ResponseShaped =
    Http1Wire.ascii("HTTP/1.1 299 Body Only\r\nContent-Length: 0\r\n\r\n") ++ Binary
  private val PingBytes = ByteVector(41, 43, 47, 53, 59, 61, 67, 71)

  val all: Vector[WireCase] = Vector(
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.interaction-length-body-request-line",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "A request line inside a fixed-length body does not start the next pipelined request.",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        val first = Http1Wire.message(
          "POST /first HTTP/1.1",
          Vector("Host" -> "wire.test", "Content-Length" -> RequestShaped.size.toString),
          RequestShaped,
        )
        for {
          _ <- peer.write(first ++ followingRequest)
          _ <- checkPipeline(peer, server, RequestShaped, Following)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.server.interaction-chunk-body-request-line",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "7.1",
        "Chunk data and trailers end before the following request even when content resembles a request line.",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        val first = Http1Wire.message(
          "POST /first HTTP/1.1",
          Vector("Host" -> "wire.test", "Transfer-Encoding" -> "chunked"),
          chunked(RequestShaped, "X-Fence: GET /trailer HTTP/1.1\r\n"),
        )
        for {
          _ <- peer.write(first ++ followingRequest)
          _ <- checkPipeline(peer, server, RequestShaped, Following)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.interaction-unframed-post-next-request",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "A POST without framing fields has no body; the following request starts immediately after its header section.",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii("POST /first HTTP/1.1\r\nHost: wire.test\r\n\r\n") ++ followingRequest
          )
          _ <- checkPipeline(peer, server, ByteVector.empty, Following)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.interaction-conflicting-length-stops-pipeline",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "Conflicting Content-Length values require 400 and closure before a pipelined suffix can be dispatched.",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /invalid HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 0\r\nContent-Length: 1\r\n\r\n"
            ) ++ followingRequest
          )
          response <- observed(Http1Wire.readFinalResponse(peer))
          _ <- Check.equal(response.status, 400)
          _ <- expectClosed(peer)
          received <- server.pollReceived
          _ <- Check(
            received.isEmpty,
            s"invalid framing or its suffix reached the application: $received",
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.server.interaction-nonfinal-chunked-stops-pipeline",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.3",
        "A request with transfer coding not ending in chunked requires 400 and closure before another request.",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          // gzip is a registered coding; the framing fault is the absence of final chunked.
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /invalid HTTP/1.1\r\nHost: wire.test\r\nTransfer-Encoding: gzip\r\n\r\n"
            ) ++
              ByteVector
                .fromValidHex("1f8b080000000000000303000000000000000000") ++ followingRequest
          )
          response <- observed(Http1Wire.readFinalResponse(peer))
          _ <- Check.equal(response.status, 400)
          _ <- expectClosed(peer)
          received <- server.pollReceived
          _ <- Check(
            received.isEmpty,
            s"undelimited request or its suffix reached the application: $received",
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
    WireCase(
      "h1.server.interaction-framing-name-whitespace-stops-pipeline",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "5.1",
        "Whitespace before a framing field colon requires 400 instead of interpreting its body as another request.",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii(
              "POST /invalid HTTP/1.1\r\nHost: wire.test\r\nContent-Length : 0\r\nConnection: close\r\n\r\n"
            ) ++ followingRequest
          )
          response <- observed(Http1Wire.readFinalResponse(peer))
          _ <- Check.equal(response.status, 400)
          // The explicit close option supplies the closure obligation independently of 5.1.
          _ <- expectClosed(peer)
          received <- server.pollReceived
          _ <- Check(
            received.isEmpty,
            s"malformed field or its suffix reached the application: $received",
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.1
    WireCase(
      "h1.server.interaction-transfer-length-stops-pipeline",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "6.1",
        "A request with Transfer-Encoding and Content-Length may be rejected or decoded by Transfer-Encoding, but the connection must close.",
      ),
    ) { context =>
      context.server(Protocol.Http1, ServerScenario.EchoBody).use { case (peer, server) =>
        val first = Http1Wire.message(
          "POST /first HTTP/1.1",
          Vector("Host" -> "wire.test", "Transfer-Encoding" -> "chunked", "Content-Length" -> "0"),
          chunked(RequestShaped),
        )
        for {
          _ <- peer.write(first ++ followingRequest)
          response <- observed(Http1Wire.readFinalResponse(peer))
          _ <-
            if (response.status == 200)
              for {
                _ <- Check.equal(response.body, RequestShaped)
                received <- server.received
                _ <- Check.equal(received.path, "/first")
                _ <- Check.equal(received.body, RequestShaped)
              } yield ()
            else
              Check(
                response.status >= 400 && response.status <= 599,
                "expected acceptance or an error response",
              )
          _ <- expectClosed(peer)
          extra <- server.pollReceived
          _ <- Check(
            extra.isEmpty,
            s"ambiguous framing allowed an additional application request: $extra",
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.interaction-head-length-next-response",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "Content-Length on HEAD is metadata and must not consume bytes belonging to the subsequent response.",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        observed(
          checkExchange(
            driver,
            RequestData(method = "HEAD", path = "/first"),
            200,
            ByteVector.empty,
          ) *>
            checkExchange(driver, RequestData(path = "/second"), 201, Following)
        )
      ) { peer =>
        serveH1Pair(peer, "HEAD", Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 123\r\n\r\n"))
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.interaction-head-chunked-next-response",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "Transfer-Encoding on HEAD does not require chunks before the next response on a reused connection.",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        observed(
          checkExchange(
            driver,
            RequestData(method = "HEAD", path = "/first"),
            200,
            ByteVector.empty,
          ) *>
            checkExchange(driver, RequestData(path = "/second"), 201, Following)
        )
      ) { peer =>
        serveH1Pair(
          peer,
          "HEAD",
          Http1Wire.ascii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"),
        )
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.interaction-not-modified-next-response",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A 304 response ends at its empty line; its Content-Length cannot absorb the following response.",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        observed(
          checkExchange(driver, RequestData(path = "/first"), 304, ByteVector.empty) *>
            checkExchange(driver, RequestData(path = "/second"), 201, Following)
        )
      ) { peer =>
        serveH1Pair(
          peer,
          "GET",
          Http1Wire.ascii("HTTP/1.1 304 Not Modified\r\nContent-Length: 123\r\n\r\n"),
        )
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.interaction-no-content-next-response",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A 204 response without framing fields ends at its empty line and permits a subsequent response.",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        observed(
          checkExchange(driver, RequestData(path = "/first"), 204, ByteVector.empty) *>
            checkExchange(driver, RequestData(path = "/second"), 201, Following)
        )
      ) { peer =>
        serveH1Pair(peer, "GET", Http1Wire.ascii("HTTP/1.1 204 No Content\r\n\r\n"))
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
    WireCase(
      "h1.client.interaction-length-body-status-line",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "6.3",
        "A status line inside fixed-length content is body data and cannot become a subsequent response.",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        observed(
          checkExchange(driver, RequestData(path = "/first"), 200, ResponseShaped) *>
            checkExchange(driver, RequestData(path = "/second"), 201, Following)
        )
      ) { peer =>
        serveH1Pair(
          peer,
          "GET",
          Http1Wire.message(
            "HTTP/1.1 200 OK",
            Vector("Content-Length" -> ResponseShaped.size.toString),
            ResponseShaped,
          ),
        )
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
    WireCase(
      "h1.client.interaction-chunk-trailer-next-response",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "7.1",
        "Response-shaped chunk data and ordered trailers remain associated with the first response before reuse.",
      ),
    ) { context =>
      context.client(Protocol.Http1)(driver =>
        observed(
          driver.exchange(RequestData(path = "/first")).use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              trailers <- response.trailers
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, ResponseShaped)
              _ <- Check.equal(
                values(trailers, "x-fence").flatMap(_.split(",", -1).map(_.trim)),
                Vector("first", "second"),
              )
            } yield ()
          } *> checkExchange(driver, RequestData(path = "/second"), 201, Following)
        )
      ) { peer =>
        serveH1Pair(
          peer,
          "GET",
          Http1Wire.message(
            "HTTP/1.1 200 OK",
            Vector("Transfer-Encoding" -> "chunked", "Trailer" -> "X-Fence"),
            chunked(ResponseShaped, "X-Fence: first\r\nX-Fence: second\r\n"),
          ),
        )
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.2
    WireCase(
      "h1.client.interaction-interim-next-request-association",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "9.2",
        "Interim and final responses to one request cannot be associated with a later request on the connection.",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { secondSeen =>
        context.client(Protocol.Http1)(driver =>
          observed {
            for {
              _ <- driver.exchange(RequestData(path = "/first")).use { response =>
                response.body.compile.to(Chunk).flatMap { body =>
                  // The frozen API may expose a 1xx. It need not expose the final response here.
                  if (response.status == 100 || response.status == 103)
                    IO.unit
                  else Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Binary)
                }
              }
              _ <- driver.exchange(RequestData(path = "/second")).use { response =>
                for {
                  _ <- secondSeen.get
                  body <- response.body.compile.to(Chunk)
                  _ <- Check.equal(response.status, 201)
                  _ <- Check.equal(body.toByteVector, Following)
                } yield ()
              }
            } yield ()
          }
        ) { peer =>
          for {
            first <- Http1Wire.readRequest(peer)
            _ <- Check.equal(first.target, "/first")
            _ <- requirePersistence(first.headers)
            _ <- peer.write(
              Http1Wire.ascii(
                "HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 103 Early Hints\r\nX-Interim: first\r\n\r\n"
              ) ++ Http1Wire.message(
                "HTTP/1.1 200 OK",
                Vector("Content-Length" -> Binary.size.toString),
                Binary,
              )
            )
            second <- Http1Wire.readRequest(peer)
            _ <- Check.equal(second.target, "/second")
            _ <- secondSeen.complete(()).void
            _ <- peer.write(finalH1Response)
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4
    WireCase(
      "h2.client.interaction-eager-server-settings",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "3.4",
        "An eager server SETTINGS preface can arrive while the client sends its preface and must be acknowledged after that preface.",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        observed(checkExchange(driver, RequestData(path = "/eager"), 200, Binary))
      ) { peer =>
        for {
          // Deliberately write before reading ANY client octet. serverHandshake would hide this ordering.
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings())
          _ <- receiveClientPreface(peer)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))
          decoder <- HpackWire.decoder[IO]()
          request <- readClientRequest(peer, decoder, awaitAck = true)
          _ <- Check.equal(values(request.fields, ":path"), Vector("/eager"))
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- peer.write(
            Http2Wire.encode(
              Http2Wire.headers(
                request.streamId,
                Vector(":status" -> "200", "content-length" -> Binary.size.toString),
                endStream = false,
              )
            ) ++ Http2Wire.encode(Http2Wire.data(request.streamId, Binary))
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4
    WireCase(
      "h2.client.interaction-eager-zero-window-upload",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "3.4",
        "An eager initial SETTINGS window applies to DATA released after its acknowledgment, and WINDOW_UPDATE permits progress.",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { release =>
        context.client(Protocol.Http2)(driver =>
          observed {
            driver
              .exchangeStream(
                RequestData(
                  method = "POST",
                  path = "/eager",
                  headers = Vector("content-length" -> Binary.size.toString),
                ),
                Stream.eval(release.get).drain ++ Stream.chunk(Chunk.byteVector(Binary)),
              )
              .use { response =>
                response.body.compile.to(Chunk).flatMap { body =>
                  Check.equal(response.status, 200) *> Check
                    .equal(body.toByteVector, ByteVector.empty)
                }
              }
          }
        ) { peer =>
          for {
            _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(Vector(4 -> 0L)))
            _ <- receiveClientPreface(peer)
            _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))
            decoder <- HpackWire.decoder[IO]()
            request <- readClientRequest(peer, decoder, awaitAck = true, incomplete = true)
            _ <- Check.equal(values(request.fields, ":method"), Vector("POST"))
            _ <- Check(
              !request.ended && request.body.isEmpty,
              "upload must remain open before its body is released",
            )
            _ <- release.complete(()).void
            // Every intervening frame is inspected; a PING ACK establishes connection liveness.
            _ <- pingBarrier(peer, Some(request.streamId))
            _ <- Http2Wire.writeFrame(peer, Http2Wire.windowUpdate(request.streamId, 65535))
            body <- readUpload(peer, request.streamId, ByteVector.empty)
            _ <- Check.equal(body, Binary)
            _ <- Http2Wire.writeFrame(
              peer,
              Http2Wire.headers(request.streamId, Vector(":status" -> "200")),
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4
    WireCase(
      "h2.server.interaction-coalesced-preface-request",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "3.4",
        "A client may send a complete request immediately after its preface without awaiting server SETTINGS.",
        "MAY",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http2Wire.clientPreface ++ Http2Wire.encode(Http2Wire.settings()) ++
              Http2Wire.encode(
                Http2Wire.headers(1, requestFields("/early", Binary.size), endStream = false)
              ) ++
              Http2Wire.encode(Http2Wire.data(1, Binary))
          )
          initial <- observed(Http2Wire.readFrame(peer))
          _ <- Check(
            initial.frameType == Http2Wire.Settings && !initial.hasFlag(Http2Wire.Ack),
            "server preface must be non-ACK SETTINGS",
          )
          _ <- control(peer, initial)
          responses <- readResponses(peer, Set(1))
          _ <- checkResponse(responses(1), Binary)
          received <- server.received
          _ <- Check.equal(received.path, "/early")
          _ <- Check.equal(received.body, Binary)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-5
    WireCase(
      "h2.server.interaction-interleaved-request-data",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "5",
        "Interleaved DATA belongs to its stream, including when the later stream reaches END_STREAM first.",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          settings <- connectServer(peer)
          _ <- requireTwoStreams(settings)
          _ <- peer.write(
            frames(
              Http2Wire.headers(1, requestFields("/one", Binary.size), endStream = false),
              Http2Wire.headers(3, requestFields("/two", Following.size), endStream = false),
              Http2Wire.data(1, Binary.take(2), endStream = false),
              Http2Wire.data(3, Following.take(3), endStream = false),
              Http2Wire.data(3, Following.drop(3)),
              Http2Wire.data(1, Binary.drop(2)),
            )
          )
          // The server can choose either response order, and may interleave its response frames.
          responses <- readResponses(peer, Set(1, 3))
          _ <- checkResponse(responses(1), Binary)
          _ <- checkResponse(responses(3), Following)
          _ <- checkTwoRequests(server)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.server.interaction-trailers-between-other-stream-data",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "8.1",
        "Trailers complete their own stream without ending or contaminating another stream's DATA sequence.",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          settings <- connectServer(peer)
          _ <- requireTwoStreams(settings)
          _ <- peer.write(
            frames(
              Http2Wire.headers(1, requestFields("/one", Binary.size), endStream = false),
              Http2Wire.headers(3, requestFields("/two", Following.size), endStream = false),
              Http2Wire.data(1, Binary, endStream = false),
              Http2Wire.data(3, Following.take(3), endStream = false),
              Http2Wire.headers(1, Vector("x-fence" -> "one", "x-fence" -> "two")),
              Http2Wire.data(3, Following.drop(3)),
            )
          )
          responses <- readResponses(peer, Set(1, 3))
          _ <- checkResponse(responses(1), Binary)
          _ <- checkResponse(responses(3), Following)
          _ <- checkTwoRequests(server)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4
    WireCase(
      "h2.server.interaction-reset-other-upload-stream",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "6.4",
        "Resetting one incomplete upload closes that stream while the other stream can complete its message.",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          settings <- connectServer(peer)
          _ <- requireTwoStreams(settings)
          _ <- peer.write(
            frames(
              Http2Wire.headers(1, requestFields("/cancelled", Binary.size), endStream = false),
              Http2Wire.headers(3, requestFields("/two", Following.size), endStream = false),
              Http2Wire.data(1, Binary.take(2), endStream = false),
              Http2Wire.data(3, Following.take(3), endStream = false),
              Http2Wire.Frame(Http2Wire.RstStream, 0, 1, Http2Wire.uint32(8L)),
              Http2Wire.data(3, Following.drop(3)),
            )
          )
          responses <- readResponses(peer, Set(3))
          _ <- checkResponse(responses(3), Following)
          _ <- pingBarrier(peer)
          received <- server.received
          _ <- Check.equal(received.path, "/two")
          _ <- Check.equal(received.body, Following)
          extra <- server.pollReceived
          _ <- Check(
            extra.isEmpty,
            s"the cancelled incomplete upload was observed as complete: $extra",
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3
    WireCase(
      "h2.server.interaction-reset-retains-compression-context",
      Protocol.Http2,
      Role.Server,
      Rfc(
        9113,
        "4.3",
        "The connection compression context retains a valid dynamic entry after its request stream is reset.",
      ),
    ) { context =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          settings <- connectServer(peer)
          _ <- prerequisite(
            settings.reverse.collectFirst { case (1, size) => size }.forall(_ >= 43L),
            "the advertised HPACK table cannot hold the 43-octet x-state entry",
          )
          _ <- prerequisite(
            settings.reverse.collectFirst { case (3, count) => count }.forall(_ >= 1L),
            "the peer does not permit a request stream",
          )
          // 40 = literal with incremental indexing and a new name. Index 62 (be) is then newest.
          insertion = ByteVector.fromValidHex("4007782d7374617465046b657074")
          capacity = settings.reverse
            .collectFirst { case (1, size) => size }
            .getOrElse(4096L)
            .min(4096L)
            .toInt
          resize =
            if (capacity == 4096) ByteVector.empty else HpackWire.encodeInteger(capacity, 5, 0x20)
          firstBlock = resize ++ HpackWire
            .encodeLiteral(requestFields("/cancelled", 1L)) ++ insertion
          secondBlock = HpackWire.encodeLiteral(requestFields("/after", 0L)) ++ ByteVector(
            0xbe.toByte
          )
          _ <- peer.write(
            frames(
              Http2Wire.Frame(Http2Wire.Headers, Http2Wire.EndHeaders, 1, firstBlock),
              Http2Wire.Frame(Http2Wire.RstStream, 0, 1, Http2Wire.uint32(8L)),
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream,
                3,
                secondBlock,
              ),
            )
          )
          responses <- readResponses(peer, Set(3))
          _ <- checkResponse(responses(3), ByteVector.empty)
          received <- server.received
          _ <- Check.equal(received.path, "/after")
          _ <- Check.equal(values(received.headers, "x-state"), Vector("kept"))
          _ <- Check.equal(received.body, ByteVector.empty)
          _ <- pingBarrier(peer)
          extra <- server.pollReceived
          _ <- Check(extra.isEmpty, s"a cancelled incomplete stream reached EchoBody: $extra")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.interaction-interleaved-response-data",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1",
        "Responses are associated by stream when the later request completes before an earlier response body.",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { firstOpen =>
        context.client(Protocol.Http2)(driver =>
          observed {
            val first = driver.exchange(RequestData(path = "/one")).use { response =>
              firstOpen.complete(()).void *> response.body.compile.to(Chunk).flatMap { body =>
                Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Binary) *>
                  Check.equal(values(response.headers, "x-stream"), Vector("one"))
              }
            }
            val second =
              firstOpen.get *> driver.exchange(RequestData(path = "/two")).use { response =>
                response.body.compile.to(Chunk).flatMap { body =>
                  Check.equal(response.status, 201) *> Check.equal(body.toByteVector, Following) *>
                    Check.equal(values(response.headers, "x-stream"), Vector("two"))
                }
              }
            (first, second).parTupled.void
          }
        ) { peer =>
          for {
            _ <- acceptClient(peer, Vector(3 -> 2L))
            decoder <- HpackWire.decoder[IO]()
            first <- readClientRequest(peer, decoder)
            _ <- Check.equal(values(first.fields, ":path"), Vector("/one"))
            _ <- Http2Wire.writeFrame(
              peer,
              Http2Wire.headers(
                first.streamId,
                Vector(
                  ":status" -> "200",
                  "content-length" -> Binary.size.toString,
                  "x-stream" -> "one",
                ),
                endStream = false,
              ),
            )
            second <- readClientRequest(peer, decoder)
            _ <- Check.equal(values(second.fields, ":path"), Vector("/two"))
            _ <- Check(
              second.streamId > first.streamId,
              "a second request must use a new increasing stream identifier",
            )
            _ <- peer.write(
              frames(
                Http2Wire.data(first.streamId, Binary.take(2), endStream = false),
                Http2Wire.headers(
                  second.streamId,
                  Vector(
                    ":status" -> "201",
                    "content-length" -> Following.size.toString,
                    "x-stream" -> "two",
                  ),
                  endStream = false,
                ),
                Http2Wire.data(second.streamId, Following),
                Http2Wire.data(first.streamId, Binary.drop(2)),
              )
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.interaction-interim-next-stream-association",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1",
        "An interim response and its final response remain on their request stream and cannot become another stream's response.",
      ),
    ) { context =>
      context.client(Protocol.Http2)(driver =>
        observed {
          driver.exchange(RequestData(path = "/one")).use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              if (response.status == 103) IO.unit
              else Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Binary)
            }
          } *> checkExchange(driver, RequestData(path = "/two"), 201, Following)
        }
      ) { peer =>
        for {
          _ <- acceptClient(peer, Vector(3 -> 2L))
          decoder <- HpackWire.decoder[IO]()
          first <- readClientRequest(peer, decoder)
          _ <- Check.equal(values(first.fields, ":path"), Vector("/one"))
          _ <- peer.write(
            frames(
              Http2Wire.headers(
                first.streamId,
                Vector(":status" -> "103", "x-interim" -> "one"),
                endStream = false,
              ),
              Http2Wire.headers(
                first.streamId,
                Vector(":status" -> "200", "content-length" -> Binary.size.toString),
                endStream = false,
              ),
              Http2Wire.data(first.streamId, Binary),
            )
          )
          // Resource release after exposing 103 can legitimately reset the already completed stream.
          second <- readClientRequest(peer, decoder, closedStreams = Set(first.streamId))
          _ <- Check.equal(values(second.fields, ":path"), Vector("/two"))
          _ <- Check(
            second.streamId > first.streamId,
            "a new request reused the first stream identifier",
          )
          _ <- peer.write(
            frames(
              Http2Wire.headers(
                second.streamId,
                Vector(":status" -> "201", "content-length" -> Following.size.toString),
                endStream = false,
              ),
              Http2Wire.data(second.streamId, Following),
            )
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.interaction-complete-data-response-no-error-reset",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1",
        "A complete DATA-terminated response is retained when NO_ERROR resets the unfinished request upload.",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { neverRelease =>
        context.client(Protocol.Http2)(driver =>
          observed {
            driver
              .exchangeStream(
                RequestData(
                  method = "POST",
                  headers = Vector("content-length" -> Binary.size.toString),
                ),
                Stream
                  .chunk(Chunk.byteVector(Binary.take(2))) ++ Stream.eval(neverRelease.get).drain ++
                  Stream.chunk(Chunk.byteVector(Binary.drop(2))),
              )
              .use { response =>
                response.body.compile.to(Chunk).flatMap { body =>
                  Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Following)
                }
              }
          }
        ) { peer =>
          for {
            _ <- acceptClient(peer)
            decoder <- HpackWire.decoder[IO]()
            request <- readClientRequest(peer, decoder, incomplete = true)
            _ <- Check(!request.ended, "request upload ended before the withheld suffix")
            prefix <- readUploadPrefix(peer, request.streamId, request.body, 2)
            _ <- Check.equal(prefix, Binary.take(2))
            _ <- peer.write(
              frames(
                Http2Wire.headers(
                  request.streamId,
                  Vector(":status" -> "200", "content-length" -> Following.size.toString),
                  endStream = false,
                ),
                Http2Wire.data(request.streamId, Following),
                Http2Wire.Frame(Http2Wire.RstStream, 0, request.streamId, Http2Wire.uint32(0L)),
              )
            )
          } yield ()
        }
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
    WireCase(
      "h2.client.interaction-complete-headers-response-no-error-reset",
      Protocol.Http2,
      Role.Client,
      Rfc(
        9113,
        "8.1",
        "A response completed in HEADERS is retained when NO_ERROR resets the unfinished request upload.",
      ),
    ) { context =>
      Deferred[IO, Unit].flatMap { neverRelease =>
        context.client(Protocol.Http2)(driver =>
          observed {
            driver
              .exchangeStream(
                RequestData(
                  method = "POST",
                  headers = Vector("content-length" -> Binary.size.toString),
                ),
                Stream
                  .chunk(Chunk.byteVector(Binary.take(2))) ++ Stream.eval(neverRelease.get).drain ++
                  Stream.chunk(Chunk.byteVector(Binary.drop(2))),
              )
              .use { response =>
                response.body.compile.to(Chunk).flatMap { body =>
                  Check.equal(response.status, 200) *> Check
                    .equal(body.toByteVector, ByteVector.empty)
                }
              }
          }
        ) { peer =>
          for {
            _ <- acceptClient(peer)
            decoder <- HpackWire.decoder[IO]()
            request <- readClientRequest(peer, decoder, incomplete = true)
            _ <- Check(!request.ended, "request upload ended before the withheld suffix")
            prefix <- readUploadPrefix(peer, request.streamId, request.body, 2)
            _ <- Check.equal(prefix, Binary.take(2))
            _ <- peer.write(
              frames(
                Http2Wire
                  .headers(request.streamId, Vector(":status" -> "200", "content-length" -> "0")),
                Http2Wire.Frame(Http2Wire.RstStream, 0, request.streamId, Http2Wire.uint32(0L)),
              )
            )
          } yield ()
        }
      }
    },
  )

  private final case class CapturedRequest(
      streamId: Int,
      fields: Vector[(String, String)],
      body: ByteVector,
      ended: Boolean,
  )

  private final case class CapturedResponse(
      status: Option[Int] = None,
      fields: Vector[(String, String)] = Vector.empty,
      body: ByteVector = ByteVector.empty,
      trailers: Vector[(String, String)] = Vector.empty,
      ended: Boolean = false,
  )

  private def frames(input: Http2Wire.Frame*): ByteVector =
    input.foldLeft(ByteVector.empty)((bytes, frame) => bytes ++ Http2Wire.encode(frame))

  private def requestFields(path: String, size: Long): Vector[(String, String)] =
    Vector(
      ":method" -> "POST",
      ":scheme" -> "http",
      ":authority" -> "wire.test",
      ":path" -> path,
      "content-length" -> size.toString,
    )

  private def checkExchange(
      driver: ClientDriver[IO],
      request: RequestData,
      status: Int,
      expected: ByteVector,
  ): IO[Unit] =
    driver.exchange(request).use { response =>
      response.body.compile.to(Chunk).flatMap { body =>
        Check.equal(response.status, status) *> Check.equal(body.toByteVector, expected)
      }
    }

  private def checkTwoRequests(server: ServerHandle[IO]): IO[Unit] =
    for {
      first <- server.received
      second <- server.received
      _ <- Check.equal(
        Vector(first.path -> first.body, second.path -> second.body).sortBy(_._1),
        Vector("/one" -> Binary, "/two" -> Following),
      )
    } yield ()

  private def checkResponse(response: CapturedResponse, expected: ByteVector): IO[Unit] =
    Check.equal(response.status, Some(200)) *> Check(response.ended, "response did not end") *>
      Check.equal(response.body, expected)

  private def settingsValues(frame: Http2Wire.Frame): IO[Vector[(Int, Long)]] =
    for {
      _ <- Check.equal(frame.frameType, Http2Wire.Settings)
      _ <- Check.equal(frame.streamId, 0)
      _ <- Check.equal(frame.payload.size % 6L, 0L)
      _ <- Check(
        !frame.hasFlag(Http2Wire.Ack) || frame.payload.isEmpty,
        "SETTINGS ACK carried a payload",
      )
      entries = (0L until frame.payload.size by 6L).map { offset =>
        val key = ((frame.payload(offset) & 0xff) << 8) | (frame.payload(offset + 1L) & 0xff)
        key -> Http2Wire.readUint32(frame.payload.slice(offset + 2L, offset + 6L))
      }.toVector
      _ <- entries.traverse_ {
        case (2, value) => Check(value <= 1L, "invalid SETTINGS_ENABLE_PUSH")
        case (4, value) => Check(value <= 0x7fffffffL, "invalid initial window")
        case (5, value) =>
          Check(value >= 16384L && value <= 0xffffffL, "invalid maximum frame size")
        case _ => IO.unit
      }
    } yield entries

  private def receiveClientPreface(peer: RawPeer[IO]): IO[Unit] =
    for {
      preface <- observed(peer.readExact(Http2Wire.clientPreface.size.toInt))
      _ <- Check.equal(preface, Http2Wire.clientPreface)
      initial <- observed(Http2Wire.readFrame(peer))
      _ <- Check(
        initial.frameType == Http2Wire.Settings && !initial.hasFlag(Http2Wire.Ack),
        "client preface must be followed by non-ACK SETTINGS",
      )
      _ <- settingsValues(initial)
    } yield ()

  private def acceptClient(
      peer: RawPeer[IO],
      settings: Vector[(Int, Long)] = Vector.empty,
  ): IO[Unit] =
    receiveClientPreface(peer) *> peer.write(
      frames(
        Http2Wire.settings(settings),
        Http2Wire.settings(ack = true),
      )
    )

  /** Retain all settings received before the ACK, so the concurrency prerequisite uses current state. */
  private def connectServer(peer: RawPeer[IO]): IO[Vector[(Int, Long)]] = {
    def acknowledge(values: Vector[(Int, Long)], remaining: Int): IO[Vector[(Int, Long)]] =
      Check(remaining > 0, "too many frames before SETTINGS acknowledgment") *>
        observed(Http2Wire.readFrame(peer)).flatMap { frame =>
          if (frame.frameType == Http2Wire.Settings)
            settingsValues(frame).flatMap { next =>
              if (frame.hasFlag(Http2Wire.Ack)) IO.pure(values)
              else
                Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true)) *>
                  acknowledge(values ++ next, remaining - 1)
            }
          else control(peer, frame) *> acknowledge(values, remaining - 1)
        }
    for {
      _ <- peer.write(Http2Wire.clientPreface ++ Http2Wire.encode(Http2Wire.settings()))
      initial <- observed(Http2Wire.readFrame(peer))
      _ <- Check(
        initial.frameType == Http2Wire.Settings && !initial.hasFlag(Http2Wire.Ack),
        "server preface must be non-ACK SETTINGS",
      )
      values <- settingsValues(initial)
      _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))
      current <- acknowledge(values, 1024)
    } yield current
  }

  private def requireTwoStreams(settings: Vector[(Int, Long)]): IO[Unit] =
    prerequisite(
      settings.reverse.collectFirst { case (3, value) => value }.forall(_ >= 2L),
      "the peer's SETTINGS_MAX_CONCURRENT_STREAMS does not permit two open request streams",
    )

  /** A single connection decoder is passed by the caller across all request field sections. */
  private def readClientRequest(
      peer: RawPeer[IO],
      decoder: HpackWire.Decoder[IO],
      awaitAck: Boolean = false,
      incomplete: Boolean = false,
      closedStreams: Set[Int] = Set.empty,
  ): IO[CapturedRequest] = {
    def loop(
        request: Option[CapturedRequest],
        acknowledged: Boolean,
        remaining: Int,
    ): IO[CapturedRequest] =
      request match {
        case Some(value) if (value.ended || incomplete) && (!awaitAck || acknowledged) =>
          IO.pure(value)
        case _ =>
          Check(remaining > 0, "missing request or SETTINGS acknowledgment") *>
            observed(Http2Wire.readFrame(peer)).flatMap { frame =>
              frame.frameType match {
                case Http2Wire.Headers =>
                  for {
                    _ <- Check(request.isEmpty, "unexpected additional request HEADERS")
                    _ <- Check(
                      frame.streamId > 0 && (frame.streamId & 1) == 1,
                      "client request stream must be positive and odd",
                    )
                    fields <- observed(Http2Wire.readHeaders(peer, frame, decoder))
                    result <- loop(
                      Some(
                        CapturedRequest(
                          frame.streamId,
                          fields,
                          ByteVector.empty,
                          frame.hasFlag(Http2Wire.EndStream),
                        )
                      ),
                      acknowledged,
                      remaining - 1,
                    )
                  } yield result
                case Http2Wire.Data =>
                  request match {
                    case Some(current) =>
                      for {
                        _ <- Check.equal(frame.streamId, current.streamId)
                        _ <- Check(!current.ended, "DATA followed END_STREAM")
                        bytes <- observed(IO(Http2Wire.unpad(frame)))
                        _ <- Check(
                          current.body.size + bytes.size <= 65536L,
                          "request exceeds interaction bound",
                        )
                        result <- loop(
                          Some(
                            current.copy(
                              body = current.body ++ bytes,
                              ended = frame.hasFlag(Http2Wire.EndStream),
                            )
                          ),
                          acknowledged,
                          remaining - 1,
                        )
                      } yield result
                    case None =>
                      IO.raiseError(new ProtocolFailure("DATA arrived before request HEADERS"))
                  }
                case Http2Wire.Settings =>
                  control(peer, frame) *> loop(
                    request,
                    acknowledged || frame.hasFlag(Http2Wire.Ack),
                    remaining - 1,
                  )
                case Http2Wire.RstStream if closedStreams.contains(frame.streamId) =>
                  Check.equal(frame.payload.size, 4L) *> loop(request, acknowledged, remaining - 1)
                case _ => control(peer, frame) *> loop(request, acknowledged, remaining - 1)
              }
            }
      }
    loop(None, false, 1024)
  }

  /** Collect every stream frame, without imposing any order between independent response streams. */
  private def readResponses(peer: RawPeer[IO], streams: Set[Int]): IO[Map[Int, CapturedResponse]] =
    HpackWire.decoder[IO]().flatMap { decoder =>
      def loop(
          current: Map[Int, CapturedResponse],
          remaining: Int,
      ): IO[Map[Int, CapturedResponse]] =
        if (current.values.forall(_.ended)) IO.pure(current)
        else
          Check(remaining > 0, "responses exceeded frame bound") *>
            observed(Http2Wire.readFrame(peer)).flatMap { frame =>
              frame.frameType match {
                case Http2Wire.Headers =>
                  for {
                    _ <- Check(
                      streams.contains(frame.streamId),
                      s"HEADERS on unexpected stream ${frame.streamId}",
                    )
                    previous = current(frame.streamId)
                    _ <- Check(!previous.ended, "HEADERS followed END_STREAM")
                    fields <- observed(Http2Wire.readHeaders(peer, frame, decoder))
                    next <-
                      if (previous.status.isDefined)
                        Check(
                          frame.hasFlag(Http2Wire.EndStream),
                          "response trailers must end the stream",
                        ) *>
                          Check(
                            !fields.exists(_._1.startsWith(":")),
                            "pseudo-header in response trailers",
                          ) *>
                          IO.pure(previous.copy(trailers = fields, ended = true))
                      else {
                        val statuses = values(fields, ":status")
                        Check(
                          statuses.size == 1 && statuses.head.matches("[1-5][0-9][0-9]"),
                          "invalid response status",
                        ) *>
                          IO.defer {
                            val status = statuses.head.toInt
                            if (status < 200)
                              Check(
                                !frame.hasFlag(Http2Wire.EndStream),
                                "interim response ended the stream",
                              ).as(previous)
                            else
                              IO.pure(
                                previous.copy(
                                  status = Some(status),
                                  fields = fields,
                                  ended = frame.hasFlag(Http2Wire.EndStream),
                                )
                              )
                          }
                      }
                    result <- loop(current.updated(frame.streamId, next), remaining - 1)
                  } yield result
                case Http2Wire.Data =>
                  for {
                    _ <- Check(
                      streams.contains(frame.streamId),
                      s"DATA on unexpected stream ${frame.streamId}",
                    )
                    previous = current(frame.streamId)
                    _ <- Check(
                      previous.status.isDefined && !previous.ended,
                      "DATA outside the final response content sequence",
                    )
                    body <- observed(IO(Http2Wire.unpad(frame)))
                    _ <- Check(
                      previous.body.size + body.size <= 65536L,
                      "response exceeds interaction bound",
                    )
                    next = previous.copy(
                      body = previous.body ++ body,
                      ended = frame.hasFlag(Http2Wire.EndStream),
                    )
                    result <- loop(current.updated(frame.streamId, next), remaining - 1)
                  } yield result
                case _ => control(peer, frame) *> loop(current, remaining - 1)
              }
            }
      loop(streams.iterator.map(_ -> CapturedResponse()).toMap, 1024)
    }

  /** No stream frame is skipped on the way to the PING acknowledgment. */
  private def pingBarrier(peer: RawPeer[IO], zeroWindowStream: Option[Int] = None): IO[Unit] = {
    def loop(remaining: Int): IO[Unit] =
      Check(remaining > 0, "missing PING acknowledgment") *>
        observed(Http2Wire.readFrame(peer)).flatMap { frame =>
          if (frame.frameType == Http2Wire.Ping && frame.hasFlag(Http2Wire.Ack))
            Check.equal(frame.streamId, 0) *> Check.equal(frame.payload, PingBytes)
          else if (frame.frameType == Http2Wire.Data && zeroWindowStream.contains(frame.streamId))
            Check(frame.payload.isEmpty, "DATA consumed credit while the stream window was zero") *>
              Check(
                !frame.hasFlag(Http2Wire.Padded),
                "empty DATA cannot contain a pad-length octet",
              ) *>
              Check(
                !frame.hasFlag(Http2Wire.EndStream),
                "upload ended before its nonempty content",
              ) *>
              loop(remaining - 1)
          else control(peer, frame) *> loop(remaining - 1)
        }
    Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, PingBytes)) *> loop(1024)
  }

  private def readUpload(peer: RawPeer[IO], streamId: Int, initial: ByteVector): IO[ByteVector] = {
    def loop(body: ByteVector, remaining: Int): IO[ByteVector] =
      Check(remaining > 0, "upload exceeded frame bound") *> observed(Http2Wire.readFrame(peer))
        .flatMap { frame =>
          if (frame.frameType == Http2Wire.Data)
            for {
              _ <- Check.equal(frame.streamId, streamId)
              bytes <- observed(IO(Http2Wire.unpad(frame)))
              _ <- Check(body.size + bytes.size <= 65536L, "upload exceeds interaction bound")
              result <-
                if (frame.hasFlag(Http2Wire.EndStream)) IO.pure(body ++ bytes)
                else loop(body ++ bytes, remaining - 1)
            } yield result
          else control(peer, frame) *> loop(body, remaining - 1)
        }
    loop(initial, 1024)
  }

  private def readUploadPrefix(
      peer: RawPeer[IO],
      streamId: Int,
      initial: ByteVector,
      size: Int,
  ): IO[ByteVector] = {
    def loop(body: ByteVector, remaining: Int): IO[ByteVector] =
      if (body.size >= size.toLong) Check.equal(body.size, size.toLong).as(body)
      else
        Check(remaining > 0, "upload prefix exceeded frame bound") *>
          observed(Http2Wire.readFrame(peer)).flatMap { frame =>
            if (frame.frameType == Http2Wire.Data)
              for {
                _ <- Check.equal(frame.streamId, streamId)
                _ <- Check(!frame.hasFlag(Http2Wire.EndStream), "partial upload unexpectedly ended")
                bytes <- observed(IO(Http2Wire.unpad(frame)))
                result <- loop(body ++ bytes, remaining - 1)
              } yield result
            else control(peer, frame) *> loop(body, remaining - 1)
          }
    loop(initial, 1024)
  }

  private def control(peer: RawPeer[IO], frame: Http2Wire.Frame): IO[Unit] = frame.frameType match {
    case Http2Wire.Settings =>
      settingsValues(frame) *> (if (frame.hasFlag(Http2Wire.Ack)) IO.unit
                                else Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true)))
    case Http2Wire.Ping =>
      Check.equal(frame.streamId, 0) *> Check.equal(frame.payload.size, 8L) *>
        (if (frame.hasFlag(Http2Wire.Ack)) IO.unit
         else Http2Wire.writeFrame(peer, frame.copy(flags = Http2Wire.Ack)))
    case Http2Wire.WindowUpdate =>
      Check.equal(frame.payload.size, 4L) *>
        Check((Http2Wire.readUint32(frame.payload) & 0x7fffffffL) != 0L, "zero window increment")
    case Http2Wire.Priority =>
      Check(frame.streamId != 0 && frame.payload.size == 5L, "invalid PRIORITY") *>
        Check(
          (Http2Wire.readUint32(frame.payload.take(4)) & 0x7fffffffL) != frame.streamId.toLong,
          "self-dependent PRIORITY",
        )
    case kind if kind > Http2Wire.Continuation => IO.unit
    case _ => Check(false, s"unexpected frame ${frame.frameType} on stream ${frame.streamId}")
  }

  private def values(fields: Vector[(String, String)], name: String): Vector[String] =
    fields.collect { case (key, value) if key.equalsIgnoreCase(name) => value }

  private def chunked(body: ByteVector, trailers: String = ""): ByteVector =
    Http1Wire.ascii(body.size.toHexString + ";wire=\"boundary\"\r\n") ++ body ++
      Http1Wire.ascii("\r\n0\r\n" + trailers + "\r\n")

  private val followingRequest = Http1Wire.message(
    "POST /second HTTP/1.1",
    Vector(
      "Host" -> "wire.test",
      "Content-Length" -> Following.size.toString,
      "Connection" -> "close",
    ),
    Following,
  )

  private val finalH1Response = Http1Wire.message(
    "HTTP/1.1 201 Created",
    Vector("Content-Length" -> Following.size.toString, "Connection" -> "close"),
    Following,
  )

  private def checkPipeline(
      peer: RawPeer[IO],
      server: ServerHandle[IO],
      firstBody: ByteVector,
      secondBody: ByteVector,
  ): IO[Unit] =
    for {
      first <- observed(Http1Wire.readFinalResponse(peer))
      _ <- Check.equal(first.status, 200)
      _ <- Check.equal(first.body, firstBody)
      _ <- requirePersistence(first.headers)
      second <- observed(Http1Wire.readFinalResponse(peer))
      _ <- Check.equal(second.status, 200)
      _ <- Check.equal(second.body, secondBody)
      firstRequest <- server.received
      secondRequest <- server.received
      _ <- Check.equal(firstRequest.path, "/first")
      _ <- Check.equal(firstRequest.body, firstBody)
      _ <- Check.equal(secondRequest.path, "/second")
      _ <- Check.equal(secondRequest.body, secondBody)
    } yield ()

  private def serveH1Pair(peer: RawPeer[IO], method: String, firstResponse: ByteVector): IO[Unit] =
    for {
      first <- Http1Wire.readRequest(peer)
      _ <- Check.equal(first.method, method)
      _ <- Check.equal(first.target, "/first")
      _ <- requirePersistence(first.headers)
      _ <- peer.write(firstResponse)
      second <- Http1Wire.readRequest(peer)
      _ <- Check.equal(second.target, "/second")
      _ <- peer.write(finalH1Response)
    } yield ()

  private def requirePersistence(fields: Vector[(String, String)]): IO[Unit] =
    prerequisite(
      !values(fields, "connection").exists(
        _.split(",", -1).exists(_.trim.equalsIgnoreCase("close"))
      ),
      "successive-message observation requires a persistent HTTP/1 connection",
    )

  private def prerequisite(condition: Boolean, detail: String): IO[Unit] =
    if (condition) IO.unit
    else IO.raiseError(new IllegalStateException("coverage prerequisite unavailable: " + detail))

  /** An error response has already been asserted, so closure alone never proves rejection. */
  private def expectClosed(peer: RawPeer[IO]): IO[Unit] =
    peer.readToEnd(0).attempt.flatMap {
      case Right(bytes) => Check.equal(bytes, ByteVector.empty)
      case Left(error) if RuntimeInfo.isConnectionReset(error) => IO.unit
      case Left(error: IllegalArgumentException) if error.getMessage == "body exceeds 0 bytes" =>
        Check(false, "additional response bytes followed a response requiring connection closure")
      case Left(error) => IO.raiseError(error)
    }

  /** Apply only to target operations or parsing target output, never to the scripted peer. */
  private def observed[A](operation: IO[A]): IO[A] =
    operation.handleErrorWith {
      case error: TimeoutException => IO.raiseError(error)
      case error if causedByTimeout(error) =>
        IO.raiseError(new TimeoutException(s"wire interaction timed out: $error"))
      case error: ProtocolFailure => IO.raiseError(error)
      case error => IO.raiseError(new ProtocolFailure(s"valid wire interaction failed: $error"))
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
