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
import cats.effect.testkit.TestControl
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
import scala.concurrent.duration.DurationInt

class H2ConnectionSuite extends Http4sSuite {

  private val addr = SocketAddress(ip"127.0.0.1", port"0")

  private def stubSocket(bytes: ByteVector, recorded: Ref[IO, ByteVector]): IO[Socket[IO]] =
    Ref[IO].of(bytes).map { ref =>
      new Socket[IO] {
        def read(maxBytes: Int): IO[Option[Chunk[Byte]]] =
          ref.modify { bv =>
            if (bv.isEmpty) (bv, None)
            else {
              val (h, t) = bv.splitAt(maxBytes.toLong)
              (t, Some(Chunk.byteVector(h)))
            }
          }
        // Implement only as necessary...
        def endOfInput: IO[Unit] = ???
        def endOfOutput: IO[Unit] = ???
        def isOpen: IO[Boolean] = ???
        def localAddress: IO[SocketAddress[IpAddress]] = ???
        def peerAddress: GenSocketAddress = ???
        def readN(numBytes: Int): IO[Chunk[Byte]] = ???
        def reads: Stream[IO, Byte] = ???
        def remoteAddress: IO[SocketAddress[IpAddress]] = ???
        def write(bytes: Chunk[Byte]): IO[Unit] =
          recorded.update(_ ++ bytes.toByteVector)
        def writes: Pipe[IO, Byte, Nothing] = ???
        def address: GenSocketAddress = ???
        def getOption[A](key: SocketOption.Key[A]): IO[Option[A]] = ???
        def setOption[A](key: SocketOption.Key[A], value: A): IO[Unit] = ???
        def supportedOptions: IO[Set[SocketOption.Key[_]]] = ???
      }
    }

  private def mkConnection(
      localSettings: H2Frame.Settings.ConnectionSettings,
      input: ByteVector,
  ): IO[H2Connection[IO]] =
    Ref[IO].of(ByteVector.empty).flatMap(mkConnection(localSettings, input, Duration.Inf, _))

  private def mkConnection(
      localSettings: H2Frame.Settings.ConnectionSettings,
      input: ByteVector,
      idleTimeout: Duration,
      writes: Ref[IO, ByteVector],
  ): IO[H2Connection[IO]] =
    for {
      socket <- stubSocket(input, writes)
      mapRef <- Ref[IO].of(Map.empty[Int, H2Stream[IO]])
      stateRef <- H2Connection.initState[IO](
        H2Frame.Settings.ConnectionSettings.default,
        H2Frame.Settings.ConnectionSettings.default.initialWindowSize,
        localSettings.initialWindowSize,
      )
      outgoing <- Queue.unbounded[IO, Chunk[H2Frame]]
      created <- Queue.unbounded[IO, Int]
      closed <- Queue.unbounded[IO, Int]
      hpack <- Hpack.create[IO](
        localSettings.maxHeaderListSize.fold(Int.MaxValue)(_.listSize)
      )
      lock <- Semaphore[IO](1)
      ack <- Deferred[IO, Either[Throwable, H2Frame.Settings.ConnectionSettings]]
      logger <- NoOpFactory[IO].fromClass(classOf[H2ConnectionSuite])
    } yield new H2Connection[IO](
      addr,
      H2Connection.ConnectionType.Server,
      Duration.Inf,
      idleTimeout,
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
    )

