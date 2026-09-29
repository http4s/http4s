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
import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Outcome
import cats.effect.Ref
import cats.effect.std.Queue
import cats.effect.std.Semaphore
import cats.effect.testkit.TestControl
import cats.syntax.all._
import fs2.Chunk
import fs2.Stream
import fs2.concurrent.Channel
import fs2.text.utf8
import org.http4s.Headers
import org.http4s.Http4sSuite
import org.http4s.HttpVersion
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.typelevel.log4cats
import scodec.bits.ByteVector

import java.util.concurrent.CancellationException
import scala.concurrent.duration.DurationLong

/** Covers stream framing, receive-window accounting, and body cleanup.
  * Consuming or discarding a body returns its credit exactly once. Padding counts
  * against the window, while empty DATA is not buffered. Finish and reset paths
  * wake blocked readers. Resets preserve already completed bodies until they are
  * consumed or released.
  */
class H2StreamSuite extends Http4sSuite {
  val defaultSettings = H2Frame.Settings.ConnectionSettings.default

  private def streamAndQueue(
      config: H2Frame.Settings.ConnectionSettings,
      creditConnection: Int => IO[Unit] = _ => IO.unit,
      outgoingQueue: IO[Queue[IO, Chunk[H2Frame]]] = Queue.unbounded[IO, Chunk[H2Frame]],
  ): IO[(H2Stream[IO], Queue[IO, Chunk[H2Frame]])] =
    for {
      writeBlock <- Deferred[IO, Either[Throwable, Unit]]
      req <- Deferred[IO, Either[Throwable, Request[fs2.Pure]]]
      resp <- Deferred[IO, Either[Throwable, Response[fs2.Pure]]]
      trailers <- Deferred[IO, Either[Throwable, Headers]]
      readBuffer <- Channel.unbounded[IO, Either[Throwable, ByteVector]]
      bodyDone <- Deferred[IO, Either[Throwable, Unit]]
      readBufferLock <- Semaphore[IO](1)

      state <- Ref[IO].of(
        H2Stream.State[IO](
          state = H2Stream.StreamState.Open,
          writeWindow = defaultSettings.initialWindowSize.windowSize,
          writeBlock = writeBlock,
          readWindow = config.initialWindowSize.windowSize,
          request = req,
          response = resp,
          trailers = trailers,
          readBuffer = readBuffer,
          contentLengthCheck = None,
          stallStart = None,
          unreadBytes = 0,
          advertisedReadWindow = config.initialWindowSize.windowSize.toLong,
          remoteReset = false,
        )
      )
      hpack <- Hpack.create[IO](1024)
      logger <- log4cats.noop.NoOpFactory[IO].fromClass(classOf[H2StreamSuite])
      outgoing <- outgoingQueue
      stream = new H2Stream[IO](
        1,
        60.seconds,
        defaultSettings,
        H2Connection.ConnectionType.Server,
        IO.pure(config),
        state,
        bodyDone,
        readBufferLock,
        hpack,
        outgoing,
        IO.unit,
        _ => IO.unit,
        creditConnection,
        logger,
      )
    } yield (stream, outgoing)

