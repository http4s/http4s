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
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.SignallingRef
import fs2.io.net.Socket
import fs2.io.net.SocketOption
import org.http4s.Http4sSuite
import org.typelevel.log4cats.noop.NoOpFactory
import scodec.bits.ByteVector

import scala.concurrent.duration._

/** Regressions for receive-window credit under backpressure and cancellation.
  * A connection-owned sender keeps body and socket readers independent of a full
  * outgoing queue. Completed bodies retain their charge until consumed or discarded,
  * and idle detection waits for credit to reach the peer. Tiny DATA payloads must
  * not retain unrelated socket buffers.
  */
class H2FlowControlReviewSuite extends Http4sSuite {
  private val settings = H2Frame.Settings.ConnectionSettings.default

  private def connection(
      outgoing: Queue[IO, Chunk[H2Frame]],
      writeBytes: Chunk[Byte] => IO[Unit],
      connectionType: H2Connection.ConnectionType = H2Connection.ConnectionType.Server,
      input: ByteVector = ByteVector.empty,
      idleTimeout: Duration = Duration.Inf,
      readBytes: Int => IO[Option[Chunk[Byte]]] = _ => IO.never,
  ): IO[H2Connection[IO]] = {
    val addr = SocketAddress(ip"127.0.0.1", port"0")
    val socket = new Socket[IO] {
      def read(maxBytes: Int): IO[Option[Chunk[Byte]]] = readBytes(maxBytes)
      def endOfInput: IO[Unit] = IO.unit
      def endOfOutput: IO[Unit] = IO.unit
      def isOpen: IO[Boolean] = IO.pure(true)
      def localAddress: IO[SocketAddress[IpAddress]] = IO.pure(addr)
      def peerAddress: GenSocketAddress = addr
      def readN(numBytes: Int): IO[Chunk[Byte]] = IO.never
      def reads: Stream[IO, Byte] = Stream.never[IO]
      def remoteAddress: IO[SocketAddress[IpAddress]] = IO.pure(addr)
      def write(bytes: Chunk[Byte]): IO[Unit] = writeBytes(bytes)
      def writes: Pipe[IO, Byte, Nothing] = _.drain
      def address: GenSocketAddress = addr
      def getOption[A](key: SocketOption.Key[A]): IO[Option[A]] = IO.pure(None)
      def setOption[A](key: SocketOption.Key[A], value: A): IO[Unit] = IO.unit
      def supportedOptions: IO[Set[SocketOption.Key[_]]] = IO.pure(Set.empty)
    }
    for {
      streams <- Ref[IO].of(Map.empty[Int, H2Stream[IO]])
      state <- H2Connection
        .initState[IO](settings, settings.initialWindowSize, settings.initialWindowSize)
      pendingReadCredit <- SignallingRef[IO, Int](0)
      created <- Queue.unbounded[IO, Int]
      closed <- Queue.unbounded[IO, Int]
      hpack <- Hpack.create[IO](4096)
      lock <- Semaphore[IO](1)
      ack <- Deferred[IO, Either[Throwable, H2Frame.Settings.ConnectionSettings]]
      logger <- NoOpFactory[IO].fromClass(classOf[H2FlowControlReviewSuite])
    } yield new H2Connection[IO](
      addr,
      connectionType,
      Duration.Inf,
      idleTimeout,
      settings,
      streams,
      state,
      pendingReadCredit,
      outgoing,
      created,
      closed,
      hpack,
      lock.permit,
      ack,
      input,
      socket,
      logger,
    )
  }

  private def granted(bytes: ByteVector, id: Int = 0): Int =
    H2Frame.RawFrame.fromByteVector(bytes) match {
      case Some((raw, rest)) =>
        val increment = H2Frame.fromRaw(raw) match {
          case Right(H2Frame.WindowUpdate(stream, n)) if stream == id => n
          case _ => 0
        }
        increment + granted(rest, id)
      case None => 0
    }

