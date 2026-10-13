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
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.std.Queue
import fs2.Chunk
import scodec.bits.ByteVector

/** Deterministic duplex transport for protocol oracles; socket behavior has separate integration tests. */
private[wire] object MemoryPeer {
  def pair(left: Transcript[IO], right: Transcript[IO]): Resource[IO, (RawPeer[IO], RawPeer[IO])] =
    for {
      toLeft <- Resource.eval(Queue.unbounded[IO, Option[Chunk[Byte]]])
      toRight <- Resource.eval(Queue.unbounded[IO, Option[Chunk[Byte]]])
      a <- peer(toLeft, toRight, left)
      b <- peer(toRight, toLeft, right)
    } yield (a, b)

  private def peer(
      input: Queue[IO, Option[Chunk[Byte]]],
      output: Queue[IO, Option[Chunk[Byte]]],
      transcript: Transcript[IO],
  ): Resource[IO, RawPeer[IO]] =
    Resource.eval(Ref.of[IO, ByteVector](ByteVector.empty)).flatMap { pending =>
      def read(n: Int): IO[Option[Chunk[Byte]]] = pending.get.flatMap { bytes =>
        if (bytes.nonEmpty)
          pending.set(bytes.drop(n.toLong)).as(Some(Chunk.byteVector(bytes.take(n.toLong))))
        else
          input.take.flatMap {
            case None => IO.pure(None)
            case Some(chunk) => pending.set(chunk.toByteVector) *> IO.defer(read(n))
          }
      }
      Resource.make(
        RawPeer.create[IO](
          read(_),
          (chunk: Chunk[Byte]) => output.offer(Some(chunk)),
          output.offer(None),
          transcript,
        )
      )(_ => output.offer(None))
    }
}