  private def decodeFrames(bv: ByteVector): Vector[H2Frame] = {
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

  private def dataFrame(size: Int): Chunk[H2Frame] =
    Chunk.singleton(H2Frame.Data(1, ByteVector.fill(size.toLong)(0), None, endStream = false))

  private def increaseWindowSize(h2: H2Connection[IO], size: Int): IO[Unit] =
    Deferred[IO, Either[Throwable, Unit]].flatMap { next =>
      h2.state
        .modify(s => (s.copy(writeBlock = next, writeWindow = s.writeWindow + size), s.writeBlock))
        .flatMap(_.complete(Right(())).void)
    }

  private def drainOutgoing(h2: H2Connection[IO]): IO[Vector[H2Frame]] =
    h2.outgoing.tryTake.flatMap {
      case Some(c) => drainOutgoing(h2).map(c.toVector ++ _)
      case None => IO.pure(Vector.empty)
    }

  private def settingsWithMaxHeaderListSize(
      maxHeaderListSize: Int
  ): H2Frame.Settings.ConnectionSettings =
    H2Frame.Settings.ConnectionSettings.default
      .copy(maxHeaderListSize = Some(H2Frame.Settings.SettingsMaxHeaderListSize(maxHeaderListSize)))

  test("data for a stream that has already been answered is ignored") {
    val data = H2Frame.Data(1, ByteVector.empty, None, endStream = true)
    for {
      h2 <- mkConnection(
        H2Frame.Settings.ConnectionSettings.default,
        H2Frame.toByteVector(data),
      )
      _ <- h2.initiateRemoteStreamById(1)
      _ <- h2.mapRef.set(Map.empty)
      _ <- h2.readLoop
      frames <- drainOutgoing(h2)
      _ = assert(
        !frames.exists(_.isInstanceOf[H2Frame.GoAway]),
        clue(
          s"a peer that sends END_STREAM after we have answered and dropped the " +
            s"stream must not take the whole connection down, got $frames"
        ),
      )
    } yield ()
  }

  test("continunation frames within maxHeaderListSize accumulate without GoAway") {
    val headers =
      H2Frame.Headers(1, None, endStream = false, endHeaders = false, ByteVector.fill(40)(0), None)
    val cont = H2Frame.Continuation(1, endHeaders = false, ByteVector.fill(40)(0))
    val input = H2Frame.toByteVector(headers) ++ H2Frame.toByteVector(cont)
    for {
      // input is 40+40, max is 100 ... it fits
      h2 <- mkConnection(settingsWithMaxHeaderListSize(100), input)
      _ <- h2.readLoop
      frames <- drainOutgoing(h2)
      _ = assert(!frames.exists(_.isInstanceOf[H2Frame.GoAway]), clue(frames))
      st <- h2.state.get
      _ = assertEquals(st.headersInProgress.map(_.size), Some(80L))
    } yield ()
  }

  test("continuation frames exceeding maxHeaderListSize trigger GoAway(EnhanceYourCalm)") {
    val headers =
      H2Frame.Headers(1, None, endStream = false, endHeaders = false, ByteVector.fill(60)(0), None)
    val cont = H2Frame.Continuation(1, endHeaders = false, ByteVector.fill(60)(0))
    val input = H2Frame.toByteVector(headers) ++ H2Frame.toByteVector(cont)
    for {
      h2 <- mkConnection(settingsWithMaxHeaderListSize(100), input)
      _ <- h2.readLoop
      frames <- drainOutgoing(h2)
      goAways = frames.collectFirst { case g: H2Frame.GoAway => g }
      _ = assert(goAways.nonEmpty, clue(frames))
      _ = assertEquals(goAways.get.errorCode.toInt, H2Error.EnhanceYourCalm.value)
      closed <- h2.state.get.map(_.closed)
      _ = assert(closed)
    } yield ()
  }

  test("terminal continuation frame exceeding maxHeaderListSize triggers GoAway(EnhanceYourCalm)") {
    // small HEADERS (endHeaders=false), then a large terminal CONTINUATION (endHeaders=true)
    val headers =
      H2Frame.Headers(1, None, endStream = false, endHeaders = false, ByteVector.fill(10)(0), None)
    val cont = H2Frame.Continuation(1, endHeaders = true, ByteVector.fill(200)(0))
    val input = H2Frame.toByteVector(headers) ++ H2Frame.toByteVector(cont)
    for {
      // 10 + 200 = 210 > 100
      h2 <- mkConnection(settingsWithMaxHeaderListSize(100), input)
      _ <- h2.readLoop
      frames <- drainOutgoing(h2)
      goAway = frames.collectFirst { case g: H2Frame.GoAway => g }
      _ = assert(goAway.nonEmpty, clue(frames))
      _ = assertEquals(goAway.get.errorCode.toInt, H2Error.EnhanceYourCalm.value)
      closed <- h2.state.get.map(_.closed)
      _ = assert(closed)
    } yield ()
  }

  test("terminal continuation frame within maxHeaderListSize does not GoAway on size") {
    val headers =
      H2Frame.Headers(1, None, endStream = false, endHeaders = false, ByteVector.fill(10)(0), None)
    val cont = H2Frame.Continuation(1, endHeaders = true, ByteVector.fill(30)(0))
    val input = H2Frame.toByteVector(headers) ++ H2Frame.toByteVector(cont)
    for {
      // 10 + 30 <= 100; will fail HPACK decode but must not GoAway(EnhanceYourCalm)
      h2 <- mkConnection(settingsWithMaxHeaderListSize(100), input)
      _ <- h2.readLoop.attempt
      frames <- drainOutgoing(h2)
      _ = assert(
        !frames
          .collect { case g: H2Frame.GoAway => g }
          .exists(_.errorCode.toInt == H2Error.EnhanceYourCalm.value),
        clue(frames),
      )
    } yield ()
  }

  test("connection write stall past idleTimeout emits GoAway and closes") {
    val idle = 1.second
    TestControl.executeEmbed(
      for {
        writes <- Ref[IO].of(ByteVector.empty)
        h2 <- mkConnection(
          H2Frame.Settings.ConnectionSettings.default,
          ByteVector.empty,
          idle,
          writes,
        )
        _ <- h2.state.update(_.copy(writeWindow = 0))
        loop <- h2.writeLoop.compile.drain.start
        _ <- h2.outgoing.offer(dataFrame(16))
        _ <- loop.join
        st <- h2.state.get
        out <- writes.get
        frames = decodeFrames(out)
      } yield {
        assert(st.closed, clue(st.closed))
        assertEquals(
          frames.collectFirst { case g: H2Frame.GoAway => g.errorCode.toInt },
          Some(H2Error.ProtocolError.value),
          clue(frames),
        )
      }
    )
  }

  test("connection write stall resolved by a window update does not GoAway") {
    val idle = 1.second
    TestControl.executeEmbed(
      for {
        writes <- Ref[IO].of(ByteVector.empty)
        h2 <- mkConnection(
          H2Frame.Settings.ConnectionSettings.default,
          ByteVector.empty,
          idle,
          writes,
        )
        _ <- h2.state.update(_.copy(writeWindow = 0))
        loop <- h2.writeLoop.compile.drain.start
        _ <- h2.outgoing.offer(dataFrame(16))
        _ <- IO.sleep(idle / 2)
        _ <- increaseWindowSize(h2, 1 << 20)
        _ <- IO.sleep(idle)
        st <- h2.state.get
        _ <- loop.cancel
        out <- writes.get
        frames = decodeFrames(out)
      } yield {
        assert(!st.closed, clue(st.closed))
        assert(!frames.exists(_.isInstanceOf[H2Frame.GoAway]), clue(frames))
        assertEquals(frames.collect { case d: H2Frame.Data => d.data.size }, Vector(16L))
      }
    )
  }

  test("idle time between writes does not consume the stall budget") {
    val idle = 1.second
    TestControl.executeEmbed(
      for {
        writes <- Ref[IO].of(ByteVector.empty)
        h2 <- mkConnection(
          H2Frame.Settings.ConnectionSettings.default,
          ByteVector.empty,
          idle,
          writes,
        )
        loop <- h2.writeLoop.compile.drain.start
        _ <- h2.outgoing.offer(dataFrame(16))
        _ <- IO.sleep(idle * 10)
        _ <- h2.state.update(_.copy(writeWindow = 0))
        _ <- h2.outgoing.offer(dataFrame(16))
        _ <- IO.sleep(idle / 2)
        midway <- h2.state.get
        _ <- increaseWindowSize(h2, 1 << 20)
        _ <- IO.sleep(idle / 2)
        st <- h2.state.get
        _ <- loop.cancel
        out <- writes.get
        frames = decodeFrames(out)
      } yield {
        assert(!midway.closed, "connection closed before its stall budget elapsed")
        assert(!st.closed, clue(st.closed))
        assert(!frames.exists(_.isInstanceOf[H2Frame.GoAway]), clue(frames))
      }
    )
  }

  test("a control frame does not trigger a stall") {
    val idle = 1.second
    TestControl.executeEmbed(
      for {
        writes <- Ref[IO].of(ByteVector.empty)
        h2 <- mkConnection(
          H2Frame.Settings.ConnectionSettings.default,
          ByteVector.empty,
          idle,
          writes,
        )
        _ <- h2.state.update(_.copy(writeWindow = 0))
        loop <- h2.writeLoop.compile.drain.start
        _ <- h2.outgoing.offer(Chunk.singleton(H2Frame.Ping.ack))
        _ <- IO.sleep(idle * 10)
        _ <- loop.cancel
        st <- h2.state.get
        out <- writes.get
        frames = decodeFrames(out)
      } yield {
        assert(frames.exists(_.isInstanceOf[H2Frame.Ping]), clue(frames))
        assert(!st.closed, clue(st.closed))
      }
    )
  }
}