  def clientStream(
      config: H2Frame.Settings.ConnectionSettings
  ): IO[H2Stream[IO]] =
    for {
      writeBlock <- Deferred[IO, Either[Throwable, Unit]]
      req <- Deferred[IO, Either[Throwable, Request[fs2.Pure]]]
      resp <- Deferred[IO, Either[Throwable, Response[fs2.Pure]]]
      trailers <- Deferred[IO, Either[Throwable, Headers]]
      readBuffer <- Channel.unbounded[IO, Either[Throwable, ByteVector]]
      bodyDone <- Deferred[IO, Either[Throwable, Unit]]
      readBufferLock <- Semaphore[IO](1)

      state <- Ref[IO].of(
        H2Stream.State[IO](
          state = H2Stream.StreamState.Idle,
          writeWindow = defaultSettings.initialWindowSize.windowSize,
          writeBlock = writeBlock,
          readWindow = config.initialWindowSize.windowSize,
          request = req,
          response = resp,
          trailers = trailers,
          readBuffer = readBuffer,
          contentLengthCheck = None,
          stallStart = None,
          unreadBytes = 0,
          advertisedReadWindow = config.initialWindowSize.windowSize.toLong,
          remoteReset = false,
        )
      )
      hpack <- Hpack.create[IO](1024)
      logger <- log4cats.noop.NoOpFactory[IO].fromClass(classOf[H2StreamSuite])
      enqueue <- Queue.unbounded[IO, Chunk[H2Frame]]
      stream = new H2Stream[IO](
        1,
        60.seconds,
        defaultSettings,
        H2Connection.ConnectionType.Client,
        IO.pure(config),
        state,
        bodyDone,
        readBufferLock,
        hpack,
        enqueue,
        IO.unit,
        _ => IO.unit,
        _ => IO.unit,
        logger,
      )
    } yield stream

  private def emptyData(endStream: Boolean = false): H2Frame.Data =
    H2Frame.Data(1, ByteVector.empty, None, endStream)

  private def rstCodes(outgoing: Queue[IO, Chunk[H2Frame]]): IO[List[Int]] =
    outgoing
      .tryTakeN(None)
      .map(_.flatMap(_.toList).collect { case H2Frame.RstStream(_, code) =>
        code.toInt
      })

  private def testMessageSize(
      stream: H2Stream[IO],
      outgoing: Queue[IO, Chunk[H2Frame]],
      frameSize: Int,
      messageSize: Int,
      numFrames: Int,
  ) = {
    val sample = Response[IO](Status.Ok, HttpVersion.`HTTP/2`)
      .withEntity("0" * messageSize)

    for {
      _ <- stream.sendMessageBody(sample)
      chunks <- outgoing.take.replicateA(numFrames).map(_.flatMap(_.toList))
      data = chunks.collect { case H2Frame.Data(_, data, _, _) => data }
      _ <- assertIO(IO(data.size), numFrames)
      _ <- assertIO(IO(data.map(_.size).sum), messageSize.toLong)
      _ <- data.traverse_(c => IO(assert(clue(c.size) <= clue(frameSize))))
    } yield ()
  }

  test("H2Stream sendMessageBody empty message should send one empty Data frame and half-close") {
    val config = defaultSettings

    for {
      sq <- streamAndQueue(config)
      (stream, queue) = sq
      _ <- testMessageSize(stream, queue, 0, messageSize = 0, numFrames = 1)
      _ <- assertIO(stream.state.get.map(_.state), H2Stream.StreamState.HalfClosedLocal)
    } yield ()
  }

  test(
    "client should not hang when endStream is sent by H2Frame.Headers as trailers"
  ) {
    for {
      stream <- clientStream(defaultSettings)
      headers <- stream.hpack.encodeHeaders(
        NonEmptyList.of(
          (":status", "200", false),
          (":method", "GET", false),
        )
      )
      init = H2Frame.Headers(
        0,
        None,
        endStream = false,
        endHeaders = false,
        headers,
        None,
      )
      headers <- stream.hpack.encodeHeaders(
        NonEmptyList.of(
          ("grpc-status", "0", false)
        )
      )
      trailers = H2Frame.Headers(
        1,
        None,
        endStream = true,
        endHeaders = true,
        headers,
        None,
      )

      source = fs2.Stream.repeatEval(IO(42.toByte)).take(10000).chunkN(100)
      actual <- Queue.unbounded[IO, Chunk[Byte]]

      _ <- stream.receiveHeaders(init)
      _ <- assertIO(stream.state.get.map(_.state), H2Stream.StreamState.Open)
      _ <- (
        // Taken from `sendMessageBody` to emulate messages sent from server.
        source.zipWithNext
          .foreach { case (c, nextChunk) =>
            val noTrailers = false
            val isEndStream = nextChunk.isEmpty && noTrailers
            stream.receiveData(H2Frame.Data(0, c.toByteVector, None, isEndStream)) >>
              actual.offer(c)
          }
          .compile
          .drain >>
          // Taken from `sendTrailerHeaders` to emulate trailers headers sent from server.
          stream
            .receiveHeaders(trailers)
      )
        // Note: Without closing `readBuffer` on headers with `endStream=true`, `readBody` hangs forever.
        .both(stream.readBody.compile.drain)
      expect <- source.compile.count
      _ <- assertIO(
        actual.size,
        expect.toInt,
        "expect the client to consume all the elements in the streaming response body before closing",
      )
      _ <- assertIO(stream.state.get.map(_.state), H2Stream.StreamState.HalfClosedRemote)
    } yield ()
  }

