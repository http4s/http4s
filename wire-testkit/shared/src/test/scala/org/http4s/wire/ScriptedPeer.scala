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
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import scodec.bits.ByteVector

/** A finite byte source with an explicit exhaustion policy (EOF, failure, or blocked read). */
private[wire] object ScriptedPeer {

  /** Frame-aligned scripted writes, optionally prefixed by the complete HTTP/2 client preface.
    * Fragmented-write behavior belongs to byte-level and socket tests, not this interpreter.
    * Transitions retain their state even when a write fails, so probes can assert the failure.
    */
  def frames[S](
      state: Ref[IO, S],
      take: (S, Int) => (S, ByteVector),
      receive: (S, Http2Wire.Frame) => (S, Option[Throwable]),
      transcript: Transcript[IO],
  ): IO[RawPeer[IO]] = RawPeer.create[IO](
    (n: Int) =>
      state.modify(s => take(s, n)).map(b => if (b.isEmpty) None else Some(Chunk.byteVector(b))),
    (chunk: Chunk[Byte]) => {
      val bytes = ByteVector(chunk.toArray)
      val wire =
        if (bytes.startsWith(Http2Wire.clientPreface))
          bytes.drop(Http2Wire.clientPreface.size)
        else bytes
      IO.fromEither(Http2Wire.decodeAll(wire))
        .flatMap(_.traverse_ { frame =>
          state.modify(s => receive(s, frame)).flatMap(_.fold(IO.unit)(IO.raiseError[Unit]))
        })
    },
    IO.unit,
    transcript,
  )

  def peer(
      bytes: ByteVector,
      fragment: Int = 8192,
      whenEmpty: IO[Option[Chunk[Byte]]] = IO.pure(None),
  ): IO[(RawPeer[IO], IO[ByteVector])] =
    for {
      input <- Ref.of[IO, ByteVector](bytes)
      output <- Ref.of[IO, ByteVector](ByteVector.empty)
      transcript <- Transcript.create[IO]()
      read = (maxBytes: Int) =>
        input
          .modify { remaining =>
            if (remaining.isEmpty) (remaining, None)
            else {
              val (head, tail) = remaining.splitAt(math.min(maxBytes, fragment).toLong)
              (tail, Some(Chunk.byteVector(head)))
            }
          }
          .flatMap(_.fold(whenEmpty)(chunk => IO.pure(Some(chunk))))
      write = (chunk: Chunk[Byte]) => output.update(_ ++ ByteVector.view(chunk.toArray))
      raw <- RawPeer.create(read, write, IO.unit, transcript)
    } yield (raw, output.get)
  def runServerBytes(
      id: String,
      bytes: ByteVector,
      whenEmpty: IO[Option[Chunk[Byte]]],
      received: IO[RequestData] =
        IO.raiseError(new AssertionError("unexpected application observation")),
  ): IO[Unit] = {
    val target = ScriptedServer[IO](
      "scripted-h1-reset",
      Resource.pure(
        ServerHandle[IO](
          SocketAddress(ip"127.0.0.1", port"1"),
          received,
          IO.pure(None),
        )
      ),
    )
    for {
      raw <- ScriptedPeer.peer(bytes, whenEmpty = whenEmpty)
      transcript <- Transcript.create[IO]()
      _ <- Catalog.all
        .find(_.id == id)
        .get
        .run(CaseContext(target, transcript, (_, _) => Resource.pure(raw._1)))
      written <- raw._2
      _ <- IO(assert(written.nonEmpty, "actual case must send its request stimulus"))
    } yield ()
  }

}
