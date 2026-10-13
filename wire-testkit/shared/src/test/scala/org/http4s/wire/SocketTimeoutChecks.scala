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
import cats.effect.Resource
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Stream
import fs2.io.net.Network
import munit.CatsEffectSuite

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeoutException

abstract class SocketTimeoutChecks extends CatsEffectSuite {
  protected def socketTimeout(subclass: Boolean = false): Throwable
  protected def connectionReset: Throwable

  private val cases = Vector(
    "h1.client.reject-conflicting-content-length",
    "h1.client.field-nul",
    "h1.client.empty-reason-phrase",
  )

  private def socketFailure(shape: String): Throwable = shape match {
    case "direct" => socketTimeout()
    case "subclass" => socketTimeout(subclass = true)
    case "cause" => new IOException("native wrapper", socketTimeout())
    case "suppressed" =>
      val error = new IOException("native wrapper")
      error.addSuppressed(socketTimeout())
      error
    case "cycle" =>
      val first = new IOException("first")
      val second = new IOException("second", first)
      first.initCause(second)
      second.addSuppressed(socketTimeout())
      first
  }

  for {
    id <- cases
    inBody <- Vector(false, true)
    shape <- Vector("direct", "subclass", "cause", "suppressed", "cycle")
  } test(s"$id socket timeout is inconclusive: $shape/body=$inBody") {
    val error = socketFailure(shape)
    runClient(id, error, inBody).attempt.map { result =>
      assertEquals(WireSuite.outcomeStatus(result), "inconclusive")
      val normalized = result.swap.toOption.get
      assert(normalized.isInstanceOf[TimeoutException])
      assert(normalized.getCause eq error, "normalization must retain the original exception graph")
    }
  }

  for {
    id <- cases
    inBody <- Vector(false, true)
  } {
    test(s"$id util timeout retains its identity: body=$inBody") {
      val error = new TimeoutException("util deadline")
      runClient(id, error, inBody).attempt.map { result =>
        assertEquals(result, Left(error))
        assertEquals(WireSuite.outcomeStatus(result), "inconclusive")
      }
    }

    for (kind <- Vector("parse", "timeout-message", "interrupted"))
      test(s"$id ordinary rejection remains unchanged: $kind/body=$inBody") {
        val error = kind match {
          case "parse" => new IOException("native parse rejected")
          case "timeout-message" =>
            new IOException("java.net.SocketTimeoutException: Read timed out")
          case "interrupted" => new InterruptedIOException("Read timed out")
        }
        runClient(id, error, inBody).attempt.map { result =>
          if (id == "h1.client.empty-reason-phrase") {
            assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
            assertEquals(WireSuite.outcomeStatus(result), "fail")
          } else assertEquals(result, Right(()))
        }
      }

    test(s"$id adapter failure takes precedence over socket timeout: body=$inBody") {
      val error = socketTimeout()
      val adapter = new AdapterFailure("fixture failed", new IOException("fixture"))
      error.addSuppressed(adapter)
      runClient(id, error, inBody).attempt.map { result =>
        assertEquals(result, Left(adapter))
        assertEquals(WireSuite.outcomeStatus(result), "error")
      }
    }
  }

  test("a cause/suppressed cycle without a timeout terminates without normalization") {
    val first = new IOException("first")
    val second = new IOException("second", first)
    first.initCause(second)
    second.addSuppressed(first)
    RawPeer.raiseAdapterOrTimeout[IO](first)
  }

  for (suppressed <- Vector(false, true))
    test(s"nested util timeout retains the original outer cause: suppressed=$suppressed") {
      val error = new IOException("wrapper")
      val timeout = new TimeoutException("util deadline")
      if (suppressed) error.addSuppressed(timeout) else error.initCause(timeout)
      RawPeer.raiseAdapterOrTimeout[IO](error).attempt.map { result =>
        assertEquals(WireSuite.outcomeStatus(result), "inconclusive")
        assert(result.swap.toOption.get.getCause eq error)
      }
    }

  test("adapter failure wins even when a sibling socket timeout is visited first") {
    val error = new IOException("wrapper", socketTimeout())
    val adapter = new AdapterFailure("fixture failed", new IOException("fixture"))
    error.addSuppressed(adapter)
    RawPeer
      .raiseAdapterOrTimeout[IO](error)
      .attempt
      .map(result => assertEquals(result, Left(adapter)))
  }