  test(
    "H2Stream sendMessageBody body=16kb frameSize=16kb should send one Data frame and half-close"
  ) {
    val frameSize = 16384
    val config = defaultSettings.copy(
      maxFrameSize = H2Frame.Settings.SettingsMaxFrameSize(frameSize)
    )

    for {
      sq <- streamAndQueue(config)
      (stream, queue) = sq
      _ <- testMessageSize(stream, queue, frameSize, messageSize = frameSize, numFrames = 1)
      _ <- assertIO(stream.state.get.map(_.state), H2Stream.StreamState.HalfClosedLocal)
    } yield ()
  }

  test(
    "H2Stream sendMessageBody body=50kb frameSize=16kb should send four Data frames and half-close"
  ) {
    val frameSize = 16384
    val config = defaultSettings.copy(
      maxFrameSize = H2Frame.Settings.SettingsMaxFrameSize(frameSize)
    )

    for {
      sq <- streamAndQueue(config)
      (stream, queue) = sq
      _ <- testMessageSize(stream, queue, frameSize, messageSize = 51200, numFrames = 4)
      _ <- assertIO(stream.state.get.map(_.state), H2Stream.StreamState.HalfClosedLocal)
    } yield ()
  }

  test(
    "H2Stream sendMessageBody body=50kb frameSize=32kb should send two Data frames and half-close"
  ) {
    val frameSize = 32768
    val config = defaultSettings.copy(
      maxFrameSize = H2Frame.Settings.SettingsMaxFrameSize(frameSize)
    )

    for {
      sq <- streamAndQueue(config)
      (stream, queue) = sq
      _ <- testMessageSize(stream, queue, frameSize, messageSize = 51200, numFrames = 2)
      _ <- assertIO(stream.state.get.map(_.state), H2Stream.StreamState.HalfClosedLocal)
    } yield ()
  }

  test("H2Stream sendMessageBody empty message without 'Trailer' header closes Stream") {
    val config = defaultSettings

    for {
      sq <- streamAndQueue(config)
      (stream, _) = sq
      resp = Response[IO](Status.Ok, HttpVersion.`HTTP/2`)
      _ <- stream.sendMessageBody(resp)
      _ <- assertIO(stream.state.get.map(_.state), H2Stream.StreamState.HalfClosedLocal)
    } yield ()
  }

  test("H2Stream sendMessageBody empty message with 'Trailer' header keeps stream open") {
    val config = defaultSettings

    for {
      sq <- streamAndQueue(config)
      (stream, _) = sq
      resp = Response[IO](Status.Ok, HttpVersion.`HTTP/2`)
        .withTrailerHeaders(IO.pure(Headers("Trailer" -> "Expires")))
      _ <- stream.sendMessageBody(resp)
      _ <- assertIO(stream.state.get.map(_.state), H2Stream.StreamState.Open)
    } yield ()
  }

