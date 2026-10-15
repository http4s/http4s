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

/** Message boundaries and connection/stream sequencing across the individual protocol rules. */
object InteractionCases {
  private val statusCodePattern = "[1-5][0-9][0-9]".r

  private val Binary = ByteVector(0, 13, 10, 127, 128, 255)
  private val Following = Http1Wire.ascii("following")
  private val RequestShaped =
    Http1Wire.ascii("GET /inside-body HTTP/1.1\r\nHost: wire.test\r\n\r\n") ++ Binary
  private val ResponseShaped =
    Http1Wire.ascii("HTTP/1.1 299 Body Only\r\nContent-Length: 0\r\n\r\n") ++ Binary
  private val PingBytes = ByteVector(41, 43, 47, 53, 59, 61, 67, 71)

  lazy val all: Vector[WireCase] = Vector(
    h1ServerInteractionLengthBodyRequestLine,
    h1ServerInteractionChunkBodyRequestLine,
    h1ServerInteractionUnframedPostNextRequest,
    h1ServerInteractionConflictingLengthStopsPipeline,
    h1ServerInteractionNonfinalChunkedStopsPipeline,
    h1ServerInteractionFramingNameWhitespaceStopsPipeline,
    h1ServerInteractionTransferLengthStopsPipeline,
    h1ClientInteractionHeadLengthNextResponse,
    h1ClientInteractionHeadChunkedNextResponse,
    h1ClientInteractionNotModifiedNextResponse,
    h1ClientInteractionNoContentNextResponse,
    h1ClientInteractionLengthBodyStatusLine,
    h1ClientInteractionChunkTrailerNextResponse,
    h1ClientInteractionInterimNextRequestAssociation,
    h2ClientInteractionEagerServerSettings,
    h2ClientInteractionEagerZeroWindowUpload,
    h2ServerInteractionCoalescedPrefaceRequest,
    h2ServerInteractionInterleavedRequestData,
    h2ServerInteractionTrailersBetweenOtherStreamData,
    h2ServerInteractionResetOtherUploadStream,
    h2ServerInteractionResetRetainsCompressionContext,
    h2ClientInteractionInterleavedResponseData,
    h2ClientInteractionInterimNextStreamAssociation,
    h2ClientInteractionCompleteDataResponseNoErrorReset,
    h2ClientInteractionCompleteHeadersResponseNoErrorReset,
  )

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
  lazy val h1ServerInteractionLengthBodyRequestLine: WireCase = WireCase(
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
  }

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
  lazy val h1ServerInteractionChunkBodyRequestLine: WireCase = WireCase(
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
        _ <- checkPipeline(peer, server, RequestShaped, Following, allowLengthRequired = true)
      } yield ()
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
  lazy val h1ServerInteractionUnframedPostNextRequest: WireCase = WireCase(
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
  }

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
  lazy val h1ServerInteractionConflictingLengthStopsPipeline: WireCase = WireCase(
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
        response <- wireObservation(Http1Wire.readFinalResponse(peer))
        _ <- Check.equal(response.status, 400)
        _ <- expectClosed(peer)
        received <- server.pollReceived
        _ <- Check(
          received.isEmpty,
          s"invalid framing or its suffix reached the application: $received",
        )
      } yield ()
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
  lazy val h1ServerInteractionNonfinalChunkedStopsPipeline: WireCase = WireCase(
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
        response <- wireObservation(Http1Wire.readFinalResponse(peer))
        _ <- Check.equal(response.status, 400)
        _ <- expectClosed(peer)
        received <- server.pollReceived
        _ <- Check(
          received.isEmpty,
          s"undelimited request or its suffix reached the application: $received",
        )
      } yield ()
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
  lazy val h1ServerInteractionFramingNameWhitespaceStopsPipeline: WireCase = WireCase(
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
        response <- wireObservation(Http1Wire.readFinalResponse(peer))
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
  }

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.1
  lazy val h1ServerInteractionTransferLengthStopsPipeline: WireCase = WireCase(
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
        response <- wireObservation(Http1Wire.readFinalResponse(peer))
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
  }

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
  lazy val h1ClientInteractionHeadLengthNextResponse: WireCase = WireCase(
    "h1.client.interaction-head-length-next-response",
    Protocol.Http1,
    Role.Client,
    Rfc(
      9112,
      "6.3",
      "Content-Length on HEAD is metadata and must not consume bytes belonging to the subsequent response.",
    ),
  ) { context =>
    h1ClientPair(context)((driver, nextSeen) =>
      targetOperation(
        checkExchange(
          driver,
          RequestData(method = "HEAD", path = "/first"),
          200,
          ByteVector.empty,
        ) *>
          afterH1Continuation(nextSeen)(
            checkExchange(driver, RequestData(path = "/second"), 201, Following)
          )
      )
    ) { (peer, nextSeen) =>
      serveH1Pair(
        peer,
        "HEAD",
        Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 123\r\n\r\n"),
      )(nextSeen)
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
  lazy val h1ClientInteractionHeadChunkedNextResponse: WireCase = WireCase(
    "h1.client.interaction-head-chunked-next-response",
    Protocol.Http1,
    Role.Client,
    Rfc(
      9112,
      "6.3",
      "Transfer-Encoding on HEAD does not require chunks before the next response on a reused connection.",
    ),
  ) { context =>
    h1ClientPair(context)((driver, nextSeen) =>
      targetOperation(
        checkExchange(
          driver,
          RequestData(method = "HEAD", path = "/first"),
          200,
          ByteVector.empty,
        ) *>
          afterH1Continuation(nextSeen)(
            checkExchange(driver, RequestData(path = "/second"), 201, Following)
          )
      )
    ) { (peer, nextSeen) =>
      serveH1Pair(
        peer,
        "HEAD",
        Http1Wire.ascii("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"),
      )(nextSeen)
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
  lazy val h1ClientInteractionNotModifiedNextResponse: WireCase = WireCase(
    "h1.client.interaction-not-modified-next-response",
    Protocol.Http1,
    Role.Client,
    Rfc(
      9112,
      "6.3",
      "A 304 response ends at its empty line; its Content-Length cannot absorb the following response.",
    ),
  ) { context =>
    h1ClientPair(context)((driver, nextSeen) =>
      targetOperation(
        checkExchange(driver, RequestData(path = "/first"), 304, ByteVector.empty) *>
          afterH1Continuation(nextSeen)(
            checkExchange(driver, RequestData(path = "/second"), 201, Following)
          )
      )
    ) { (peer, nextSeen) =>
      serveH1Pair(
        peer,
        "GET",
        Http1Wire.ascii("HTTP/1.1 304 Not Modified\r\nContent-Length: 123\r\n\r\n"),
      )(nextSeen)
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
  lazy val h1ClientInteractionNoContentNextResponse: WireCase = WireCase(
    "h1.client.interaction-no-content-next-response",
    Protocol.Http1,
    Role.Client,
    Rfc(
      9112,
      "6.3",
      "A 204 response without framing fields ends at its empty line and permits a subsequent response.",
    ),
  ) { context =>
    h1ClientPair(context)((driver, nextSeen) =>
      targetOperation(
        checkExchange(driver, RequestData(path = "/first"), 204, ByteVector.empty) *>
          afterH1Continuation(nextSeen)(
            checkExchange(driver, RequestData(path = "/second"), 201, Following)
          )
      )
    ) { (peer, nextSeen) =>
      serveH1Pair(peer, "GET", Http1Wire.ascii("HTTP/1.1 204 No Content\r\n\r\n"))(nextSeen)
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3
  lazy val h1ClientInteractionLengthBodyStatusLine: WireCase = WireCase(
    "h1.client.interaction-length-body-status-line",
    Protocol.Http1,
    Role.Client,
    Rfc(
      9112,
      "6.3",
      "A status line inside fixed-length content is body data and cannot become a subsequent response.",
    ),
  ) { context =>
    h1ClientPair(context)((driver, nextSeen) =>
      targetOperation(
        checkExchange(driver, RequestData(path = "/first"), 200, ResponseShaped) *>
          afterH1Continuation(nextSeen)(
            checkExchange(driver, RequestData(path = "/second"), 201, Following)
          )
      )
    ) { (peer, nextSeen) =>
      serveH1Pair(
        peer,
        "GET",
        Http1Wire.message(
          "HTTP/1.1 200 OK",
          Vector("Content-Length" -> ResponseShaped.size.toString),
          ResponseShaped,
        ),
      )(nextSeen)
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1
  lazy val h1ClientInteractionChunkTrailerNextResponse: WireCase = WireCase(
    "h1.client.interaction-chunk-trailer-next-response",
    Protocol.Http1,
    Role.Client,
    Rfc(
      9112,
      "7.1",
      "Response-shaped chunk data ends before reuse; unadvertised trailers may be discarded or retained in order.",
    ),
  ) { context =>
    h1ClientPair(context)((driver, nextSeen) =>
      targetOperation(
        driver.exchange(RequestData(path = "/first")).use { response =>
          for {
            body <- response.body.compile.to(Chunk)
            trailers <- response.trailers
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(body.toByteVector, ResponseShaped)
            retained = values(trailers, "x-fence").flatMap(_.split(",", -1).map(_.trim))
            _ <- Check(
              retained.isEmpty || retained == Vector("first", "second"),
              s"retained trailers changed order or values: $retained",
            )
          } yield ()
        } *> afterH1Continuation(nextSeen)(
          checkExchange(driver, RequestData(path = "/second"), 201, Following)
        )
      )
    ) { (peer, nextSeen) =>
      serveH1Pair(
        peer,
        "GET",
        Http1Wire.message(
          "HTTP/1.1 200 OK",
          Vector("Transfer-Encoding" -> "chunked", "Trailer" -> "X-Fence"),
          chunked(ResponseShaped, "X-Fence: first\r\nX-Fence: second\r\n"),
        ),
      )(nextSeen)
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-9.2
  lazy val h1ClientInteractionInterimNextRequestAssociation: WireCase = WireCase(
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
        targetOperation {
          for {
            _ <- driver.exchange(RequestData(path = "/first")).use { response =>
              response.body.compile.to(Chunk).flatMap { body =>
                // The API may expose a 1xx. It need not expose the final response here.
                if (response.status == 100 || response.status == 103)
                  IO.unit
                else Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Binary)
              }
            }
            _ <- afterH1Continuation(secondSeen.get) {
              driver.exchange(RequestData(path = "/second")).use { response =>
                for {
                  body <- response.body.compile.to(Chunk)
                  _ <- Check.equal(response.status, 201)
                  _ <- Check.equal(body.toByteVector, Following)
                } yield ()
              }
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
          second <- nextH1Message(peer)(Http1Wire.readRequest(peer))
          _ <- Check.equal(second.target, "/second")
          _ <- secondSeen.complete(()).void
          _ <- peer.write(finalH1Response)
        } yield ()
      }
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4
  lazy val h2ClientInteractionEagerServerSettings: WireCase = WireCase(
    "h2.client.interaction-eager-server-settings",
    Protocol.Http2,
    Role.Client,
    Rfc(
      9113,
      "3.4",
      "An eager server SETTINGS preface can arrive while the client sends its preface and must be acknowledged after that preface.",
    ),
  ) { context =>
    Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
      context.client(Protocol.Http2)(driver =>
        targetOperation(checkExchange(driver, RequestData(path = "/eager"), 200, Binary))
      ) { peer =>
        for {
          // Deliberately write before reading ANY client octet. serverHandshake would hide this ordering.
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings())
          settings <- receiveClientPreface(peer)
          _ <- requireSendWindow(settings, Binary.size)
          _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))
          decoder <- HpackWire.decoder[IO]()
          request <- readClientRequest(peer, decoder, Binary.size, awaitAck = true)
          _ <- Check.equal(values(request.fields, ":path"), Vector("/eager"))
          _ <- Check.equal(request.body, ByteVector.empty)
          _ <- Http2PeerSettings.write(
            peer,
            Http2Wire.encode(
              literalHeaders(
                request.streamId,
                Vector(":status" -> "200", "content-length" -> Binary.size.toString),
                endStream = false,
              )
            ) ++ Http2Wire.encode(Http2Wire.data(request.streamId, Binary)),
          )
        } yield ()
      }
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4
  lazy val h2ClientInteractionEagerZeroWindowUpload: WireCase = WireCase(
    "h2.client.interaction-eager-zero-window-upload",
    Protocol.Http2,
    Role.Client,
    Rfc(
      9113,
      "3.4",
      "An eager initial SETTINGS window applies to DATA released after its acknowledgment, and WINDOW_UPDATE permits progress.",
    ),
  ) { context =>
    Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
      Deferred[IO, Unit].flatMap { release =>
        context.client(Protocol.Http2)(driver =>
          targetOperation {
            driver
              .exchangeStream(
                RequestData(
                  method = "POST",
                  path = "/eager",
                  headers = Vector("content-length" -> Binary.size.toString),
                ),
                _ => Stream.eval(release.get).drain ++ Stream.chunk(Chunk.byteVector(Binary)),
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
            settings <- receiveClientPreface(peer)
            _ <- requireSendWindow(settings, 0L)
            _ <- Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))
            decoder <- HpackWire.decoder[IO]()
            request <- readClientRequest(peer, decoder, 0L, awaitAck = true, incomplete = true)
            _ <- Check.equal(values(request.fields, ":method"), Vector("POST"))
            _ <- Check(
              !request.ended && request.body.isEmpty,
              "upload must remain open before its body is released",
            )
            _ <- release.complete(()).void
            // Every intervening frame is inspected; a PING ACK establishes connection liveness.
            _ <- pingBarrier(peer, fromServer = false, zeroWindowStream = Some(request.streamId))
            _ <- Http2Wire.writeFrame(peer, Http2Wire.windowUpdate(request.streamId, 65535))
            body <- readUpload(peer, decoder, request.streamId, request.body)
            _ <- Check.equal(body, Binary)
            _ <- Http2Wire.writeFrame(
              peer,
              literalHeaders(request.streamId, Vector(":status" -> "200")),
            )
          } yield ()
        }
      }
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4
  lazy val h2ServerInteractionCoalescedPrefaceRequest: WireCase = WireCase(
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
    Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          _ <- Http2PeerSettings.write(
            peer,
            Http2Wire.clientPreface ++ Http2Wire.encode(Http2Wire.settings(Vector(2 -> 0L))) ++
              Http2Wire.encode(
                literalHeaders(1, requestFields("/early", Binary.size), endStream = false)
              ) ++
              Http2Wire.encode(Http2Wire.data(1, Binary)),
          )
          initial <- wireObservation(Http2Wire.readFrame(peer))
          _ <- Check(
            initial.frameType == Http2Wire.Settings && !initial.hasFlag(Http2Wire.Ack),
            "server preface must be non-ACK SETTINGS",
          )
          _ <- control(peer, initial, fromServer = true)
          responses <- readResponses(peer, Set(1))
          _ <- checkResponse(responses(1), Binary)
          received <- server.received
          _ <- Check.equal(received.path, "/early")
          _ <- Check.equal(received.body, Binary)
        } yield ()
      }
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9113.html#section-5
  lazy val h2ServerInteractionInterleavedRequestData: WireCase = WireCase(
    "h2.server.interaction-interleaved-request-data",
    Protocol.Http2,
    Role.Server,
    Rfc(
      9113,
      "5",
      "Interleaved DATA belongs to its stream, including when the later stream reaches END_STREAM first.",
    ),
  ) { context =>
    Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          settings <- connectServer(peer)
          _ <- requireTwoStreams(settings)
          _ <- requireSendWindow(settings, Following.size)
          _ <- Http2PeerSettings.write(
            peer,
            frames(
              literalHeaders(1, requestFields("/one", Binary.size), endStream = false),
              literalHeaders(3, requestFields("/two", Following.size), endStream = false),
              Http2Wire.data(1, Binary.take(2), endStream = false),
              Http2Wire.data(3, Following.take(3), endStream = false),
              Http2Wire.data(3, Following.drop(3)),
              Http2Wire.data(1, Binary.drop(2)),
            ),
          )
          // The server can choose either response order, and may interleave its response frames.
          responses <- readResponses(peer, Set(1, 3))
          _ <- checkResponse(responses(1), Binary)
          _ <- checkResponse(responses(3), Following)
          _ <- checkTwoRequests(server)
        } yield ()
      }
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
  lazy val h2ServerInteractionTrailersBetweenOtherStreamData: WireCase = WireCase(
    "h2.server.interaction-trailers-between-other-stream-data",
    Protocol.Http2,
    Role.Server,
    Rfc(
      9113,
      "8.1",
      "Trailers complete their own stream without ending or contaminating another stream's DATA sequence.",
    ),
  ) { context =>
    Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          settings <- connectServer(peer)
          _ <- requireTwoStreams(settings)
          _ <- requireSendWindow(settings, Following.size)
          _ <- Http2PeerSettings.write(
            peer,
            frames(
              literalHeaders(1, requestFields("/one", Binary.size), endStream = false),
              literalHeaders(3, requestFields("/two", Following.size), endStream = false),
              Http2Wire.data(1, Binary, endStream = false),
              Http2Wire.data(3, Following.take(3), endStream = false),
              literalHeaders(1, Vector("x-fence" -> "one", "x-fence" -> "two")),
              Http2Wire.data(3, Following.drop(3)),
            ),
          )
          responses <- readResponses(peer, Set(1, 3))
          _ <- checkResponse(responses(1), Binary)
          _ <- checkResponse(responses(3), Following)
          _ <- checkTwoRequests(server)
        } yield ()
      }
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4
  lazy val h2ServerInteractionResetOtherUploadStream: WireCase = WireCase(
    "h2.server.interaction-reset-other-upload-stream",
    Protocol.Http2,
    Role.Server,
    Rfc(
      9113,
      "6.4",
      "Resetting one incomplete upload does not prevent the other stream from completing or publish the incomplete upload as complete.",
    ),
  ) { context =>
    Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
      context.server(Protocol.Http2, ServerScenario.EchoBody).use { case (peer, server) =>
        for {
          settings <- connectServer(peer)
          _ <- requireTwoStreams(settings)
          _ <- requireSendWindow(settings, Following.size)
          _ <- Http2PeerSettings.write(
            peer,
            frames(
              literalHeaders(1, requestFields("/cancelled", Binary.size), endStream = false),
              literalHeaders(3, requestFields("/two", Following.size), endStream = false),
              Http2Wire.data(1, Binary.take(2), endStream = false),
              Http2Wire.data(3, Following.take(3), endStream = false),
              Http2Wire.Frame(Http2Wire.RstStream, 0, 1, Http2Wire.uint32(8L)),
              Http2Wire.data(3, Following.drop(3)),
            ),
          )
          // RFC 9113 Section 5.1: HEADERS on an already open stream do not confirm
          // reset processing. Echoing the suffix sent after CANCEL does.
          responses <- readResponses(peer, Set(3), Some(PendingReset(1, _.body == Following)))
          _ <- checkResponse(responses(3), Following)
          _ <- pingBarrier(peer, fromServer = true)
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
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3
  lazy val h2ServerInteractionResetRetainsCompressionContext: WireCase = WireCase(
    "h2.server.interaction-reset-retains-compression-context",
    Protocol.Http2,
    Role.Server,
    Rfc(
      9113,
      "4.3",
      "The connection compression context retains a valid dynamic entry after its request stream is reset.",
    ),
  ) { context =>
    Http2PeerSettings.scoped[IO, Unit](initiatesRequests = true) { implicit peerSettings =>
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
          minimum = settings.collect { case (1, size) => size }.foldLeft(4096L)(_.min(_)).toInt
          resize =
            (if (minimum < capacity) HpackWire.encodeInteger(minimum, 5, 0x20)
             else ByteVector.empty) ++
              (if (capacity < 4096 || minimum < capacity)
                 HpackWire.encodeInteger(capacity, 5, 0x20)
               else ByteVector.empty)
          firstBlock = resize ++ HpackWire
            .encodeLiteral(requestFields("/cancelled", 1L)) ++ insertion
          secondBlock = HpackWire.encodeLiteral(requestFields("/after", 0L)) ++ ByteVector(
            0xbe.toByte
          )
          _ <- Http2PeerSettings.write(
            peer,
            frames(
              Http2Wire.Frame(Http2Wire.Headers, Http2Wire.EndHeaders, 1, firstBlock),
              Http2Wire.Frame(Http2Wire.RstStream, 0, 1, Http2Wire.uint32(8L)),
              Http2Wire.Frame(
                Http2Wire.Headers,
                Http2Wire.EndHeaders | Http2Wire.EndStream,
                3,
                secondBlock,
              ),
            ),
          )
          // RFC 9113 Section 5.1: this request was created after CANCEL, so its
          // first response HEADERS confirm that the peer processed the reset.
          responses <- readResponses(peer, Set(3), Some(PendingReset(1, _ => true)))
          _ <- checkResponse(responses(3), ByteVector.empty)
          received <- server.received
          _ <- Check.equal(received.path, "/after")
          _ <- Check.equal(values(received.headers, "x-state"), Vector("kept"))
          _ <- Check.equal(received.body, ByteVector.empty)
          _ <- pingBarrier(peer, fromServer = true)
          extra <- server.pollReceived
          _ <- Check(extra.isEmpty, s"a cancelled incomplete stream reached EchoBody: $extra")
        } yield ()
      }
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
  lazy val h2ClientInteractionInterleavedResponseData: WireCase = WireCase(
    "h2.client.interaction-interleaved-response-data",
    Protocol.Http2,
    Role.Client,
    Rfc(
      9113,
      "8.1",
      "Responses are associated by stream when the later request completes before an earlier response body.",
    ),
  ) { context =>
    Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
      Deferred[IO, Unit].flatMap { firstOpen =>
        context.client(Protocol.Http2)(driver =>
          targetOperation {
            val first = driver.exchange(RequestData(path = "/one")).use { response =>
              firstOpen.complete(()).void *> response.body.compile.to(Chunk).flatMap { body =>
                Check.equal(response.status, 200) *> Check.equal(body.toByteVector, Binary) *>
                  Check.equal(values(response.headers, "x-stream"), Vector("one"))
              }
            }
            val second =
              firstOpen.get *> driver.exchange(RequestData(path = "/two")).use { response =>
                response.body.compile.to(Chunk).flatMap { body =>
                  Check.equal(response.status, 201) *> Check
                    .equal(body.toByteVector, Following) *>
                    Check.equal(values(response.headers, "x-stream"), Vector("two"))
                }
              }
            (first, second).parTupled.void
          }
        ) { peer =>
          for {
            _ <- acceptClient(peer, Vector(3 -> 2L))
            decoder <- HpackWire.decoder[IO]()
            first <- readClientRequest(peer, decoder, Following.size)
            _ <- Check.equal(values(first.fields, ":path"), Vector("/one"))
            _ <- Http2Wire.writeFrame(
              peer,
              literalHeaders(
                first.streamId,
                Vector(
                  ":status" -> "200",
                  "content-length" -> Binary.size.toString,
                  "x-stream" -> "one",
                ),
                endStream = false,
              ),
            )
            second <- readClientRequest(peer, decoder, Following.size)
            _ <- Check.equal(values(second.fields, ":path"), Vector("/two"))
            _ <- Check(
              second.streamId > first.streamId,
              "a second request must use a new increasing stream identifier",
            )
            _ <- Http2PeerSettings.write(
              peer,
              frames(
                Http2Wire.data(first.streamId, Binary.take(2), endStream = false),
                literalHeaders(
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
              ),
            )
          } yield ()
        }
      }
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
  lazy val h2ClientInteractionInterimNextStreamAssociation: WireCase = WireCase(
    "h2.client.interaction-interim-next-stream-association",
    Protocol.Http2,
    Role.Client,
    Rfc(
      9113,
      "8.1",
      "An interim response and its final response remain on their request stream and cannot become another stream's response.",
    ),
  ) { context =>
    Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
      context.client(Protocol.Http2)(driver =>
        targetOperation {
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
          first <- readClientRequest(peer, decoder, Following.size)
          _ <- Check.equal(values(first.fields, ":path"), Vector("/one"))
          _ <- Http2PeerSettings.write(
            peer,
            frames(
              literalHeaders(
                first.streamId,
                Vector(":status" -> "103", "x-interim" -> "one"),
                endStream = false,
              ),
              literalHeaders(
                first.streamId,
                Vector(":status" -> "200", "content-length" -> Binary.size.toString),
                endStream = false,
              ),
              Http2Wire.data(first.streamId, Binary),
            ),
          )
          // Resource release after exposing 103 can legitimately reset the already completed stream.
          second <- readClientRequest(
            peer,
            decoder,
            Following.size,
            closedStreams = Set(first.streamId),
          )
          _ <- Check.equal(values(second.fields, ":path"), Vector("/two"))
          _ <- Check(
            second.streamId > first.streamId,
            "a new request reused the first stream identifier",
          )
          _ <- Http2PeerSettings.write(
            peer,
            frames(
              literalHeaders(
                second.streamId,
                Vector(":status" -> "201", "content-length" -> Following.size.toString),
                endStream = false,
              ),
              Http2Wire.data(second.streamId, Following),
            ),
          )
        } yield ()
      }
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
  lazy val h2ClientInteractionCompleteDataResponseNoErrorReset: WireCase = WireCase(
    "h2.client.interaction-complete-data-response-no-error-reset",
    Protocol.Http2,
    Role.Client,
    Rfc(
      9113,
      "8.1",
      "A complete DATA-terminated response is retained when NO_ERROR resets the unfinished request upload.",
    ),
  ) { context =>
    Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
      Deferred[IO, Unit].flatMap { neverRelease =>
        context.client(Protocol.Http2)(driver =>
          targetOperation {
            driver
              .exchangeStream(
                RequestData(
                  method = "POST",
                  headers = Vector("content-length" -> Binary.size.toString),
                ),
                _ =>
                  Stream
                    .chunk(Chunk.byteVector(Binary.take(2))) ++ Stream
                    .eval(neverRelease.get)
                    .drain ++
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
            request <- readClientRequest(peer, decoder, Following.size, incomplete = true)
            _ <- Check(!request.ended, "request upload ended before the withheld suffix")
            prefix <- readUploadPrefix(peer, request.streamId, request.body, 2, Following.size)
            _ <- Check.equal(prefix, Binary.take(2))
            _ <- Http2PeerSettings.write(
              peer,
              frames(
                literalHeaders(
                  request.streamId,
                  Vector(":status" -> "200", "content-length" -> Following.size.toString),
                  endStream = false,
                ),
                Http2Wire.data(request.streamId, Following),
                Http2Wire.Frame(Http2Wire.RstStream, 0, request.streamId, Http2Wire.uint32(0L)),
              ),
            )
          } yield ()
        }
      }
    }
  }

  // https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1
  lazy val h2ClientInteractionCompleteHeadersResponseNoErrorReset: WireCase = WireCase(
    "h2.client.interaction-complete-headers-response-no-error-reset",
    Protocol.Http2,
    Role.Client,
    Rfc(
      9113,
      "8.1",
      "A response completed in HEADERS is retained when NO_ERROR resets the unfinished request upload.",
    ),
  ) { context =>
    Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) { implicit peerSettings =>
      Deferred[IO, Unit].flatMap { neverRelease =>
        context.client(Protocol.Http2)(driver =>
          targetOperation {
            driver
              .exchangeStream(
                RequestData(
                  method = "POST",
                  headers = Vector("content-length" -> Binary.size.toString),
                ),
                _ =>
                  Stream
                    .chunk(Chunk.byteVector(Binary.take(2))) ++ Stream
                    .eval(neverRelease.get)
                    .drain ++
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
            _ <- acceptClient(peer, responseBytes = 0L)
            decoder <- HpackWire.decoder[IO]()
            request <- readClientRequest(peer, decoder, 0L, incomplete = true)
            _ <- Check(!request.ended, "request upload ended before the withheld suffix")
            prefix <- readUploadPrefix(peer, request.streamId, request.body, 2, 0L)
            _ <- Check.equal(prefix, Binary.take(2))
            _ <- Http2PeerSettings.write(
              peer,
              frames(
                literalHeaders(
                  request.streamId,
                  Vector(":status" -> "200", "content-length" -> "0"),
                ),
                Http2Wire.Frame(Http2Wire.RstStream, 0, request.streamId, Http2Wire.uint32(0L)),
              ),
            )
          } yield ()
        }
      }
    }
  }

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

  /** Literal-only fixtures choose zero dynamic capacity (RFC 7541 Section 4.2).
    * Every block can therefore honor even a newly acknowledged zero table maximum.
    * The dedicated dynamic-context case constructs its own field blocks.
    */
  private def literalHeaders(
      streamId: Int,
      fields: Vector[(String, String)],
      endStream: Boolean = true,
  ): Http2Wire.Frame =
    Http2Wire.headers(streamId, fields, endStream)

  private def requireSendWindow(settings: Vector[(Int, Long)], bytes: Long): IO[Unit] =
    observationBound(
      settings.reverse.collectFirst { case (4, size) => size }.forall(_ >= bytes),
      s"coverage prerequisite unavailable: peer stream window cannot carry $bytes fixture DATA octets without additional credit",
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

  private def settingsValues(
      frame: Http2Wire.Frame,
      fromServer: Boolean,
  ): IO[Vector[(Int, Long)]] =
    for {
      _ <- Check.equal(frame.frameType, Http2Wire.Settings)
      _ <- IO.fromEither(Http2RejectionChecks.validate(frame, fromServer))
    } yield (0L until frame.payload.size by 6L).map { offset =>
      val key = ((frame.payload(offset) & 0xff) << 8) | (frame.payload(offset + 1L) & 0xff)
      key -> Http2Wire.readUint32(frame.payload.slice(offset + 2L, offset + 6L))
    }.toVector

  private def receiveClientPreface(
      peer: RawPeer[IO]
  )(implicit peerSettings: Http2PeerSettings[IO]): IO[Vector[(Int, Long)]] =
    for {
      preface <- wireObservation(peer.readExact(Http2Wire.clientPreface.size.toInt))
      _ <- Check.equal(preface, Http2Wire.clientPreface)
      initial <- wireObservation(Http2Wire.readFrame(peer))
      _ <- Check(
        initial.frameType == Http2Wire.Settings && !initial.hasFlag(Http2Wire.Ack),
        "client preface must be followed by non-ACK SETTINGS",
      )
      settings <- settingsValues(initial, fromServer = false)
    } yield settings

  private def acceptClient(
      peer: RawPeer[IO],
      settings: Vector[(Int, Long)] = Vector.empty,
      responseBytes: Long = Following.size,
  )(implicit peerSettings: Http2PeerSettings[IO]): IO[Unit] =
    receiveClientPreface(peer)
      .flatMap(requireSendWindow(_, responseBytes)) *> Http2PeerSettings.write(
      peer,
      frames(
        Http2Wire.settings(settings),
        Http2Wire.settings(ack = true),
      ),
    )

  /** Retain all settings received before the ACK, so the concurrency prerequisite uses current state. */
  private def connectServer(
      peer: RawPeer[IO]
  )(implicit peerSettings: Http2PeerSettings[IO]): IO[Vector[(Int, Long)]] = {
    def acknowledge(values: Vector[(Int, Long)], remaining: Int): IO[Vector[(Int, Long)]] =
      observationBound(remaining > 0, "too many frames before SETTINGS acknowledgment") *>
        wireObservation(Http2Wire.readFrame(peer)).flatMap { frame =>
          if (frame.frameType == Http2Wire.Settings)
            settingsValues(frame, fromServer = true).flatMap { next =>
              if (frame.hasFlag(Http2Wire.Ack)) IO.pure(values)
              else
                Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true)) *>
                  acknowledge(values ++ next, remaining - 1)
            }
          else control(peer, frame, fromServer = true) *> acknowledge(values, remaining - 1)
        }
    for {
      _ <- Http2PeerSettings.write(
        peer,
        Http2Wire.clientPreface ++ Http2Wire.encode(Http2Wire.settings(Vector(2 -> 0L))),
      )
      initial <- wireObservation(Http2Wire.readFrame(peer))
      _ <- Check(
        initial.frameType == Http2Wire.Settings && !initial.hasFlag(Http2Wire.Ack),
        "server preface must be non-ACK SETTINGS",
      )
      values <- settingsValues(initial, fromServer = true)
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
      pendingResponseBytes: Long,
      awaitAck: Boolean = false,
      incomplete: Boolean = false,
      closedStreams: Set[Int] = Set.empty,
  )(implicit peerSettings: Http2PeerSettings[IO]): IO[CapturedRequest] = {
    def loop(
        request: Option[CapturedRequest],
        acknowledged: Boolean,
        remaining: Int,
    ): IO[CapturedRequest] =
      request match {
        case Some(value) if (value.ended || incomplete) && (!awaitAck || acknowledged) =>
          IO.pure(value)
        case _ =>
          observationBound(remaining > 0, "missing request or SETTINGS acknowledgment") *>
            wireObservation(Http2Wire.readFrame(peer))
              .flatTap(frame => peerSettings.positive(peer).flatMap(_.observe(frame, false)))
              .flatMap { frame =>
                frame.frameType match {
                  case Http2Wire.Headers =>
                    val next = request match {
                      case Some(current) =>
                        Check(!current.ended, "HEADERS followed END_STREAM") *>
                          readRequestTrailers(peer, decoder, frame, current.streamId)
                            .as(current.copy(ended = true))
                      case None =>
                        for {
                          _ <- Check(
                            frame.streamId > 0 && (frame.streamId & 1) == 1,
                            "client request stream must be positive and odd",
                          )
                          fields <- wireObservation(Http2Wire.readHeaders(peer, frame, decoder))
                        } yield CapturedRequest(
                          frame.streamId,
                          fields,
                          ByteVector.empty,
                          frame.hasFlag(Http2Wire.EndStream),
                        )
                    }
                    next.flatMap(value => loop(Some(value), acknowledged, remaining - 1))
                  case Http2Wire.Data =>
                    request match {
                      case Some(current) =>
                        for {
                          _ <- Check.equal(frame.streamId, current.streamId)
                          _ <- Check(!current.ended, "DATA followed END_STREAM")
                          bytes <- wireObservation(IO(Http2Wire.unpad(frame)))
                          _ <- observationBound(
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
                    control(peer, frame, fromServer = false, pendingResponseBytes) *> loop(
                      request,
                      acknowledged || frame.hasFlag(Http2Wire.Ack),
                      remaining - 1,
                    )
                  case Http2Wire.RstStream if closedStreams.contains(frame.streamId) =>
                    Check
                      .equal(frame.payload.size, 4L) *> loop(request, acknowledged, remaining - 1)
                  case _ =>
                    control(peer, frame, fromServer = false, pendingResponseBytes) *> loop(
                      request,
                      acknowledged,
                      remaining - 1,
                    )
                }
              }
      }
    loop(None, false, 1024)
  }

  private final case class PendingReset(
      streamId: Int,
      confirmedBy: CapturedResponse => Boolean,
  )

  /** Collect stream frames in wire order, including HPACK updates from output crossing a reset.
    * Only the two reset callers admit that output, until their subsequent exchange confirms
    * reset processing. Their finite DATA budget includes padding and discarded bytes; it does
    * not replenish credit or claim to exercise full connection flow-control accounting.
    */
  private def readResponses(
      peer: RawPeer[IO],
      streams: Set[Int],
      reset: Option[PendingReset] = None,
  )(implicit peerSettings: Http2PeerSettings[IO]): IO[Map[Int, CapturedResponse]] =
    HpackWire.decoder[IO]().flatMap { decoder =>
      def loop(
          current: Map[Int, CapturedResponse],
          remaining: Int,
          pendingReset: Option[PendingReset],
          dataRemaining: Long,
      ): IO[Map[Int, CapturedResponse]] =
        if (current.values.forall(_.ended)) IO.pure(current)
        else
          observationBound(remaining > 0, "responses exceeded frame bound") *>
            wireObservation(Http2Wire.readFrame(peer))
              .flatTap(frame =>
                peerSettings.positive(peer).flatMap(_.observe(frame, fromServer = true))
              )
              .flatMap { frame =>
                val nextDataRemaining =
                  dataRemaining - (if (frame.frameType == Http2Wire.Data) frame.payload.size
                                   else 0L)
                observationBound(
                  nextDataRemaining >= 0L,
                  "reset responses exceeded finite DATA budget",
                ) *>
                  (frame.frameType match {
                    // Both reference-client handshakes disable push before opening requests.
                    case Http2Wire.PushPromise =>
                      IO.raiseError(new ProtocolFailure("PUSH_PROMISE after push disablement"))
                    case Http2Wire.Headers if pendingReset.exists(_.streamId == frame.streamId) =>
                      wireObservation(Http2Wire.readHeaders(peer, frame, decoder)) *>
                        loop(current, remaining - 1, pendingReset, nextDataRemaining)
                    case Http2Wire.Data if pendingReset.exists(_.streamId == frame.streamId) =>
                      wireObservation(IO(Http2Wire.unpad(frame))) *>
                        loop(current, remaining - 1, pendingReset, nextDataRemaining)
                    case Http2Wire.RstStream if pendingReset.exists(_.streamId == frame.streamId) =>
                      Check.equal(frame.payload.size, 4L) *>
                        loop(current, remaining - 1, pendingReset, nextDataRemaining)
                    case Http2Wire.Headers =>
                      for {
                        _ <- Check(
                          streams.contains(frame.streamId),
                          s"HEADERS on unexpected stream ${frame.streamId}",
                        )
                        previous = current(frame.streamId)
                        _ <- Check(!previous.ended, "HEADERS followed END_STREAM")
                        fields <- wireObservation(Http2Wire.readHeaders(peer, frame, decoder))
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
                              statuses.size == 1 && statusCodePattern.pattern
                                .matcher(statuses.head)
                                .matches() &&
                                statuses.head != "101",
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
                        result <- loop(
                          current.updated(frame.streamId, next),
                          remaining - 1,
                          pendingReset.filterNot(_.confirmedBy(next)),
                          nextDataRemaining,
                        )
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
                        body <- wireObservation(IO(Http2Wire.unpad(frame)))
                        _ <- observationBound(
                          previous.body.size + body.size <= 65536L,
                          "response exceeds interaction bound",
                        )
                        next = previous.copy(
                          body = previous.body ++ body,
                          ended = frame.hasFlag(Http2Wire.EndStream),
                        )
                        result <- loop(
                          current.updated(frame.streamId, next),
                          remaining - 1,
                          pendingReset.filterNot(_.confirmedBy(next)),
                          nextDataRemaining,
                        )
                      } yield result
                    case _ =>
                      control(peer, frame, fromServer = true) *> loop(
                        current,
                        remaining - 1,
                        pendingReset,
                        nextDataRemaining,
                      )
                  })
              }
      peerSettings.positive(peer).flatMap { positive =>
        positive.expecting(streams) *> loop(
          streams.iterator.map(_ -> CapturedResponse()).toMap,
          1024,
          reset,
          if (reset.isDefined) 65535L else Long.MaxValue,
        ).flatTap(_ => positive.completed(streams))
      }
    }

  /** No stream frame is skipped on the way to the PING acknowledgment. */
  private def pingBarrier(
      peer: RawPeer[IO],
      fromServer: Boolean,
      zeroWindowStream: Option[Int] = None,
  )(implicit
      peerSettings: Http2PeerSettings[IO]
  ): IO[Unit] = {
    def loop(remaining: Int): IO[Unit] =
      observationBound(remaining > 0, "missing PING acknowledgment") *>
        wireObservation(Http2Wire.readFrame(peer))
          .flatTap(frame => peerSettings.positive(peer).flatMap(_.observe(frame, fromServer)))
          .flatMap { frame =>
            if (frame.frameType == Http2Wire.Ping && frame.hasFlag(Http2Wire.Ack))
              Check.equal(frame.streamId, 0) *> Check.equal(frame.payload, PingBytes)
            else if (frame.frameType == Http2Wire.Data && zeroWindowStream.contains(frame.streamId))
              Check(
                frame.payload.isEmpty,
                "DATA consumed credit while the stream window was zero",
              ) *>
                Check(
                  !frame.hasFlag(Http2Wire.Padded),
                  "empty DATA cannot contain a pad-length octet",
                ) *>
                Check(
                  !frame.hasFlag(Http2Wire.EndStream),
                  "upload ended before its nonempty content",
                ) *>
                loop(remaining - 1)
            else control(peer, frame, fromServer) *> loop(remaining - 1)
          }
    Http2Wire.writeFrame(peer, Http2Wire.Frame(Http2Wire.Ping, 0, 0, PingBytes)) *> loop(1024)
  }

  private def readRequestTrailers(
      peer: RawPeer[IO],
      decoder: HpackWire.Decoder[IO],
      frame: Http2Wire.Frame,
      streamId: Int,
  )(implicit peerSettings: Http2PeerSettings[IO]): IO[Unit] =
    for {
      _ <- Check.equal(frame.streamId, streamId)
      _ <- Check(frame.hasFlag(Http2Wire.EndStream), "request trailers did not end the stream")
      fields <- wireObservation(Http2Wire.readHeaders(peer, frame, decoder))
      _ <- Check(!fields.exists(_._1.startsWith(":")), "request trailers contained a pseudo-header")
    } yield ()

  private def readUpload(
      peer: RawPeer[IO],
      decoder: HpackWire.Decoder[IO],
      streamId: Int,
      initial: ByteVector,
  )(implicit peerSettings: Http2PeerSettings[IO]): IO[ByteVector] = {
    def loop(body: ByteVector, remaining: Int): IO[ByteVector] =
      observationBound(remaining > 0, "upload exceeded frame bound") *> wireObservation(
        Http2Wire
          .readFrame(peer)
          .flatTap(frame =>
            peerSettings.positive(peer).flatMap(_.observe(frame, fromServer = false))
          )
      )
        .flatMap { frame =>
          if (frame.frameType == Http2Wire.Data)
            for {
              _ <- Check.equal(frame.streamId, streamId)
              bytes <- wireObservation(IO(Http2Wire.unpad(frame)))
              _ <- observationBound(
                body.size + bytes.size <= 65536L,
                "upload exceeds interaction bound",
              )
              result <-
                if (frame.hasFlag(Http2Wire.EndStream)) IO.pure(body ++ bytes)
                else loop(body ++ bytes, remaining - 1)
            } yield result
          else if (frame.frameType == Http2Wire.Headers)
            readRequestTrailers(peer, decoder, frame, streamId).as(body)
          else control(peer, frame, fromServer = false) *> loop(body, remaining - 1)
        }
    loop(initial, 1024)
  }

  private def readUploadPrefix(
      peer: RawPeer[IO],
      streamId: Int,
      initial: ByteVector,
      size: Int,
      pendingResponseBytes: Long,
  )(implicit peerSettings: Http2PeerSettings[IO]): IO[ByteVector] = {
    def loop(body: ByteVector, remaining: Int): IO[ByteVector] =
      if (body.size >= size.toLong) Check.equal(body.size, size.toLong).as(body)
      else
        observationBound(remaining > 0, "upload prefix exceeded frame bound") *>
          wireObservation(Http2Wire.readFrame(peer))
            .flatTap(frame => peerSettings.positive(peer).flatMap(_.observe(frame, false)))
            .flatMap { frame =>
              if (frame.frameType == Http2Wire.Data)
                for {
                  _ <- Check.equal(frame.streamId, streamId)
                  _ <- Check(
                    !frame.hasFlag(Http2Wire.EndStream),
                    "partial upload unexpectedly ended",
                  )
                  bytes <- wireObservation(IO(Http2Wire.unpad(frame)))
                  result <- loop(body ++ bytes, remaining - 1)
                } yield result
              else
                control(peer, frame, fromServer = false, pendingResponseBytes) *> loop(
                  body,
                  remaining - 1,
                )
            }
    loop(initial, 1024)
  }

  private def control(
      peer: RawPeer[IO],
      frame: Http2Wire.Frame,
      fromServer: Boolean,
      pendingResponseBytes: Long = 0L,
  )(implicit
      peerSettings: Http2PeerSettings[IO]
  ): IO[Unit] = peerSettings.positive(peer).flatMap { positive =>
    positive.observe(frame, fromServer) *> (frame.frameType match {
      case Http2Wire.Settings =>
        settingsValues(frame, fromServer).flatMap { settings =>
          if (frame.hasFlag(Http2Wire.Ack)) IO.unit
          else
            requireSendWindow(settings, pendingResponseBytes) *>
              positive.incidental(Http2Wire.writeFrame(peer, Http2Wire.settings(ack = true))).void
        }
      case Http2Wire.Ping =>
        Check.equal(frame.streamId, 0) *> Check.equal(frame.payload.size, 8L) *>
          (if (frame.hasFlag(Http2Wire.Ack)) IO.unit
           else
             positive
               .incidental(Http2Wire.writeFrame(peer, frame.copy(flags = Http2Wire.Ack)))
               .void)
      case Http2Wire.GoAway => IO.unit
      case Http2Wire.WindowUpdate =>
        Check.equal(frame.payload.size, 4L) *>
          Check((Http2Wire.readUint32(frame.payload) & 0x7fffffffL) != 0L, "zero window increment")
      case Http2Wire.Priority =>
        Check(frame.streamId != 0 && frame.payload.size == 5L, "invalid PRIORITY")
      case kind if kind > Http2Wire.Continuation => IO.unit
      case _ => Check(false, s"unexpected frame ${frame.frameType} on stream ${frame.streamId}")
    })
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
      allowLengthRequired: Boolean = false,
  ): IO[Unit] =
    for {
      first <- wireObservation(Http1Wire.readFinalResponse(peer))
      // The 411 branch is a policy alternative, not evidence of chunk decoding or reuse.
      _ <-
        if (allowLengthRequired && first.status == 411)
          Http1LifecycleCases.checkLengthRequiredBoundary(peer, server, secondBody)
        else
          for {
            _ <- Check.equal(first.status, 200)
            _ <- Check.equal(first.body, firstBody)
            firstRequest <- server.received
            _ <- Check.equal(firstRequest.path, "/first")
            _ <- Check.equal(firstRequest.body, firstBody)
            _ <- requirePersistence(first.headers)
            second <- nextH1Message(peer)(wireObservation(Http1Wire.readFinalResponse(peer)))
            _ <- Check.equal(second.status, 200)
            _ <- Check.equal(second.body, secondBody)
            secondRequest <- server.received
            _ <- Check.equal(secondRequest.path, "/second")
            _ <- Check.equal(secondRequest.body, secondBody)
          } yield ()
    } yield ()

  private def h1ClientPair(context: CaseContext)(
      exercise: (ClientDriver[IO], IO[Unit]) => IO[Unit]
  )(serve: (RawPeer[IO], IO[Unit]) => IO[Unit]): IO[Unit] =
    Deferred[IO, Unit].flatMap { nextSeen =>
      context.client(Protocol.Http1)(driver => exercise(driver, nextSeen.get))(peer =>
        serve(peer, nextSeen.complete(()).void)
      )
    }

  /** A native second-operation failure cannot race the peer's lawful-closure prerequisite. */
  private def afterH1Continuation[A](nextSeen: IO[Unit])(operation: IO[A]): IO[A] =
    operation.attempt.flatMap {
      case Right(value) => nextSeen.as(value)
      case Left(error) =>
        RawPeer.raiseAdapterOrTimeout[IO](error) *> (error match {
          case _: AssertionError | _: WireObservationLimit => IO.raiseError(error)
          case error => nextSeen *> IO.raiseError(error)
        })
    }

  private def serveH1Pair(peer: RawPeer[IO], method: String, firstResponse: ByteVector)(
      nextSeen: IO[Unit]
  ): IO[Unit] =
    for {
      first <- Http1Wire.readRequest(peer)
      _ <- Check.equal(first.method, method)
      _ <- Check.equal(first.target, "/first")
      _ <- requirePersistence(first.headers)
      _ <- peer.write(firstResponse)
      second <- nextH1Message(peer)(Http1Wire.readRequest(peer))
      _ <- Check.equal(second.target, "/second")
      _ <- nextSeen
      _ <- peer.write(finalH1Response)
    } yield ()

  /** Section 9.3.1 permits EOF between messages, but not successful decoding of a partial message. */
  private def nextH1Message[A](peer: RawPeer[IO])(read: IO[A]): IO[A] =
    peer.awaitInput
      .handleErrorWith { error =>
        RawPeer.raiseAdapterOrTimeout[IO](error) *>
          (if (RuntimeInfo.isConnectionReset(error)) IO.pure(false) else IO.raiseError(error))
      }
      .flatMap { available =>
        prerequisite(available, "connection closed before the next HTTP/1 message") *> read
      }

  private def observationBound(condition: Boolean, detail: String): IO[Unit] =
    if (condition) IO.unit else IO.raiseError(new WireObservationLimit(detail))

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
    peer
      .readToEnd(0)
      .attempt
      .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
      .flatMap {
        case Right(bytes) => Check.equal(bytes, ByteVector.empty)
        case Left(error) if RuntimeInfo.isConnectionReset(error) => IO.unit
        case Left(WireBodyLimit(0)) =>
          Check(false, "additional response bytes followed a response requiring connection closure")
        case Left(error) => IO.raiseError(error)
      }

  /** Independent decoders already distinguish malformed wire bytes from transport failures. */
  private def wireObservation[A](operation: IO[A]): IO[A] =
    operation.handleErrorWith(error =>
      RawPeer.raiseAdapterOrTimeout[IO](error) *> IO.raiseError(error)
    )

  /** Native target operations may reject valid stimuli; independent reads must never use this. */
  private def targetOperation[A](operation: IO[A]): IO[A] =
    operation.handleErrorWith { error =>
      RawPeer.raiseAdapterOrTimeout[IO](error) *> (error match {
        case error: WireObservationLimit => IO.raiseError(error)
        case error: AssertionError => IO.raiseError(error)
        case error => IO.raiseError(new ProtocolFailure(s"valid wire interaction failed: $error"))
      })
    }

}
