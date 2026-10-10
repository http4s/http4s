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

import cats.data.NonEmptyList
import cats.effect._
import cats.effect.std.Queue
import cats.effect.std.Semaphore
import cats.effect.testkit.TestControl
import com.comcast.ip4s._
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.SignallingRef
import fs2.io.net.Socket
import fs2.io.net.SocketOption
import org.typelevel.log4cats.noop.NoOpFactory
import scodec.bits.ByteVector

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.TimeoutException

/** Covers connection frame processing, protocol limits, and write stalls.
  * Unread DATA must not block WINDOW_UPDATE processing. Late frames for closed
  * streams must preserve connection credit and HPACK state without reopening the
  * stream or terminating the connection.
  */
class H2ConnectionSuite extends H2Suite {

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
      writes: Ref[IO, ByteVector],
  ): IO[H2Connection[IO]] =
    mkConnection(localSettings, input, Duration.Inf, writes)

  private def mkConnection(
      localSettings: H2Frame.Settings.ConnectionSettings,
      input: ByteVector,
      idleTimeout: Duration,
      writes: Ref[IO, ByteVector],
      connectionType: H2Connection.ConnectionType = H2Connection.ConnectionType.Server,
  ): IO[H2Connection[IO]] =
    for {
      socket <- stubSocket(input, writes)
      mapRef <- Ref[IO].of(Map.empty[Int, H2Stream[IO]])
      stateRef <- H2Connection.initState[IO](
        H2Frame.Settings.ConnectionSettings.default,
        localSettings.initialWindowSize,
      )
      pendingReadCredit <- SignallingRef[IO, Int](0)
      writeWindow <- H2Connection.WriteWindow.init[IO](
        H2Frame.Settings.ConnectionSettings.default.initialWindowSize
      )
      outgoing <- Queue.unbounded[IO, H2Frame]
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
      connectionType,
      Duration.Inf,
      idleTimeout,
      localSettings,
      mapRef,
      stateRef,
      pendingReadCredit,
      writeWindow,
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

  private def dataFrame(size: Int): H2Frame =
    H2Frame.Data(1, ByteVector.fill(size.toLong)(0), None, endStream = false)

  private def settingsWithMaxHeaderListSize(
      maxHeaderListSize: Int
  ): H2Frame.Settings.ConnectionSettings =
    H2Frame.Settings.ConnectionSettings.default
      .copy(maxHeaderListSize = Some(H2Frame.Settings.SettingsMaxHeaderListSize(maxHeaderListSize)))

  test("unread DATA does not block a subsequent connection WINDOW_UPDATE") {
    val data = H2Frame.toByteVector(H2Frame.Data(1, ByteVector(1.toByte), None, endStream = false))
    val input = ByteVector.concat(List.fill(200)(data)) ++
      H2Frame.toByteVector(H2Frame.WindowUpdate(0, 1))
    for {
      h2 <- mkConnection(H2Frame.Settings.ConnectionSettings.default, input)
      stream <- h2.initiateRemoteStreamById(1)
      _ <- stream.state.update(_.copy(state = H2Stream.StreamState.Open))
      _ <- clearWriteWindow(h2)
      _ <- h2.readLoop.timeout(2.seconds)
      available <- h2.writeWindow.available
      streamState <- stream.state.get
    } yield {
      assertEquals(available, 1)
      assertEquals(streamState.unreadBytes, 200)
    }
  }

  test("data exceeding the connection window is a flow control error") {
    val window = H2Frame.Settings.ConnectionSettings.default.initialWindowSize.windowSize
    val settings = H2Frame.Settings.ConnectionSettings.default
      .copy(maxFrameSize = H2Frame.Settings.SettingsMaxFrameSize(window))
    val input =
      H2Frame.toByteVector(H2Frame.Data(1, ByteVector.fill(window.toLong)(0), None, false)) ++
        H2Frame.toByteVector(H2Frame.Data(1, ByteVector.fill(1)(0), None, false))
    for {
      writes <- Ref[IO].of(ByteVector.empty)
      h2 <- mkConnection(settings, input, writes)
      stream <- h2.initiateRemoteStreamById(1)
      _ <- stream.state.update(_.copy(state = H2Stream.StreamState.Open))
      _ <- h2.readLoop
      frames <- drainOutgoing(h2, writes)
    } yield assertEquals(
      frames.collectFirst { case g: H2Frame.GoAway => g.errorCode.toInt },
      Some(H2Error.FlowControlError.value),
      clue(frames),
    )
  }

  test("window update for an answered stream is ignored") {
    val w = H2Frame.WindowUpdate(1, 10)
    for {
      writes <- Ref[IO].of(ByteVector.empty)
      h2 <- mkConnection(
        H2Frame.Settings.ConnectionSettings.default,
        H2Frame.toByteVector(w),
        writes,
      )
      _ <- h2.initiateRemoteStreamById(1)
      _ <- h2.mapRef.set(Map.empty)
      _ <- h2.readLoop
      frames <- drainOutgoing(h2, writes)
    } yield assert(!frames.exists(_.isInstanceOf[H2Frame.GoAway]), clue(frames))
  }

  test("HEADERS for stream 0 terminates the connection") {
    for {
      hpack <- Hpack.create[IO](4096)
      request <- hpack.encodeHeaders(
        NonEmptyList.of(
          (":method", "GET", false),
          (":scheme", "http", false),
          (":path", "/", false),
          (":authority", "localhost", false),
          ("x-checksum", "abc", false),
        )
      )
      input = H2Frame.toByteVector(H2Frame.Headers(0, None, true, true, request, None))
      writes <- Ref[IO].of(ByteVector.empty)
      h2 <- mkConnection(H2Frame.Settings.ConnectionSettings.default, input, writes)
      _ <- h2.readLoop
      frames <- drainOutgoing(h2, writes)
    } yield assertEquals(
      frames.collectFirst { case g: H2Frame.GoAway => g.errorCode.toInt },
      Some(H2Error.ProtocolError.value),
      clue(frames),
    )
  }

  test("rst for a stream that has already been answered is ignored") {
    val rst = H2Frame.RstStream(1, H2Error.Cancel.value)
    for {
      writes <- Ref[IO].of(ByteVector.empty)
      h2 <- mkConnection(
        H2Frame.Settings.ConnectionSettings.default,
        H2Frame.toByteVector(rst),
        writes,
      )
      _ <- h2.initiateRemoteStreamById(1)
      _ <- h2.mapRef.set(Map.empty)
      _ <- h2.readLoop
      frames <- drainOutgoing(h2, writes)
    } yield assert(!frames.exists(_.isInstanceOf[H2Frame.GoAway]), clue(frames))
  }

  test("rst for an idle stream is a protocol error") {
    val rst = H2Frame.RstStream(3, H2Error.Cancel.value)
    for {
      writes <- Ref[IO].of(ByteVector.empty)
      h2 <- mkConnection(
        H2Frame.Settings.ConnectionSettings.default,
        H2Frame.toByteVector(rst),
        writes,
      )
      _ <- h2.readLoop
      frames <- drainOutgoing(h2, writes)
    } yield assertEquals(
      frames.collectFirst { case g: H2Frame.GoAway => g.errorCode.toInt },
      Some(H2Error.ProtocolError.value),
      clue(frames),
    )
  }

  test("continunation frames within maxHeaderListSize accumulate without GoAway") {
    val headers =
      H2Frame.Headers(1, None, endStream = false, endHeaders = false, ByteVector.fill(40)(0), None)
    val cont = H2Frame.Continuation(1, endHeaders = false, ByteVector.fill(40)(0))
    val input = H2Frame.toByteVector(headers) ++ H2Frame.toByteVector(cont)
    for {
      writes <- Ref[IO].of(ByteVector.empty)
      // input is 40+40, max is 100 ... it fits
      h2 <- mkConnection(settingsWithMaxHeaderListSize(100), input, writes)
      _ <- h2.readLoop
      frames <- drainOutgoing(h2, writes)
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
      writes <- Ref[IO].of(ByteVector.empty)
      h2 <- mkConnection(settingsWithMaxHeaderListSize(100), input, writes)
      _ <- h2.readLoop
      frames <- drainOutgoing(h2, writes)
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
      writes <- Ref[IO].of(ByteVector.empty)
      // 10 + 200 = 210 > 100
      h2 <- mkConnection(settingsWithMaxHeaderListSize(100), input, writes)
      _ <- h2.readLoop
      frames <- drainOutgoing(h2, writes)
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
      writes <- Ref[IO].of(ByteVector.empty)
      // 10 + 30 <= 100; will fail HPACK decode but must not GoAway(EnhanceYourCalm)
      h2 <- mkConnection(settingsWithMaxHeaderListSize(100), input, writes)
      _ <- h2.readLoop.attempt
      frames <- drainOutgoing(h2, writes)
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
        _ <- clearWriteWindow(h2)
        _ <- h2.writeLoop.compile.drain.start
        _ <- h2.offerFrame(dataFrame(16)).attempt
        st <- (IO.sleep(idle) >> h2.state.get).iterateUntil(_.closed)
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
        _ <- clearWriteWindow(h2)
        loop <- h2.writeLoop.compile.drain.start
        _ <- h2.offerFrame(dataFrame(16)).start
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
        _ <- h2.offerFrame(dataFrame(16))
        _ <- IO.sleep(idle * 10)
        _ <- clearWriteWindow(h2)
        _ <- h2.offerFrame(dataFrame(16)).start
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
        _ <- clearWriteWindow(h2)
        loop <- h2.writeLoop.compile.drain.start
        _ <- h2.offerFrame(H2Frame.Ping.ack)
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
  private val peerBody: ByteVector = {
    val data = H2Frame.Data(1, ByteVector.fill(16384)(0), None, endStream = false)
    H2Frame.toByteVector(data) ++ H2Frame.toByteVector(data)
  }

  private def openStream(h2: H2Connection[IO]): IO[H2Stream[IO]] =
    for {
      stream <- h2.initiateRemoteStreamById(1)
      _ <- stream.state.update(_.copy(state = H2Stream.StreamState.Open))
    } yield stream

  private def grantsIn(frames: Vector[H2Frame]): Vector[(Int, Int)] =
    frames.collect { case H2Frame.WindowUpdate(id, increment) => (id, increment) }.sorted

  test("window updates are written while DATA waits for the peer to grant more") {
    TestControl.executeEmbed(
      for {
        writes <- Ref[IO].of(ByteVector.empty)
        h2 <- mkConnection(
          H2Frame.Settings.ConnectionSettings.default,
          peerBody,
          Duration.Inf,
          writes,
        )
        stream <- openStream(h2)
        _ <- clearWriteWindow(h2)
        loop <- h2.writeLoop.compile.drain.start
        _ <- h2.offerFrame(dataFrame(16)).start
        _ <- h2.readLoop
        _ <- stream.readBody.take(32768).compile.drain
        _ <- IO.sleep(1.second)
        _ <- loop.cancel
        out <- writes.get
        frames = decodeFrames(out)
      } yield {
        assertEquals(grantsIn(frames), Vector((0, 32768), (1, 32768)), clue(frames))
        assert(!frames.exists(_.isInstanceOf[H2Frame.Data]), clue(frames))
      }
    )
  }

  test("window updates are written while the write loop is idle") {
    TestControl.executeEmbed(
      for {
        writes <- Ref[IO].of(ByteVector.empty)
        h2 <- mkConnection(
          H2Frame.Settings.ConnectionSettings.default,
          peerBody,
          Duration.Inf,
          writes,
        )
        stream <- openStream(h2)
        loop <- h2.writeLoop.compile.drain.start
        _ <- h2.readLoop
        _ <- stream.readBody.take(32768).compile.drain
        _ <- IO.sleep(1.second)
        _ <- loop.cancel
        out <- writes.get
      } yield assertEquals(grantsIn(decodeFrames(out)), Vector((0, 32768), (1, 32768)))
    )
  }

  test("window updates written during a write stall don't extend it") {
    val idle = 1.second
    TestControl.executeEmbed(
      for {
        writes <- Ref[IO].of(ByteVector.empty)
        h2 <- mkConnection(
          H2Frame.Settings.ConnectionSettings.default,
          peerBody,
          idle,
          writes,
        )
        stream <- openStream(h2)
        _ <- clearWriteWindow(h2)
        start <- IO.monotonic
        _ <- h2.writeLoop.compile.drain.start
        send <- h2.offerFrame(dataFrame(16)).start
        _ <- IO.sleep(idle / 2)
        _ <- h2.readLoop
        _ <- stream.readBody.take(32768).compile.drain
        _ <- send.join
        end <- IO.monotonic
        st <- h2.state.get
        out <- writes.get
        frames = decodeFrames(out)
      } yield {
        assertEquals(grantsIn(frames), Vector((0, 32768), (1, 32768)), clue(frames))
        assertEquals(end - start, idle)
        assert(st.closed, clue(st.closed))
        assertEquals(
          frames.collectFirst { case g: H2Frame.GoAway => g.errorCode.toInt },
          Some(H2Error.ProtocolError.value),
          clue(frames),
        )
      }
    )
  }

  test("WriteWindow has the initialized amount available") {
    TestControl.executeEmbed(
      H2Connection.WriteWindow
        .init[IO](H2Frame.Settings.SettingsInitialWindowSize(5))
        .flatMap(_.available)
        .map(assertEquals(_, 5))
    )
  }

  test("WriteWindow has the initialized amount available") {
    TestControl.executeEmbed(
      H2Connection.WriteWindow
        .init[IO](H2Frame.Settings.SettingsInitialWindowSize(5))
        .flatMap(_.available)
        .map(assertEquals(_, 5))
    )
  }

  test("WriteWindow has bytes available after taking some") {
    TestControl.executeEmbed(
      H2Connection.WriteWindow
        .init[IO](H2Frame.Settings.SettingsInitialWindowSize(5))
        .flatMap(window => window.take(3) >> window.available)
        .map(assertEquals(_, 2))
    )
  }

  test("WriteWindow has 0 bytes available after taking more than available") {
    TestControl.executeEmbed(
      H2Connection.WriteWindow
        .init[IO](H2Frame.Settings.SettingsInitialWindowSize(5))
        .flatMap(window => window.take(300) >> window.available)
        .map(assertEquals(_, 0))
    )
  }

  test("WriteWindow.takes only returns what is available") {
    TestControl.executeEmbed(
      H2Connection.WriteWindow
        .init[IO](H2Frame.Settings.SettingsInitialWindowSize(5))
        .flatMap(window => window.take(300))
        .map(assertEquals(_, 5))
    )
  }

  test("WriteWindow.take blocks when no bytes are available") {
    TestControl.executeEmbed(
      H2Connection.WriteWindow
        .init[IO](H2Frame.Settings.SettingsInitialWindowSize(5))
        .flatMap(window =>
          window.take(300) >> interceptIO[TimeoutException](window.take(300).timeout(100.millis))
        )
    )
  }

  test("WriteWindow.take unblocks when bytes become available") {
    TestControl.executeEmbed(
      for {
        window <- H2Connection.WriteWindow
          .init[IO](H2Frame.Settings.SettingsInitialWindowSize(5))
        _ <- window.take(300)
        blocked <- window.take(300).start
        _ <- window.change(200)
        remainingBytes <- blocked.join.flatMap(_.embedError)
      } yield assertEquals(remainingBytes, 200)
    )
  }
}
