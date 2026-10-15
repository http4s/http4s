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
import cats.effect.Resource
import com.comcast.ip4s._
import fs2.Chunk
import scodec.bits.ByteVector

class Http2ClosedWriteSuite extends RuntimeDiagnostics.Suite {
  import Http2Wire._

  private def nativeWriteReset: java.io.IOException =
    new java.io.IOException("Connection reset by peer")

  test("exact native write reset requires further observation") {
    Http2RejectionWrite.reply(IO.raiseError[Unit](nativeWriteReset)).map { result =>
      assertEquals(result, false)
    }
  }

  for (kind <- Vector("wrong-class", "subclass", "wrong-message", "wrapper", "cause", "suppressed"))
    test(s"write reset matcher preserves unrelated failures: $kind") {
      val error = kind match {
        case "wrong-class" => new RuntimeException("Connection reset by peer")
        case "subclass" => new java.net.SocketException("Connection reset by peer")
        case "wrong-message" => new java.io.IOException("Connection reset")
        case "wrapper" => new java.io.IOException("wrapper", nativeWriteReset)
        case "cause" =>
          val outer = nativeWriteReset
          outer.initCause(new RuntimeException("unrelated"))
          outer
        case _ =>
          val outer = nativeWriteReset
          outer.addSuppressed(new RuntimeException("cleanup"))
          outer
      }
      Http2RejectionWrite.reply(IO.raiseError[Unit](error)).attempt.map { result =>
        assert(result.swap.exists(_ eq error), result.toString)
      }
    }

  for {
    kind <- Vector("adapter", "timeout")
    placement <- Vector("cause", "suppressed")
  } test(s"write reset matcher retains failure provenance: $kind/$placement") {
    val nested =
      if (kind == "adapter") new AdapterFailure("fixture", new RuntimeException("failed"))
      else new java.util.concurrent.TimeoutException("deadline")
    val outer = nativeWriteReset
    if (placement == "cause") outer.initCause(nested) else outer.addSuppressed(nested)
    Http2RejectionWrite.reply(IO.raiseError[Unit](outer)).attempt.map { result =>
      if (kind == "adapter") assert(result.swap.exists(_ eq nested), result.toString)
      else {
        assert(
          result.swap.exists(_.isInstanceOf[java.util.concurrent.TimeoutException]),
          result.toString,
        )
        assert(result.swap.exists(_.getCause eq outer), result.toString)
      }
    }
  }

  private final case class Observation(run: IO[Unit], readsAfterFailure: IO[Int], writes: IO[Int])

  private def observation(
      verdict: ByteVector,
      terminal: IO[Option[Chunk[Byte]]],
  ): IO[Observation] = {
    val initial = encode(settings()) ++ encode(settings()) ++ encode(settings()) ++
      encode(Frame(Ping, 0, 0, ByteVector.fill(8)(0))) ++ verdict
    val target = new WireTarget[IO] {
      def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

      val name = "deterministic-write-reset"
      val version = "1"
      def server(protocol: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] =
        Resource.pure(
          ServerHandle[IO](
            SocketAddress(ip"127.0.0.1", port"1"),
            IO.never[RequestData],
            IO.pure(None),
          )
        )
      def client(
          protocol: Protocol,
          address: SocketAddress[IpAddress],
      ): Resource[IO, ClientDriver[IO]] =
        Resource.eval(IO.raiseError(new UnsupportedOperationException("server only")))
    }
    for {
      trace <- Transcript.create[IO]()
      remaining <- Ref.of[IO, ByteVector](initial)
      writes <- Ref.of[IO, Int](0)
      requestWritten <- Ref.of[IO, Boolean](false)
      writeFailed <- Ref.of[IO, Boolean](false)
      readsAfterFailure <- Ref.of[IO, Int](0)
      peer <- RawPeer.create[IO](
        (max: Int) =>
          writeFailed.get.flatMap(failed =>
            if (failed) readsAfterFailure.update(_ + 1) else IO.unit
          ) *>
            remaining.modify(bytes => (bytes.drop(max.toLong), bytes.take(max.toLong))).flatMap {
              case bytes if bytes.nonEmpty => IO.pure(Some(Chunk.byteVector(bytes)))
              case _ => terminal
            },
        (bytes: Chunk[Byte]) =>
          writes.update(_ + 1) *> writeFailed.get.flatMap { failed =>
            Check(!failed, "unexpected reply after injected write reset") *>
              requestWritten.get.flatMap { sent =>
                val wire = ByteVector.view(bytes.toArray)
                if (sent)
                  // Fail the incidental SETTINGS ACK only after the malformed request HEADERS.
                  Check.equal(wire, encode(settings(ack = true))) *>
                    writeFailed.set(true) *> IO.raiseError[Unit](nativeWriteReset)
                else
                  requestWritten.set(
                    decodeAll(wire)
                      .exists(_.exists(frame => frame.frameType == Headers && frame.streamId == 1))
                  )
              }
          },
        IO.unit,
        trace,
      )
      run = Http2MessageCases.all
        .find(_.id == "h2.server.reject-uppercase-field-name")
        .get
        .run(
          CaseContext(target, trace, (_, _) => Resource.pure(peer))
        )
    } yield Observation(run, readsAfterFailure.get, writes.get)
  }