  test("H2Stream sendMessageBody non-empty message with 'Trailer' header keeps stream open") {
    val frameSize = 16384
    val config = defaultSettings.copy(
      maxFrameSize = H2Frame.Settings.SettingsMaxFrameSize(frameSize)
    )

    for {
      sq <- streamAndQueue(config)
      (stream, _) = sq
      resp = Response[IO](Status.Ok, HttpVersion.`HTTP/2`)
        .withTrailerHeaders(IO.pure(Headers("Trailer" -> "Expires")))
        .withEntity("0" * frameSize * 2)
      _ <- stream.sendMessageBody(resp)
      _ <- assertIO(stream.state.get.map(_.state), H2Stream.StreamState.Open)
    } yield ()
  }

  test(
    "H2Stream sendMessageBody should flush data without waiting for the next chunk"
  ) {

    def bodyStream(gate: Deferred[IO, Unit]): Stream[IO, Byte] =
      Stream("hello").through(utf8.encode) ++
        Stream.eval(gate.get).drain ++
        Stream("world").through(utf8.encode)

    def assertFrame(chunk: Chunk[H2Frame], expected: String, endStream: Boolean) = {
      assert(chunk.size == 1)
      val frame = chunk.collectFirst { case data: H2Frame.Data => data }.get

      assertEquals(frame.data.decodeUtf8, Right(expected))
      assertEquals(frame.endStream, endStream)
    }

    for {
      sq <- streamAndQueue(defaultSettings)
      (stream, queue) = sq
      gate <- Deferred[IO, Unit]
      resp = Response[IO](Status.Ok, HttpVersion.`HTTP/2`).withBodyStream(bodyStream(gate))
      fiber <- stream.sendMessageBody(resp).start
      firstChunk <- queue.take
      _ <- IO(assertFrame(firstChunk, "hello", endStream = false))
      _ <- assertIO(stream.state.get.map(_.state), H2Stream.StreamState.Open)
      _ <- gate.complete(())
      secondChunk <- queue.take
      _ <- IO(assertFrame(secondChunk, "world", endStream = false))
      lastChunk <- queue.take
      _ <- IO(assertFrame(lastChunk, "", endStream = true))
      _ <- fiber.joinWithNever
      _ <- assertIO(stream.state.get.map(_.state), H2Stream.StreamState.HalfClosedLocal)
    } yield ()
  }

  test("sendData clears stallStart when peer grants enough credit for a full chunk") {
    TestControl.executeEmbed {
      for {
        sq <- streamAndQueue(defaultSettings)
        (stream, _) = sq
        _ <- stream.state.update(_.copy(writeWindow = 0))
        fiber <- stream.sendData(ByteVector.fill(10)(0), endStream = false).start
        _ <- IO.sleep(10.seconds)
        stalled <- stream.state.get.map(_.stallStart)
        _ = assert(stalled.isDefined)
        _ <- stream.receiveWindowUpdate(H2Frame.WindowUpdate(1, 10))
        _ <- fiber.joinWithNever
        st <- stream.state.get
      } yield assertEquals(st.stallStart, None)
    }
  }

  test("sendData preserves stallStart across a sub-chunk drip") {
    TestControl.executeEmbed {
      for {
        sq <- streamAndQueue(defaultSettings)
        (stream, _) = sq
        _ <- stream.state.update(_.copy(writeWindow = 0))
        fiber <- stream.sendData(ByteVector.fill(10)(0), endStream = false).start
        _ <- IO.sleep(10.seconds)
        stalled0 <- stream.state.get.map(_.stallStart)
        _ = assert(stalled0.isDefined)
        _ <- stream.receiveWindowUpdate(H2Frame.WindowUpdate(1, 1))
        _ <- IO.sleep(10.seconds)
        stalled1 <- stream.state.get.map(_.stallStart)
        _ <- fiber.cancel
      } yield assertEquals(stalled1, stalled0)
    }
  }

  test("finish does not reset a stream the peer has already closed") {
    for {
      sq <- streamAndQueue(defaultSettings)
      (stream, outgoing) = sq
      _ <- stream.receiveData(emptyData(endStream = true))
      _ <- stream.sendData(ByteVector.empty, endStream = true)
      _ <- stream.finish(H2Error.NoError)
      st <- stream.state.get.map(_.state)
      codes <- rstCodes(outgoing)
    } yield {
      assertEquals(st, H2Stream.StreamState.Closed)
      assertEquals(codes, Nil)
    }
  }

