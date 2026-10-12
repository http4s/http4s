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

import cats.ApplicativeThrow
import cats.effect.Async
import cats.effect.Ref
import cats.effect.std.Semaphore
import cats.syntax.all._
import fs2.Chunk
import fs2.io.net.Socket
import scodec.bits.ByteVector

import java.util.concurrent.TimeoutException

/** Buffers socket reads without owning the socket's lifetime.
  *
  * There must be one message reader at a time. Individual reads and writes are serialized,
  * and input remains buffered if a read is canceled before its requested boundary is found.
  */
final class RawPeer[F[_]] private (
    read: Int => F[Option[Chunk[Byte]]],
    send: Chunk[Byte] => F[Unit],
    shutdownOutput: F[Unit],
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
        poll(read(maxBytes))
          .handleErrorWith { error =>
            RawPeer.raiseAdapterOrTimeout[F](error) *> F.raiseError(error)
          }
          .flatMap {
            case None => eof.set(true).as(false)
            case Some(chunk) =>
              val bytes = ByteVector.view(chunk.toArray)
              transcript.record("received", bytes) *> buffered.update(_ ++ bytes).as(true)
          }
      }
  }

  /** Waits for input without consuming it; false means EOF at the current message boundary. */
  def awaitInput: F[Boolean] = readLock.permit.use { _ =>
    buffered.get.flatMap(bytes => if (bytes.nonEmpty) true.pure[F] else receive(1))
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
          F.raiseError(new WireObservationLimit(s"delimiter not found within $maxBytes bytes"))
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

  /** An empty EOF or recognized reset is an absent message; buffered partial bytes are malformed.
    * Like readUntil, a canceled read keeps its bytes for the next reader.
    */
  private[wire] def readUntilOrClosed(
      delimiter: ByteVector,
      maxBytes: Int,
  ): F[Option[ByteVector]] =
    readUntil(delimiter, maxBytes).map(Option(_)).handleErrorWith { error =>
      RawPeer.raiseAdapterOrTimeout[F](error) *> {
        val closed = RuntimeInfo.isConnectionReset(error) || (error match {
          case failure: IllegalArgumentException =>
            failure.getMessage == "unexpected EOF before delimiter"
          case _ => false
        })
        if (!closed) F.raiseError(error)
        else
          readLock.permit.use { _ =>
            eof.set(true) *> buffered.get.flatMap { bytes =>
              if (bytes.isEmpty) none[ByteVector].pure[F]
              else
                F.raiseError(
                  new WireDecodingFailure(s"truncated target response head: ${bytes.toHex}")
                )
            }
          }
      }
    }

  /** Reads through EOF, optionally accepting a connection reset after the buffered bytes. */
  def readToEnd(
      maxBytes: Int = 16 * 1024 * 1024,
      allowReset: Boolean = false,
  ): F[ByteVector] =
    readLock.permit.use { _ =>
      def loop: F[ByteVector] = buffered.get.flatMap { bytes =>
        if (bytes.size > maxBytes.toLong)
          F.raiseError(new WireObservationLimit(s"body exceeds $maxBytes bytes"))
        else
          receive(math.min(8192L, maxBytes.toLong - bytes.size + 1L).toInt)
            .handleErrorWith { case error =>
              RawPeer.raiseAdapterOrTimeout[F](error) *>
                (if (allowReset && RuntimeInfo.isConnectionReset(error)) eof.set(true).as(false)
                 else F.raiseError(error))
            }
            .flatMap {
              case true => F.defer(loop)
              case false => buffered.getAndSet(ByteVector.empty)
            }
      }
      if (maxBytes < 0) fail("negative read limit") else loop
    }

  def write(bytes: ByteVector): F[Unit] = writeLock.permit.use { _ =>
    transcript.record("sending", bytes) *> send(Chunk.byteVector(bytes))
  }

  def writeAscii(value: String): F[Unit] =
    F.fromEither(ByteVector.encodeAscii(value)).flatMap(write)

  def endOfOutput: F[Unit] = writeLock.permit.use(_ => shutdownOutput)
}

object RawPeer {

  /** Preserve adapter failures first, then normalize cause/suppressed transport/native deadlines. */
  private[wire] def raiseAdapterOrTimeout[F[_]: ApplicativeThrow](error: Throwable): F[Unit] = {
    @annotation.tailrec
    def timedOut(pending: List[Throwable], seen: Set[Throwable]): Boolean = pending match {
      case Nil => false
      case current :: rest if current == null || seen(current) => timedOut(rest, seen)
      case current :: _ if current.isInstanceOf[TimeoutException] => true
      case current :: rest =>
        timedOut(current.getCause :: current.getSuppressed.toList ::: rest, seen + current)
    }

    AdapterFailure.raiseIfPresent[F](error) *> {
      if (!timedOut(List(error), Set.empty)) ApplicativeThrow[F].unit
      else {
        val timeout = error match {
          case direct: TimeoutException => direct
          case _ =>
            val wrapped = new TimeoutException(s"wire operation timed out: $error")
            val _ = wrapped.initCause(error)
            wrapped
        }
        ApplicativeThrow[F].raiseError(timeout)
      }
    }
  }

  def create[F[_]: Async](socket: Socket[F], transcript: Transcript[F]): F[RawPeer[F]] =
    create(socket.read, socket.write, socket.endOfOutput, transcript)

  private[wire] def create[F[_]: Async](
      read: Int => F[Option[Chunk[Byte]]],
      write: Chunk[Byte] => F[Unit],
      endOfOutput: F[Unit],
      transcript: Transcript[F],
  ): F[RawPeer[F]] =
    for {
      buffered <- Ref.of[F, ByteVector](ByteVector.empty)
      eof <- Ref.of[F, Boolean](false)
      readLock <- Semaphore[F](1)
      writeLock <- Semaphore[F](1)
    } yield new RawPeer(read, write, endOfOutput, transcript, buffered, eof, readLock, writeLock)
}
