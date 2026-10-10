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

import cats.effect.IO
import cats.syntax.all._
import fs2.Chunk
import fs2.Stream
import munit.CatsEffectSuite
import scodec.bits.ByteVector

import scala.concurrent.duration._

class NettyTargetSuite extends CatsEffectSuite {
  private val target = NettyTarget[IO]()
  private val pieces = Vector.tabulate(12)(i => ByteVector.fill(16384)(i.toByte))
  private val body = pieces.foldLeft(ByteVector.empty)(_ ++ _)
  private val duplicateHeaders = Vector("x-repeat" -> "first", "x-repeat" -> "second")

  private def upload: Stream[IO, Byte] =
    Stream.emits(pieces).flatMap(value => Stream.chunk(Chunk.byteVector(value)))

  Vector(Protocol.Http1, Protocol.Http2).foreach { protocol =>
    test(
      s"${protocol.id} streams an upload beyond the HTTP/2 initial window and reuses the driver"
    ) {
      target
        .server(protocol, ServerScenario.EchoBody)
        .use { server =>
          target.client(protocol, server.address).use { client =>
            def exchange = client
              .exchangeStream(RequestData("POST", "/echo", duplicateHeaders), upload)
              .use { response =>
                for {
                  received <- response.body.compile.to(Chunk)
                  trailers <- response.trailers
                } yield {
                  assertEquals(response.status, 200)
                  assertEquals(received.toByteVector, body)
                  assertEquals(trailers, Vector.empty[(String, String)])
                }
              }
            for {
              _ <- exchange
              _ <- exchange
              first <- server.received
              second <- server.received
            } yield Vector(first, second).foreach { request =>
              assertEquals(request.method, "POST")
              assertEquals(request.path, "/echo")
              assertEquals(
                request.headers.filter(_._1.equalsIgnoreCase("x-repeat")),
                duplicateHeaders,
              )
              assertEquals(request.body, body)
            }
          }
        }
        .timeout(10.seconds)
    }

    test(s"${protocol.id} streams response chunks beyond the HTTP/2 initial window") {
      target
        .server(protocol, ServerScenario.ReplyChunks(pieces))
        .use { server =>
          target.client(protocol, server.address).use { client =>
            client.exchange(RequestData()).use { response =>
              response.body.compile.to(Chunk).map(result => assertEquals(result.toByteVector, body))
            }
          }
        }
        .timeout(10.seconds)
    }
  }

  test("HTTP/1 uses one TCP connection for sequential exchanges and exposes trailers") {
    Transcript
      .create[IO]()
      .flatMap { transcript =>
        CaseContext(target, transcript).client(Protocol.Http1) { client =>
          def exchange = client.exchange(RequestData()).use { response =>
            for {
              result <- response.body.compile.to(Chunk)
              trailers <- response.trailers
            } yield {
              assertEquals(result.toByteVector, Http1Wire.ascii("a"))
              assertEquals(response.headers.filter(_._1 == "x-repeat"), duplicateHeaders)
              assertEquals(trailers, Vector("x-trailer" -> "yes"))
            }
          }
          exchange *> exchange
        } { peer =>
          val response =
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n" +
              "x-repeat: first\r\nx-repeat: second\r\n\r\n" +
              "1\r\na\r\n0\r\nx-trailer: yes\r\n\r\n"
          (Http1Wire.readRequest(peer) *> peer.writeAscii(response)).replicateA_(2)
        }
      }
      .timeout(10.seconds)
  }

  test("HTTP/1 premature close fails the body instead of completing it") {
    Transcript
      .create[IO]()
      .flatMap { transcript =>
        CaseContext(target, transcript).client(Protocol.Http1) { client =>
          client.exchange(RequestData()).use { response =>
            response.body.compile.drain.attempt.map(result => assert(result.isLeft))
          }
        } { peer =>
          Http1Wire.readRequest(peer) *>
            peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nshort") *>
            peer.endOfOutput
        }
      }
      .timeout(10.seconds)
  }

  test("HTTP/1 reports a native request decoder failure as 400") {
    Transcript
      .create[IO]()
      .flatMap { transcript =>
        CaseContext(target, transcript).server(Protocol.Http1).use { case (peer, _) =>
          peer.writeAscii("GET / HTTP/1.1\r\nbad header: value\r\n\r\n") *>
            Http1Wire.readResponse(peer).map(response => assertEquals(response.status, 400))
        }
      }
      .timeout(10.seconds)
  }

  test("HTTP/2 supports concurrent streams on one driver") {
    target
      .server(Protocol.Http2, ServerScenario.EchoBody)
      .use { server =>
        target.client(Protocol.Http2, server.address).use { client =>
          Vector("/first", "/second").parTraverse_ { path =>
            client.exchangeStream(RequestData("POST", path), upload).use { response =>
              response.body.compile.to(Chunk).map(result => assertEquals(result.toByteVector, body))
            }
          }
        }
      }
      .timeout(10.seconds)
  }
}
