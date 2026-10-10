/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.core.h2

import cats.effect._
import fs2.Stream
import org.http4s.Http4sSuite
import scodec.bits.ByteVector

import scala.concurrent.duration.DurationInt

trait H2Suite extends Http4sSuite {

  protected def drainOutgoing(
      h2: H2Connection[IO],
      writes: Ref[IO, ByteVector],
  ): IO[Vector[H2Frame]] =
    h2.writeLoop
      .interruptWhen(Stream.awakeEvery[IO](50.millis).evalMap(_ => h2.state.get.map(_.closed)))
      .compile
      .drain >> writes.get.map(decodeFrames)

  protected def decodeFrames(bv: ByteVector): Vector[H2Frame] = {
    @annotation.tailrec
    def go(rest: ByteVector, acc: Vector[H2Frame]): Vector[H2Frame] =
      H2Frame.RawFrame.fromByteVector(rest) match {
        case Some((raw, tail)) =>
          H2Frame.fromRaw(raw) match {
            case Right(frame) => go(tail, acc :+ frame)
            case Left(_) => acc
          }
        case None => acc
      }
    go(bv, Vector.empty)
  }

  protected def increaseWindowSize(h2: H2Connection[IO], size: Int): IO[Unit] =
    h2.writeWindow.change(size).void

  protected def clearWriteWindow(h2: H2Connection[IO]): IO[Unit] =
    h2.writeWindow.available.flatMap(available => h2.writeWindow.change(-available)).void
}