  List[(String, H2Stream[IO] => IO[Unit])](
    "RST_STREAM" -> (_.receiveRstStream(H2Error.Cancel.toRst(1))),
    "GOAWAY" -> (_.receiveGoAway(H2Error.NoError.toGoAway(1))),
    "local reset" -> (_.rstStream(H2Error.Cancel)),
  ).foreach { case (name, cancel) =>
    test(s"$name preserves completed bodies and releases aborted bodies exactly once") {
      List(false, true).traverse_ { completed =>
        for {
          credited <- Ref[IO].of(0)
          sq <- streamAndQueue(defaultSettings, creditConnection = n => credited.update(_ + n))
          (stream, _) = sq
          _ <- stream.receiveData(H2Frame.Data(1, ByteVector.fill(10)(0), None, completed))
          _ <- cancel(stream).replicateA_(2)
          before <- credited.get
          _ <- stream.state.get
            .flatMap(_.readBuffer.stream.compile.count)
            .assertEquals(0L)
            .unlessA(completed)
          body <- stream.readBody.compile.toVector.attempt
          _ <- stream.finish(H2Error.Cancel)
          after <- credited.get
        } yield {
          assertEquals(before, if (completed) 0 else 10)
          if (completed) assertEquals(body, Right(Vector.fill[Byte](10)(0)))
          else assert(body.left.exists(_.isInstanceOf[CancellationException]), clue(body))
          assertEquals(after, 10)
        }
      }
    }

    test(s"$name lets an active reader release its buffered bytes without blocking the reset") {
      TestControl.executeEmbed {
        for {
          credited <- Ref[IO].of(0)
          creditStarted <- Deferred[IO, Unit]
          resumeCredit <- Deferred[IO, Unit]
          sq <- streamAndQueue(
            defaultSettings,
            creditConnection = n =>
              creditStarted.complete(()).flatMap { first =>
                resumeCredit.get.whenA(first) >> credited.update(_ + n)
              },
          )
          (stream, _) = sq
          _ <- stream
            .receiveData(H2Frame.Data(1, ByteVector.fill(10)(0), None, false))
            .replicateA_(2)
          _ <- stream.readBody.compile.drain.attempt.background.use { reader =>
            (for {
              _ <- creditStarted.get
              _ <- cancel(stream).replicateA_(2).timeout(2.seconds)
              _ <- resumeCredit.complete(())
              result <- reader.flatMap(_.embedNever).timeout(2.seconds)
              _ = assert(result.left.exists(_.isInstanceOf[CancellationException]), clue(result))
              buffered <- stream.state.get.flatMap(_.readBuffer.stream.compile.count)
              after <- credited.get
            } yield {
              assertEquals(buffered, 0L)
              assertEquals(after, 20)
            }).guarantee(resumeCredit.complete(()).void)
          }
        } yield ()
      }
    }
  }

  test("END_STREAM releases the stream credit sender and preserves the body") {
    TestControl.executeEmbed {
      for {
        sq <- streamAndQueue(
          defaultSettings,
          outgoingQueue = Queue.bounded[IO, Chunk[H2Frame]](1),
        )
        (stream, outgoing) = sq
        _ <- outgoing.offer(Chunk.singleton(H2Frame.Ping.ack))
        _ <- stream
          .receiveData(H2Frame.Data(1, ByteVector.fill(16384)(0), None, false))
          .replicateA_(2)
        count <- stream.readBody.compile.count.background.use { reader =>
          IO.sleep(1.second) >>
            stream.sendReadWindowUpdate.background.use { sender =>
              IO.sleep(1.second) >>
                stream.receiveData(emptyData(endStream = true)) >>
                sender.flatMap(_.embedNever).timeout(2.seconds) >>
                reader.flatMap(_.embedNever).timeout(2.seconds)
            }
        }
      } yield assertEquals(count, 32768L)
    }
  }

