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
import cats.effect.std.Queue
import cats.effect.std.Semaphore
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import fs2.io.net.Socket
import fs2.io.net.SocketOption
import org.http4s.Http4sSuite
import org.typelevel.log4cats.noop.NoOpFactory
import scodec.bits.ByteVector

import scala.concurrent.duration.Duration

class H2FrameSizeSuite extends Http4sSuite {

  private val addr = SocketAddress(ip"127.0.0.1", port"0")

  private val localSettings = H2Frame.Settings.ConnectionSettings.default

  private val maxFrameSize = localSettings.maxFrameSize.frameSize // 16384

  /** How much one `socket.read` can hand back, so how much the read loop may
    * legitimately hold before it has seen a frame header at all.
    */
  private val oneRead = localSettings.initialWindowSize.windowSize.toLong // 65535

  private val dataFrameType: Byte = 0x0

  /** Replays `bytes` and records how many of them it handed over. */
  private def countingSocket(bytes: ByteVector): IO[(Socket[IO], IO[Long])] =
    (Ref[IO].of(bytes), Ref[IO].of(0L)).tupled.map { case (remaining, consumed) =>
      val socket = new Socket[IO] {
        def read(maxBytes: Int): IO[Option[Chunk[Byte]]] =
          remaining
            .modify { bv =>
              if (bv.isEmpty) (bv, None)
              else {
                val (h, t) = bv.splitAt(maxBytes.toLong)
                (t, Some(Chunk.byteVector(h)))
              }
            }
            .flatTap(c => consumed.update(_ + c.fold(0L)(_.size.toLong)))

        def endOfInput: IO[Unit] = IO.unit
        def endOfOutput: IO[Unit] = IO.unit
        def isOpen: IO[Boolean] = IO.pure(true)
        def localAddress: IO[SocketAddress[IpAddress]] = IO.pure(addr)
        def peerAddress: GenSocketAddress = addr
        def readN(numBytes: Int): IO[Chunk[Byte]] = ???
        def reads: Stream[IO, Byte] = ???
        def remoteAddress: IO[SocketAddress[IpAddress]] = IO.pure(addr)
        def write(bytes: Chunk[Byte]): IO[Unit] = IO.unit
        def writes: Pipe[IO, Byte, Nothing] = ???
        def address: GenSocketAddress = addr
        def getOption[A](key: SocketOption.Key[A]): IO[Option[A]] = IO.pure(None)
        def setOption[A](key: SocketOption.Key[A], value: A): IO[Unit] = IO.unit
        def supportedOptions: IO[Set[SocketOption.Key[_]]] = IO.pure(Set.empty)
      }
      (socket, consumed.get)
    }

  private def mkConnection(input: ByteVector): IO[(H2Connection[IO], IO[Long])] =
    for {
      t <- countingSocket(input)
      (socket, consumed) = t
      mapRef <- Ref[IO].of(Map.empty[Int, H2Stream[IO]])
      stateRef <- H2Connection.initState[IO](
        H2Frame.Settings.ConnectionSettings.default,
        H2Frame.Settings.ConnectionSettings.default.initialWindowSize,
        localSettings.initialWindowSize,
      )
      outgoing <- Queue.unbounded[IO, Chunk[H2Frame]]
      created <- Queue.unbounded[IO, Int]
      closed <- Queue.unbounded[IO, Int]
      hpack <- Hpack.create[IO](localSettings.maxHeaderListSize.fold(Int.MaxValue)(_.listSize))
      lock <- Semaphore[IO](1)
      ack <- Deferred[IO, Either[Throwable, H2Frame.Settings.ConnectionSettings]]
      logger <- NoOpFactory[IO].fromClass(classOf[H2FrameSizeSuite])
    } yield (
      new H2Connection[IO](
        addr,
        H2Connection.ConnectionType.Server,
        Duration.Inf,
        Duration.Inf,
        localSettings,
        mapRef,
        stateRef,
        outgoing,
        created,
        closed,
        hpack,
        lock.permit,
        ack,
        ByteVector.empty,
        socket,
        logger,
      ),
      consumed,
    )

