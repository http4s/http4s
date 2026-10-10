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

import cats.effect.Async
import cats.effect.Ref
import cats.effect.std.Semaphore
import cats.syntax.all._
import fs2.Chunk
import fs2.io.net.Socket
import scodec.bits.ByteVector

/** Buffers socket reads without owning the socket's lifetime.
  *
  * There must be one message reader at a time. Individual reads and writes are serialized,
  * and input remains buffered if a read is canceled before its requested boundary is found.
  */
final class RawPeer[F[_]] private (
    socket: Socket[F],
    val transcript: Transcript[F],
    buffered: Ref[F, ByteVector],
    eof: Ref[F, Boolean],
    readLock: Semaphore[F],
    writeLock: Semaphore[F],
)(implicit F: Async[F]) {
  private def fail[A](message: String): F[A] =
    F.raiseError(new IllegalArgumentException(message))

  private def receive(maxBytes: Int): F[Boolean] = eof.get.flatMap {
    case true => false.pure[F]
    case false =>
      F.uncancelable { poll =>
        poll(socket.read(maxBytes)).flatMap {
          case None => eof.set(true).as(false)
          case Some(chunk) =>
            val bytes = ByteVector.view(chunk.toArray)
            transcript.record("received", bytes) *> buffered.update(_ ++ bytes).as(true)
        }
      }
  }

  def readExact(size: Int): F[ByteVector] = readLock.permit.use { _ =>
    def loop: F[ByteVector] = buffered.get.flatMap { bytes =>
      if (bytes.size >= size.toLong)
        buffered.set(bytes.drop(size.toLong)).as(bytes.take(size.toLong))
      else
        receive(math.min(8192, size - bytes.size.toInt)).flatMap {
          case true => F.defer(loop)
          case false => fail(s"unexpected EOF: needed $size bytes, received ${bytes.size}")
        }
    }
    if (size < 0) fail("negative exact-read size") else loop
  }

  /** Consumes the delimiter and returns the preceding bytes. */
  def readUntil(delimiter: ByteVector, maxBytes: Int = 65536): F[ByteVector] =
    readLock.permit.use { _ =>
      def loop(from: Long): F[ByteVector] = buffered.get.flatMap { bytes =>
        val index = bytes.indexOfSlice(delimiter, from)
        if (index >= 0 && index <= maxBytes.toLong)
          buffered.set(bytes.drop(index + delimiter.size)).as(bytes.take(index))
        else if (index > maxBytes.toLong || bytes.size >= maxBytes.toLong + delimiter.size)
          fail(s"delimiter not found within $maxBytes bytes")
        else {
          val nextFrom = math.max(0L, bytes.size - delimiter.size + 1L)
          val capacity = maxBytes.toLong + delimiter.size - bytes.size
          receive(math.min(8192L, capacity).toInt).flatMap {
            case true => F.defer(loop(nextFrom))
            case false => fail("unexpected EOF before delimiter")
          }
        }
      }
      if (delimiter.isEmpty || maxBytes < 0) fail("invalid delimiter or read limit")
      else loop(0L)
    }

  def readToEnd(maxBytes: Int = 16 * 1024 * 1024): F[ByteVector] =
    readLock.permit.use { _ =>
      def loop: F[ByteVector] = buffered.get.flatMap { bytes =>
        if (bytes.size > maxBytes.toLong) fail(s"body exceeds $maxBytes bytes")
        else
          receive(math.min(8192L, maxBytes.toLong - bytes.size + 1L).toInt).flatMap {
            case true => F.defer(loop)
            case false => buffered.set(ByteVector.empty).as(bytes)
          }
      }
      if (maxBytes < 0) fail("negative read limit") else loop
    }

  def write(bytes: ByteVector): F[Unit] = writeLock.permit.use { _ =>
    transcript.record("sending", bytes) *> socket.write(Chunk.byteVector(bytes))
  }

  def writeAscii(value: String): F[Unit] =
    F.fromEither(ByteVector.encodeAscii(value)).flatMap(write)

  def endOfOutput: F[Unit] = writeLock.permit.use(_ => socket.endOfOutput)
}

object RawPeer {
  def create[F[_]: Async](socket: Socket[F], transcript: Transcript[F]): F[RawPeer[F]] =
    for {
      buffered <- Ref.of[F, ByteVector](ByteVector.empty)
      eof <- Ref.of[F, Boolean](false)
      readLock <- Semaphore[F](1)
      writeLock <- Semaphore[F](1)
    } yield new RawPeer(socket, transcript, buffered, eof, readLock, writeLock)
}