  private val errorGoAway = encode(Frame(GoAway, 0, 0, uint32(1) ++ uint32(1)))

  test("actual case reads independent EOF after failed reply and stops further replies") {
    for {
      eof <- Ref.of[IO, Int](0)
      fixture <- observation(errorGoAway, eof.update(_ + 1).as(None))
      _ <- fixture.run
      reads <- fixture.readsAfterFailure
      writes <- fixture.writes
      ends <- eof.get
    } yield {
      assert(reads > 0)
      assertEquals(writes, 5)
      assertEquals(ends, 1)
    }
  }

  test("actual case rejects malformed captured tail after write reset and independent EOF") {
    for {
      eof <- Ref.of[IO, Int](0)
      fixture <- observation(
        errorGoAway ++ encode(Frame(WindowUpdate, 0, 0, uint32(0))),
        eof.update(_ + 1).as(None),
      )
      error <- fixture.run.intercept[ProtocolFailure]
      ends <- eof.get
    } yield {
      assertEquals(error.getMessage, "zero WINDOW_UPDATE")
      assertEquals(ends, 1)
    }
  }

  for (verdict <- Vector("none", "stream-reset", "goaway"))
    test(s"actual case preserves subsequent read failure instead of inferring closure: $verdict") {
      val readError = nativeWriteReset
      val bytes = verdict match {
        case "none" => ByteVector.empty
        case "stream-reset" => encode(Frame(RstStream, 0, 1, uint32(1)))
        case _ => errorGoAway
      }
      for {
        fixture <- observation(bytes, IO.raiseError(readError))
        result <- fixture.run.attempt
        reads <- fixture.readsAfterFailure
      } yield {
        assert(result.swap.exists(_ eq readError), result.toString)
        assert(reads > 0)
      }
    }

  for (verdict <- Vector("none", "stream-reset", "goaway"))
    test(s"actual case remains pending on held-open read after write reset: $verdict") {
      val bytes = verdict match {
        case "none" => ByteVector.empty
        case "stream-reset" => encode(Frame(RstStream, 0, 1, uint32(1)))
        case _ => errorGoAway
      }
      for {
        waiting <- Deferred[IO, Unit]
        completed <- Deferred[IO, Either[Throwable, Unit]]
        fixture <- observation(bytes, waiting.complete(()).void *> IO.never[Option[Chunk[Byte]]])
        _ <- Resource.make(fixture.run.attempt.flatMap(completed.complete).start)(_.cancel).use {
          _ =>
            IO.race(waiting.get, completed.get).flatMap {
              case Left(_) => completed.tryGet.flatMap(result => IO(assertEquals(result, None)))
              case Right(result) => IO(fail(s"case completed before independent read: $result"))
            }
        }
      } yield ()
    }
}