  private def receiveBody(h2: H2Connection[IO], id: Int, bytes: Int): IO[H2Stream[IO]] =
    for {
      stream <- h2.initiateRemoteStreamById(id)
      _ <- stream.state.update(_.copy(state = H2Stream.StreamState.Open))
      // Model the connection debit made immediately before receiveData.
      _ <- h2.state.update(s =>
        s.copy(
          readWindow = s.readWindow - bytes,
          advertisedReadWindow = s.advertisedReadWindow - bytes,
        )
      )
      _ <- Stream
        .emits(Array.fill[Byte](bytes)(0))
        .covary[IO]
        .chunkN(16384)
        .evalMap(c => stream.receiveData(H2Frame.Data(id, c.toByteVector, None, false)))
        .compile
        .drain
    } yield stream

  List(H2Connection.ConnectionType.Server, H2Connection.ConnectionType.Client).foreach { role =>
    test(s"$role sends cancelled readers' connection grants after stream release") {
      val window: Int = settings.initialWindowSize.windowSize
      for {
        outgoing <- Queue.bounded[IO, Chunk[H2Frame]](128)
        writing <- Deferred[IO, Unit]
        resume <- Deferred[IO, Unit]
        written <- Ref[IO].of(ByteVector.empty)
        h2 <- connection(
          outgoing,
          bytes => writing.complete(()) >> resume.get >> written.update(_ ++ bytes.toByteVector),
          role,
        )
        _ <- h2.writeLoop.compile.drain.background.use { _ =>
          for {
            _ <- outgoing.offer(Chunk.singleton(H2Frame.Ping.ack))
            _ <- writing.get
            _ <- outgoing.offer(Chunk.singleton(H2Frame.Ping.ack)).replicateA_(128)
            streams <- Stream
              .emits(List((1, 32768), (3, 16383), (5, 16384)))
              .covary[IO]
              .evalMap { case (id, bytes) =>
                val streamId = role match {
                  case H2Connection.ConnectionType.Server => id
                  case H2Connection.ConnectionType.Client => id + 1
                }
                for {
                  stream <- receiveBody(h2, streamId, bytes)
                  // Stop reading an open body without waiting for END_STREAM.
                  _ <- stream.readBody.take(bytes.toLong).compile.drain.timeout(2.seconds)
                } yield stream
              }
              .compile
              .toList
            _ <- resume.complete(())
            _ <- Stream.emits(streams).evalMap(_.finish(H2Error.Cancel)).compile.drain
            _ <- written.get
              .iterateUntil(bytes => granted(bytes) == window)
              .void
              .timeoutTo(2.seconds, IO.unit)
            bytes <- written.get
          } yield assertEquals(
            granted(bytes),
            window,
            "The peer must receive all consumed credit after the streams are released",
          )
        }
      } yield ()
    }
  }

  test("discarded bodies return all connection credit after the writer resumes") {
    TestControl.executeEmbed {
      val window = settings.initialWindowSize.windowSize
      val batch = window / 2
      for {
        outgoing <- Queue.bounded[IO, Chunk[H2Frame]](1)
        writing <- Deferred[IO, Unit]
        resume <- Deferred[IO, Unit]
        written <- Ref[IO].of(ByteVector.empty)
        h2 <- connection(
          outgoing,
          bytes => writing.complete(()) >> resume.get >> written.update(_ ++ bytes.toByteVector),
        )
        _ <- h2.writeLoop.compile.drain.background.use { _ =>
          (for {
            _ <- outgoing.offer(Chunk.singleton(H2Frame.Ping.ack))
            _ <- writing.get
            _ <- outgoing.offer(Chunk.singleton(H2Frame.Ping.ack))
            first <- receiveBody(h2, 1, batch)
            second <- receiveBody(h2, 3, batch)
            _ <- first.receiveRstStream(H2Error.Cancel.toRst(1))
            // Let the first grant reach the blocked writer before releasing an equal batch.
            _ <- IO.sleep(1.second)
            _ <- second.receiveRstStream(H2Error.Cancel.toRst(3))
            _ <- resume.complete(())
            _ <- IO.sleep(1.second)
            bytes <- written.get
          } yield assertEquals(granted(bytes), 2 * batch))
            .guarantee(resume.complete(()).void)
        }
      } yield ()
    }
  }

