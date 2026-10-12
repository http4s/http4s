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
import com.comcast.ip4s._
import fs2.Chunk
import fs2.io.net.Network

private[wire] object Http2WriteFailureFixture {
  // The exact FS2 exception is package-private. Capture its native representation,
  // keeping this platform dependency out of the shared scripted socket.
  def closedWrite: IO[Throwable] = for {
    closed <- Deferred[IO, Unit]
    error <- Network[IO].bind(SocketAddress(ip"127.0.0.1", port"0")).use { listener =>
      val remote = listener.accept.head.compile.resource.lastOrError
        .use(_ => IO.unit) *> closed.complete(()).void
      remote.background.use { _ =>
        Network[IO].connect(listener.address.asIpUnsafe).use { socket =>
          def loop(remaining: Int): IO[Throwable] =
            if (remaining == 0)
              IO.raiseError(
                new IllegalStateException(
                  "native closed-write fixture did not observe a write failure"
                )
              )
            else
              socket.write(Chunk.singleton(0.toByte)).attempt.flatMap {
                case Left(error) => IO.pure(error)
                case Right(_) => IO.cede *> loop(remaining - 1)
              }
          closed.get *> socket.read(1) *> loop(256)
        }
      }
    }
  } yield error
}