  test("socket timeouts are not connection resets or closed writes") {
    Vector(socketTimeout(), socketTimeout(subclass = true)).foreach { error =>
      assert(RuntimeInfo.isSocketTimeout(error))
      assert(!RuntimeInfo.isConnectionReset(error))
      assert(!RuntimeInfo.isClosedWrite(error))
    }
  }

  test("connection resets retain their existing classification") {
    val error = connectionReset
    assert(RuntimeInfo.isConnectionReset(error))
    assert(!RuntimeInfo.isSocketTimeout(error))
    RawPeer.raiseAdapterOrTimeout[IO](error)
  }

  for (shape <- Vector("cause", "suppressed", "nested", "cycle", "adapter"))
    test(s"HTTP/2 reset classification preserves socket timeout provenance: $shape") {
      val reset = connectionReset
      val timeout = socketTimeout()
      val adapter = new AdapterFailure("fixture read", reset)
      val error = shape match {
        case "cause" => timeout.initCause(reset)
        case "suppressed" => reset.addSuppressed(timeout); reset
        case "nested" => new IOException("outer", timeout.initCause(reset))
        case "cycle" =>
          val outer = new IOException("outer", reset)
          reset.initCause(outer)
          outer.addSuppressed(timeout)
          outer
        case _ => timeout.initCause(reset); timeout.addSuppressed(adapter); timeout
      }
      val expected = if (shape == "adapter") "error" else "inconclusive"
      val raw = WireCodecsSuite
        .peer(scodec.bits.ByteVector.empty, whenEmpty = IO.raiseError(error))
        .map(_._1)
      for {
        boundary <- raw.flatMap(Http2Wire.readFrameOrClose[IO](_)).attempt
        actual <- WireCodecsSuite
          .runH2ServerCase("h2.server.reject-preface-magic", Resource.eval(raw))
          .attempt
      } yield {
        assertEquals(WireSuite.outcomeStatus(boundary.void), expected)
        assertEquals(WireSuite.outcomeStatus(actual), expected)
        if (shape == "adapter") {
          assertEquals(boundary, Left(adapter))
          assertEquals(actual, Left(adapter))
        } else {
          assert(boundary.swap.toOption.get.getCause eq error)
          assert(actual.swap.toOption.get.getCause eq error)
        }
      }
    }

  private def runClient(id: String, error: Throwable, inBody: Boolean): IO[Unit] = {
    val (head, body) = id match {
      case "h1.client.reject-conflicting-content-length" =>
        ("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nContent-Length: 3", "abc")
      case "h1.client.field-nul" =>
        ("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX-Wire: one\u0000two", "ok")
      case "h1.client.empty-reason-phrase" =>
        ("HTTP/1.1 200 \r\nContent-Length: 2", "ok")
    }
    val target = new WireTarget[IO] {
      val name = "socket-timeout-client"
      val version = "oracle-challenge"
      def server(protocol: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.eval(IO.raiseError(new UnsupportedOperationException("client only")))
      def client(
          protocol: Protocol,
          address: SocketAddress[IpAddress],
      ): Resource[IO, ClientDriver[IO]] =
        for {
          socket <- Network[IO].connect(address)
          transcript <- Resource.eval(Transcript.create[IO]())
          peer <- Resource.eval(RawPeer.create(socket, transcript))
        } yield new ClientDriver[IO] {
          def exchange(request: RequestData): Resource[IO, ResponseData[IO]] =
            Resource.eval {
              for {
                _ <- peer.writeAscii(
                  s"${request.method} ${request.path} HTTP/1.1\r\nHost: wire.test\r\nContent-Length: 0\r\n\r\n"
                )
                receivedHead <- peer.readUntil(Http1Wire.ascii("\r\n\r\n"))
                receivedBody <- peer.readExact(body.length)
                _ <- IO {
                  assertEquals(receivedHead, Http1Wire.ascii(head))
                  assertEquals(receivedBody, Http1Wire.ascii(body))
                }
                response <-
                  if (inBody)
                    IO.pure(
                      ResponseData[IO](
                        200,
                        Vector.empty,
                        Stream.raiseError[IO](error),
                        IO.pure(Vector.empty),
                      )
                    )
                  else IO.raiseError[ResponseData[IO]](error)
              } yield response
            }
          def exchangeStream(
              request: RequestData,
              body: FixtureCleanup[IO] => Stream[IO, Byte],
          ): Resource[IO, ResponseData[IO]] = exchange(request)
        }
    }
    val selected = (SmokeCases.all ++ Http1SyntaxCases.all).find(_.id == id).get
    Transcript.create[IO]().flatMap(t => selected.run(CaseContext(target, t)))
  }
}
