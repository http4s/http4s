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
import cats.effect.Deferred
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import fs2.io.net.Network

class Http2RejectionWriteSuite extends RuntimeDiagnostics.Suite {
  for (fullClose <- Vector(false, true))
    test(s"transport write after peer closure: full=$fullClose") {
      for {
        closed <- Deferred[IO, Unit]
        _ <- Network[IO].bind(SocketAddress(ip"127.0.0.1", port"0")).use { listener =>
          val remote = listener.accept.head.compile.resource.lastOrError.use { socket =>
            if (fullClose) IO.unit
            else socket.endOfOutput *> closed.complete(()).void *> IO.never[Unit]
          } *> closed.complete(()).void
          remote.background.use { _ =>
            Network[IO].connect(listener.address.asIpUnsafe).use { socket =>
              for {
                _ <- closed.get
                eof <- socket.read(1)
                _ <- IO(assertEquals(eof, None))
                results <- (0 until 8).toVector
                  .traverse(_ => socket.write(Chunk.singleton(0.toByte)).attempt)
                errors = results.collect { case Left(error) => error }
                _ <- IO.println(
                  s"Transport close evidence ${RuntimeInfo.platform}/full=$fullClose: " + errors
                    .map(e =>
                      s"${e.getClass.getName}:${e.getMessage}; cause=${e.getCause}; suppressed=${e.getSuppressed.toList}"
                    )
                    .distinct
                    .mkString(" | ")
                )
                _ <- IO(
                  if (!fullClose) assert(errors.isEmpty, errors.toString)
                  else
                    errors.headOption
                      .foreach(error => assert(RuntimeInfo.isClosedWrite(error), error.toString))
                )
              } yield ()
            }
          }
        }
      } yield ()
    }
  test("recognized failed reply requires further observation") {
    Http2WriteFailureFixture.closedWrite
      .flatMap(error => Http2RejectionWrite.reply(IO.raiseError[Unit](error)))
      .map(result => assertEquals(result, false))
  }

  for (placement <- Vector("direct", "cause", "suppressed", "cycle")) {
    test(s"adapter provenance precedes reply classification: $placement") {
      Http2WriteFailureFixture.closedWrite.flatMap { nativeError =>
        val adapter = new AdapterFailure("fixture release", new RuntimeException("cleanup"))
        val error = placement match {
          case "direct" => adapter
          case "cause" => new RuntimeException("wrapper", adapter)
          case "suppressed" => val outer = nativeError; outer.addSuppressed(adapter); outer
          case _ =>
            val outer = new RuntimeException("cycle");
            val other = new RuntimeException("other", outer); outer.initCause(other);
            outer.addSuppressed(adapter); outer
        }
        Http2RejectionWrite
          .reply(IO.raiseError[Unit](error))
          .attempt
          .map(result => assert(result.swap.exists(_ eq adapter)))
      }
    }
    test(s"timeout provenance precedes reply classification: $placement") {
      Http2WriteFailureFixture.closedWrite.flatMap { nativeError =>
        val timeout = new java.util.concurrent.TimeoutException("deadline")
        val error = placement match {
          case "direct" => timeout
          case "cause" => new RuntimeException("wrapper", timeout)
          case "suppressed" => val outer = nativeError; outer.addSuppressed(timeout); outer
          case _ =>
            val outer = new RuntimeException("cycle");
            val other = new RuntimeException("other", outer); outer.initCause(other);
            outer.addSuppressed(timeout); outer
        }
        Http2RejectionWrite
          .reply(IO.raiseError[Unit](error))
          .attempt
          .map(result =>
            assert(result.swap.exists(_.isInstanceOf[java.util.concurrent.TimeoutException]))
          )
      }
    }
  }

  for (kind <- Vector("arbitrary-io", "same-message", "wrapper", "suppressed", "cause"))
    test(s"unrelated write error is preserved: $kind") {
      Http2WriteFailureFixture.closedWrite.flatMap { nativeError =>
        val error = kind match {
          case "arbitrary-io" => new java.io.IOException("disk unavailable")
          case "same-message" => new RuntimeException("Broken pipe")
          case "wrapper" => new RuntimeException("wrapper", nativeError)
          case "suppressed" =>
            val outer = nativeError; outer.addSuppressed(new RuntimeException("cleanup")); outer
          case _ =>
            val outer = nativeError;
            if (outer.getCause == null) outer.initCause(new RuntimeException("unrelated"))
            else outer.getCause.addSuppressed(new RuntimeException("unrelated"));
            outer
        }
        Http2RejectionWrite
          .reply(IO.raiseError[Unit](error))
          .attempt
          .map(result => assert(result.swap.exists(_ eq error)))
      }
    }

  test("cancellation remains cancellation") {
    for {
      fiber <- Http2RejectionWrite.reply(IO.canceled *> IO.never[Unit]).start
      result <- fiber.join
    } yield assert(result.isCanceled)
  }

}