  test("connection shutdown completes under outgoing backpressure") {
    TestControl.executeEmbed {
      (for {
        outgoing <- Queue.bounded[IO, Chunk[H2Frame]](1)
        writing <- Deferred[IO, Unit]
        h2 <- connection(outgoing, _ => writing.complete(()) >> IO.never)
        _ <- h2.writeLoop.compile.drain.background.use { _ =>
          for {
            _ <- outgoing.offer(Chunk.singleton(H2Frame.Ping.ack))
            _ <- writing.get
            _ <- outgoing.offer(Chunk.singleton(H2Frame.Ping.ack))
            stream <- receiveBody(h2, 1, 32768)
            _ <- stream.readBody.take(32768).compile.drain
            _ <- IO.sleep(1.second)
          } yield ()
        }
      } yield ()).timeout(2.seconds)
    }
  }

  test("reset must not replenish credit for a completed body that remains buffered") {
    TestControl.executeEmbed {
      val window: Int = settings.initialWindowSize.windowSize
      for {
        outgoing <- Queue.bounded[IO, Chunk[H2Frame]](128)
        written <- Ref[IO].of(ByteVector.empty)
        h2 <- connection(
          outgoing,
          bytes => written.update(_ ++ bytes.toByteVector),
          H2Connection.ConnectionType.Server,
        )
        _ <- h2.writeLoop.compile.drain.background.use { _ =>
          def completedBody(id: Int): IO[H2Stream[IO]] =
            for {
              stream <- receiveBody(h2, id, window)
              _ <- stream.receiveData(H2Frame.Data(id, ByteVector.empty, None, true))
              _ <- stream.receiveRstStream(H2Error.NoError.toRst(id))
            } yield stream

          for {
            first <- completedBody(1)
            // Let the connection's sender process all available credit.
            _ <- IO.sleep(1.second)
            firstWrites <- written.get
            // A conforming peer sends another window only if we advertised it.
            second <-
              if (granted(firstWrites) >= window) completedBody(3).map(Some(_))
              else IO.pure(Option.empty[H2Stream[IO]])
            _ <- IO.sleep(1.second)
            beforeRead <- written.get
            // Both bodies have been retained by the application without being read.
            retained <- Stream
              .emits(first :: second.toList)
              .evalMap(_.readBody.compile.count)
              .compile
              .fold(0L)(_ + _)
          } yield assert(
            retained <= window,
            s"Buffered $retained bytes with a $window-byte receive window; " +
              s"${granted(beforeRead)} bytes of new credit were sent before either body was read",
          )
        }
      } yield ()
    }
  }

  test("the credit sender grants stream credit below the connection batching threshold") {
    TestControl.executeEmbed {
      for {
        outgoing <- Queue.bounded[IO, Chunk[H2Frame]](128)
        written <- Ref[IO].of(ByteVector.empty)
        firstRead <- Deferred[IO, Unit]
        resumeRead <- Deferred[IO, Unit]
        h2 <- connection(
          outgoing,
          bytes => written.update(_ ++ bytes.toByteVector),
          H2Connection.ConnectionType.Server,
        )
        _ <- h2.writeLoop.compile.drain.background.use { _ =>
          for {
            first <- receiveBody(h2, 1, 32768)
            second <- receiveBody(h2, 3, 16384)
            _ <- first.readBody.chunks
              .take(2)
              .evalMap(_ => firstRead.complete(()).flatMap(resumeRead.get.whenA))
              .compile
              .drain
              .background
              .use { reader =>
                (for {
                  _ <- firstRead.get
                  _ <- second.readBody.take(16384).compile.drain
                  _ <- IO.sleep(1.second)
                  before <- written.get
                  _ = assertEquals(granted(before), 32768)
                  _ = assertEquals(granted(before, 1), 0)
                  _ <- resumeRead.complete(())
                  _ <- reader.flatMap(_.embedNever)
                  _ <- IO.sleep(1.second)
                  after <- written.get
                } yield {
                  assertEquals(granted(after), 32768)
                  assertEquals(granted(after, 1), 32768)
                }).guarantee(resumeRead.complete(()).void)
              }
          } yield ()
        }
      } yield ()
    }
  }