  test("a content-length mismatch on the final data frame still resets the stream") {
    for {
      sq <- streamAndQueue(defaultSettings)
      (stream, outgoing) = sq
      _ <- stream.state.update(
        _.copy(state = H2Stream.StreamState.HalfClosedLocal, contentLengthCheck = Some((10L, 0L)))
      )
      _ <- stream.receiveData(H2Frame.Data(1, ByteVector.fill(5)(1), None, endStream = true))
      codes <- rstCodes(outgoing)
      read <- stream.readBody.compile.drain.attempt
    } yield {
      assertEquals(codes, List(H2Error.ProtocolError.value))
      assert(read.left.exists(_.isInstanceOf[CancellationException]), clue(read))
    }
  }

  test("sendTrailerHeaders with an empty trailer set still ends the stream") {
    val resp = Response[IO](Status.Ok, HttpVersion.`HTTP/2`)
      .withAttribute(org.http4s.Message.Keys.TrailerHeaders[IO], IO.pure(Headers.empty))
    for {
      sq <- streamAndQueue(defaultSettings)
      (stream, outgoing) = sq
      _ <- stream.sendMessageBody(resp)
      _ <- stream.sendTrailerHeaders(resp)
      frames <- outgoing.tryTakeN(None).map(_.flatMap(_.toList))
      st <- stream.state.get.map(_.state)
    } yield {
      assert(
        frames.exists { case H2Frame.Data(1, _, _, true) => true; case _ => false },
        clue(frames),
      )
      assertEquals(st, H2Stream.StreamState.HalfClosedLocal)
    }
  }

  test("data exceeding the stream window is a flow control error") {
    val window: Int = defaultSettings.initialWindowSize.windowSize
    for {
      sq <- streamAndQueue(defaultSettings)
      (stream, outgoing) = sq
      _ <- stream.receiveData(H2Frame.Data(1, ByteVector.fill(window.toLong)(0), None, false))
      _ <- stream.receiveData(H2Frame.Data(1, ByteVector.fill(1)(0), None, false))
      codes <- rstCodes(outgoing)
    } yield assertEquals(codes, List(H2Error.FlowControlError.value))
  }

  test("stream window is granted on consumption, not on receipt") {
    val window: Int = defaultSettings.initialWindowSize.windowSize
    val size = window / 2 + 1
    for {
      credited <- Ref[IO].of(0)
      sq <- streamAndQueue(defaultSettings, creditConnection = n => credited.update(_ + n))
      (stream, outgoing) = sq
      _ <- stream.receiveData(H2Frame.Data(1, ByteVector.fill(size.toLong)(0), None, false))
      onReceipt <- outgoing.tryTakeN(None)
      _ <- stream.readBody.take(size.toLong).compile.drain
      _ <- stream.sendReadWindowUpdate
      onConsume <- outgoing.tryTakeN(None).map(_.flatMap(_.toList))
      toConnection <- credited.get
    } yield {
      assertEquals(onReceipt, Nil)
      assertEquals(onConsume, List(H2Frame.WindowUpdate(1, size)))
      assertEquals(toConnection, size)
    }
  }

  test("padding counts against the window and is returned to the connection") {
    for {
      credited <- Ref[IO].of(0)
      sq <- streamAndQueue(defaultSettings, creditConnection = n => credited.update(_ + n))
      (stream, _) = sq
      _ <- stream.receiveData(
        H2Frame.Data(1, ByteVector.fill(5)(0), Some(ByteVector.fill(10)(0)), false)
      )
      st <- stream.state.get
      toConnection <- credited.get
    } yield {
      assertEquals(defaultSettings.initialWindowSize.windowSize - st.readWindow, 16)
      assertEquals(st.unreadBytes, 5)
      assertEquals(toConnection, 11)
    }
  }