  private def drainOutgoing(h2: H2Connection[IO]): IO[Vector[H2Frame]] =
    h2.outgoing.tryTake.flatMap {
      case Some(c) => drainOutgoing(h2).map(c.toVector ++ _)
      case None => IO.pure(Vector.empty)
    }

  private def rawFrame(frameType: Byte, declaredLength: Int, payload: ByteVector): ByteVector =
    H2Frame.RawFrame.toByteVector(
      H2Frame.RawFrame(declaredLength, frameType, 0x0, 1, ByteVector.empty)
    ) ++ payload

  private def dataFrame(declaredLength: Int, payload: ByteVector): ByteVector =
    rawFrame(dataFrameType, declaredLength, payload)

  private def firstGoAway(frames: Vector[H2Frame]): Option[H2Frame.GoAway] =
    frames.collectFirst { case g: H2Frame.GoAway => g }

  test("a frame declaring more than SETTINGS_MAX_FRAME_SIZE is rejected before its payload") {
    // Declares the 24-bit maximum, 1024x what we advertised, and supplies 1 MiB
    // of it. Without the length check the read loop drains all of it waiting for
    // a frame that would only then be rejected.
    val supplied = 1024L * 1024L
    val input = dataFrame(0xffffff, ByteVector.fill(supplied)(0))

    for {
      t <- mkConnection(input)
      (h2, consumed) = t
      _ <- h2.readLoop
      n <- consumed
      frames <- drainOutgoing(h2)
      goAway = firstGoAway(frames)
      _ = assert(goAway.nonEmpty, clue(frames))
      _ = assertEquals(
        goAway.get.errorCode.toInt,
        H2Error.FrameSizeError.value,
        clue(frames),
      )
      // The payload must not have been drained. One socket read is unavoidable,
      // since the header only becomes readable once some bytes have arrived.
      _ = assert(
        n <= oneRead,
        clue(s"buffered $n bytes, expected no more than one read of $oneRead"),
      )
      _ = assert(n < supplied, clue(s"buffered $n bytes of the $supplied byte payload"))
    } yield ()
  }

  test("a frame at exactly SETTINGS_MAX_FRAME_SIZE is not rejected for its size") {
    // Guards the boundary: the check must be `>` and not `>=`, or every frame at
    // the advertised limit would be refused. Stream 1 was never opened, so the
    // frame is rejected as a protocol error, which is only reachable if the size
    // gate let it through and it was parsed.
    val input = dataFrame(maxFrameSize, ByteVector.fill(maxFrameSize.toLong)(0))

    for {
      t <- mkConnection(input)
      (h2, _) = t
      _ <- h2.readLoop
      frames <- drainOutgoing(h2)
      goAway = firstGoAway(frames)
      _ = assert(goAway.nonEmpty, clue(frames))
      _ = assertEquals(
        goAway.get.errorCode.toInt,
        H2Error.ProtocolError.value,
        clue(s"expected the frame to parse and fail on stream state, got $frames"),
      )
    } yield ()
  }

  List(
    "DATA" -> dataFrameType,
    "HEADERS" -> (0x1: Byte),
    "PUSH_PROMISE" -> (0x5: Byte),
    "CONTINUATION" -> (0x9: Byte),
  ).foreach { case (name, frameType) =>
    test(s"an oversized $name frame is rejected even when it arrives complete") {
      val declared = maxFrameSize + 1
      val input = rawFrame(frameType, declared, ByteVector.fill(declared.toLong)(0))

      for {
        t <- mkConnection(input)
        (h2, _) = t
        _ <- h2.readLoop
        frames <- drainOutgoing(h2)
        goAway = firstGoAway(frames)
        _ = assert(goAway.nonEmpty, clue(frames))
        _ = assertEquals(
          goAway.get.errorCode.toInt,
          H2Error.FrameSizeError.value,
          clue(frames),
        )
      } yield ()
    }
  }
}