  List(0.millis, 700.millis).foreach { writeDelay =>
    test(s"idle timeout waits for receive credit to reach the peer (write delay=$writeDelay)") {
      TestControl.executeEmbed {
        val window: Int = settings.initialWindowSize.windowSize
        for {
          outgoing <- Queue.bounded[IO, Chunk[H2Frame]](128)
          written <- Ref[IO].of(ByteVector.empty)
          creditSent <- Deferred[IO, FiniteDuration]
          h2 <- connection(
            outgoing,
            bytes =>
              IO.sleep(writeDelay) >> written
                .modify { current =>
                  val next = current ++ bytes.toByteVector
                  (next, granted(next) > 0 && granted(next, 1) > 0)
                }
                .flatMap { delivered =>
                  IO.monotonic.flatMap(creditSent.complete).void.whenA(delivered)
                },
            H2Connection.ConnectionType.Server,
            idleTimeout = 1.second,
          )
          stream <- receiveBody(h2, 1, window)
          // The handler is streaming a response while the upload remains open.
          // Each write finishes before the write timeout, but the slow socket
          // delays when the receive-window updates reach the peer.
          _ <- outgoing
            .offer(Chunk.singleton(H2Frame.Data(1, ByteVector.fill(4096)(0), None, false)))
            .replicateA_(4)
          _ <- (h2.readLoop.background, h2.writeLoop.compile.drain.background).tupled.use {
            case (reader, _) =>
              for {
                _ <- IO.sleep(100.millis)
                _ <- stream.readBody.take(window.toLong).compile.drain
                _ <- IO.sleep(2100.millis)
                sentAt <- creditSent.get
                now <- IO.monotonic
                state <- h2.state.get
                _ = assertEquals(
                  state.closed,
                  now - sentAt >= 1.second,
                  "The peer cannot resume its upload until WINDOW_UPDATE is written",
                )
                _ <- reader.flatMap(_.embedNever).timeout(10.seconds)
                expiredAt <- IO.monotonic
              } yield assert(
                expiredAt - sentAt >= 1.second,
                "Idle timeout must allow a full interval after receive credit is sent",
              )
          }
        } yield ()
      }
    }
  }

  test("a window update that can not be written closes the connection") {
    TestControl.executeEmbed {
      for {
        outgoing <- Queue.bounded[IO, Chunk[H2Frame]](128)
        h2 <- connection(outgoing, _ => IO.never, idleTimeout = 1.second)
        stream <- receiveBody(h2, 1, 32768)
        start <- IO.monotonic
        _ <- h2.writeLoop.compile.drain.background.use { loop =>
          stream.readBody.take(32768L).compile.drain >>
            loop.flatMap(_.embedNever).timeout(2.seconds)
        }
        end <- IO.monotonic
        state <- h2.state.get
      } yield {
        assert(state.closed, "A stalled window update must close the connection")
        assertEquals(end - start, 1.second)
      }
    }
  }

  test("idle timeout resumes after credit is sent with an exhausted connection send window") {
    TestControl.executeEmbed {
      val window: Int = settings.initialWindowSize.windowSize
      for {
        outgoing <- Queue.bounded[IO, Chunk[H2Frame]](128)
        written <- Ref[IO].of(ByteVector.empty)
        h2 <- connection(
          outgoing,
          bytes => written.update(_ ++ bytes.toByteVector),
          H2Connection.ConnectionType.Server,
          idleTimeout = 1.second,
        )
        stream <- receiveBody(h2, 1, window)
        _ <- h2.state.update(_.copy(writeWindow = 0))
        _ <- (h2.readLoop.background, h2.writeLoop.compile.drain.background).tupled.use {
          case (reader, _) =>
            for {
              _ <- IO.sleep(100.millis)
              _ <- stream.readBody.take(window.toLong).compile.drain
              _ <- reader.flatMap(_.embedNever).timeout(5.seconds)
              bytes <- written.get
              state <- h2.state.get
            } yield {
              assert(granted(bytes) > 0, "Connection credit was not sent")
              assert(granted(bytes, 1) > 0, "Stream credit was not sent")
              assert(state.closed, "The peer is idle once both receive windows are replenished")
            }
        }
      } yield ()
    }
  }