  test("empty data frames are not buffered") {
    for {
      sq <- streamAndQueue(defaultSettings)
      (stream, _) = sq
      _ <- stream.receiveData(emptyData()).replicateA_(1000)
      _ <- stream.receiveData(emptyData(endStream = true))
      st <- stream.state.get
      buffered <- st.readBuffer.stream.compile.count
    } yield {
      assertEquals(st.unreadBytes, 0)
      assertEquals(buffered, 0L)
    }
  }

  test("unread bytes are returned to the connection when the stream is finished") {
    for {
      credited <- Ref[IO].of(0)
      sq <- streamAndQueue(defaultSettings, creditConnection = n => credited.update(_ + n))
      (stream, _) = sq
      _ <- stream.receiveData(H2Frame.Data(1, ByteVector.fill(1000)(0), None, false))
      _ <- stream.finish(H2Error.NoError)
      toConnection <- credited.get
    } yield assertEquals(toConnection, 1000)
  }

  test("finish interrupts a reader still on the body") {
    for {
      sq <- streamAndQueue(defaultSettings)
      (stream, _) = sq
      _ <- stream.receiveData(H2Frame.Data(1, ByteVector.fill(10)(0), None, false))
      reader <- stream.readBody.compile.drain.start
      _ <- IO.sleep(100.millis)
      _ <- stream.finish(H2Error.NoError).timeout(5.seconds)
      outcome <- reader.join.timeout(5.seconds)
    } yield outcome match {
      case Outcome.Errored(_: CancellationException) => ()
      case other => fail(s"expected the reader to end with the reset, got $other")
    }
  }

  test("padding-only DATA must replenish the stream window") {
    for {
      sq <- streamAndQueue(defaultSettings)
      (stream, outgoing) = sq
      _ <- stream.readBody.compile.drain.background.use { _ =>
        stream
          .receiveData(
            H2Frame.Data(1, ByteVector.empty, Some(ByteVector.fill(127)(0)), false)
          )
          .replicateA_(511) >>
          stream.receiveData(
            H2Frame.Data(1, ByteVector.empty, Some(ByteVector.fill(126)(0)), false)
          ) >> stream.sendReadWindowUpdate
      }
      frames <- outgoing.tryTakeN(None).map(_.flatMap(_.toList))
      state <- stream.state.get
    } yield {
      assertEquals(state.unreadBytes, 0)
      assert(
        frames.exists { case H2Frame.WindowUpdate(1, n) => n > 0; case _ => false },
        s"Padding exhausted the stream window (${state.readWindow}) without a WINDOW_UPDATE",
      )
    }
  }

  test("finish must credit final DATA arriving during discardUnread") {
    for {
      credited <- Ref[IO].of(0)
      creditStarted <- Deferred[IO, Unit]
      resumeCredit <- Deferred[IO, Unit]
      sq <- streamAndQueue(
        defaultSettings,
        creditConnection = n =>
          credited.update(_ + n) >> creditStarted.complete(()).flatMap { first =>
            if (first) resumeCredit.get else IO.unit
          },
      )
      (stream, _) = sq
      _ <- stream
        .receiveData(H2Frame.Data(1, ByteVector.fill(16384)(0), None, false))
        .replicateA_(2)
      _ <- stream.sendData(ByteVector.empty, endStream = true)
      _ <- stream.finish(H2Error.NoError).background.use { done =>
        (creditStarted.get.timeout(2.seconds) >>
          stream.receiveData(H2Frame.Data(1, ByteVector.fill(100)(0), None, true)) >>
          resumeCredit.complete(()) >> done.flatMap(_.embedNever))
          .guarantee(resumeCredit.complete(()).void)
      }
      total <- credited.get
      state <- stream.state.get
    } yield {
      assertEquals(state.state, H2Stream.StreamState.Closed)
      assertEquals(total, 32868)
      assertEquals(state.unreadBytes, 0)
    }
  }

}
