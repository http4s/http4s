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

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.effect.std.Queue
import cats.syntax.all._
import fs2.Chunk
import fs2.concurrent.Channel
import org.http4s.Headers
import org.http4s.Http4sSuite
import org.http4s.HttpVersion
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.typelevel.log4cats
import scodec.bits.ByteVector

class H2StreamSuite extends Http4sSuite {
  val defaultSettings = H2Frame.Settings.ConnectionSettings.default

  def streamAndQueue(
      config: H2Frame.Settings.ConnectionSettings
  ): IO[(H2Stream[IO], Queue[IO, Chunk[H2Frame]])] =
    for {
      writeBlock <- Deferred[IO, Either[Throwable, Unit]]
      req <- Deferred[IO, Either[Throwable, Request[fs2.Pure]]]
      resp <- Deferred[IO, Either[Throwable, Response[fs2.Pure]]]
      trailers <- Deferred[IO, Either[Throwable, Headers]]
      readBuffer <- Channel.unbounded[IO, Either[Throwable, ByteVector]]

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
        )
      )
      hpack <- Hpack.create[IO]
      logger <- log4cats.noop.NoOpFactory[IO].fromClass(classOf[H2StreamSuite])
      outgoing <- Queue.unbounded[IO, Chunk[H2Frame]]
      stream = new H2Stream[IO](
        1,
        defaultSettings,
        H2Connection.ConnectionType.Server,
        IO.pure(config),
        state,
        hpack,
        outgoing,
        IO.unit,
        _ => IO.unit,
        logger,
      )
    } yield (stream, outgoing)

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
      (stream, queue) = sq
      resp = Response[IO](Status.Ok, HttpVersion.`HTTP/2`)
      _ <- stream.sendMessageBody(resp)
      _ <- assertIO(stream.state.get.map(_.state), H2Stream.StreamState.HalfClosedLocal)
    } yield ()
  }

  test("H2Stream sendMessageBody empty message with 'Trailer' header keeps stream open") {
    val config = defaultSettings

    for {
      sq <- streamAndQueue(config)
      (stream, queue) = sq
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
      (stream, queue) = sq
      resp = Response[IO](Status.Ok, HttpVersion.`HTTP/2`)
        .withTrailerHeaders(IO.pure(Headers("Trailer" -> "Expires")))
        .withEntity("0" * frameSize * 2)
      _ <- stream.sendMessageBody(resp)
      _ <- assertIO(stream.state.get.map(_.state), H2Stream.StreamState.Open)
    } yield ()
  }
}