  test("tiny buffered DATA must not retain unrelated socket payloads") {
    val count = 256
    val window: Int = settings.initialWindowSize.windowSize
    // Each socket read contains one body byte and valid, ignored extension frames.
    // Their payloads are not flow controlled and should not stay in the body buffer.
    val ignored = ByteVector.concat(List(16384, 16384, 16384, window - 46 - 3 * 16384).map { n =>
      H2Frame.RawFrame.toByteVector(
        H2Frame.RawFrame(n, 0x0b.toByte, 0.toByte, 0, ByteVector.fill(n.toLong)(0))
      )
    })
    for {
      outgoing <- Queue.bounded[IO, Chunk[H2Frame]](128)
      reads <- Ref[IO].of(0)
      parsed <- Deferred[IO, Unit]
      h2 <- connection(
        outgoing,
        _ => IO.unit,
        H2Connection.ConnectionType.Server,
        readBytes = maxBytes =>
          reads.getAndUpdate(_ + 1).flatMap { n =>
            if (n == count) parsed.complete(()) >> IO.never
            else
              IO {
                val data = H2Frame.Data(1, ByteVector(1.toByte), None, n == count - 1)
                val packet = (H2Frame.toByteVector(data) ++ ignored).toArray
                assertEquals(packet.length, maxBytes)
                Some(Chunk.array(packet))
              }
          },
      )
      stream <- h2.initiateRemoteStreamById(1)
      _ <- stream.state.update(_.copy(state = H2Stream.StreamState.Open))
      _ <- h2.readLoop.background.use { _ =>
        for {
          _ <- parsed.get
          connectionState <- h2.state.get
          streamState <- stream.state.get
          buffered <- streamState.readBuffer.stream.compile.toList
        } yield {
          val bodies = buffered.map(_.toOption.get)
          assertEquals(bodies.size, count)
          assertEquals(ByteVector.concat(bodies), ByteVector.fill(count.toLong)(1.toByte))
          assertEquals(connectionState.readWindow, window - count)
          assertEquals(streamState.unreadBytes, count)
          // Array equality is reference equality, so shared read buffers count once.
          val retained = bodies.map(_.toByteBufferUnsafe.array()).distinct.map(_.length.toLong).sum
          assert(
            retained <= window,
            s"$count unread body bytes retain $retained backing-array bytes " +
              s"despite a $window-byte receive window",
          )
        }
      }
    } yield ()
  }

  List(false, true).foreach { padded =>
    test(s"read loop processes WINDOW_UPDATE with a full outgoing queue (padded=$padded)") {
      TestControl.executeEmbed {
        val data = H2Frame.Data(
          1,
          ByteVector.empty,
          if (padded) Some(ByteVector.fill(127)(0)) else None,
          false,
        )
        val input = ByteVector.concat(List.fill(128)(H2Frame.toByteVector(data))) ++
          H2Frame.toByteVector(H2Frame.WindowUpdate(0, 1))
        for {
          outgoing <- Queue.bounded[IO, Chunk[H2Frame]](128)
          h2 <- connection(outgoing, _ => IO.unit, H2Connection.ConnectionType.Server, input)
          stream <- receiveBody(h2, 1, 16384)
          // The handler reads a prefix, then pauses with less than half a window consumed.
          _ <- stream.readBody.take(16384).compile.drain
          _ <- h2.state.update(_.copy(writeWindow = 0))
          _ <- outgoing.offer(Chunk.singleton(H2Frame.Ping.ack)).replicateA_(128)
          _ <- h2.readLoop.background.use { _ =>
            for {
              _ <- IO.sleep(1.second)
              before <- h2.state.get
              // Freeing one queue slot proves what prevented read-loop progress.
              _ <- outgoing.take
              _ <- IO.sleep(1.second)
              after <- h2.state.get
              _ = assertEquals(after.writeWindow, 1)
            } yield assertEquals(
              before.writeWindow,
              1,
              "Padding blocked the connection reader before it could process WINDOW_UPDATE",
            )
          }
        } yield ()
      }
    }
  }
}
