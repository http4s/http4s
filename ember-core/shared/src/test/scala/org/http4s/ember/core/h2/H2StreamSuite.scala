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
import org.http4s.Headers
import org.http4s.Http4sSuite
import org.http4s.HttpVersion
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.ember.core.h2.H2Frame
import org.http4s.ember.core.h2.H2Frame.Settings.SettingsMaxFrameSize
import org.typelevel.log4cats
import scodec.bits.ByteVector

class H2StreamSuite extends Http4sSuite {
  val defaultSettings = H2Frame.Settings.ConnectionSettings.default

  def streamAndQueue(
      config: H2Frame.Settings.ConnectionSettings,
      state: H2Stream.StreamState,
  ): IO[(H2Stream[IO], Queue[IO, Chunk[H2Frame]])] =
    for {
      writeBlock <- Deferred[IO, Either[Throwable, Unit]]
      req <- Deferred[IO, Either[Throwable, Request[fs2.Pure]]]
      resp <- Deferred[IO, Either[Throwable, Response[fs2.Pure]]]
      trailers <- Deferred[IO, Either[Throwable, Headers]]
      readBuffer <- Queue.unbounded[IO, Either[Throwable, ByteVector]]

      _ <- writeBlock.complete(Either.unit)
      _ <- req.complete(Left(new Exception()))
      _ <- resp.complete(Left(new Exception()))
      _ <- trailers.complete(Right(Headers.empty))

      state <- Ref[IO].of(
        H2Stream.State[IO](
          state = state,
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

  def testMessageSize(
      stream: H2Stream[IO],
      outgoing: Queue[IO, Chunk[H2Frame]],
      frameSize: Int,
      messageSize: Int,
      frames: Int,
  ) = {
    val sample = Response[IO](Status.Ok, HttpVersion.`HTTP/2`)
      .withEntity("0" * messageSize)

    for {
      _ <- stream.sendMessageBody(sample)
      chunks <- outgoing.take.replicateA(frames).map(_.flatMap(_.toList))
      data = chunks.collect { case H2Frame.Data(_, data, _, _) => data }
      _ <- assertIO(IO(data.size), frames)
      _ <- assertIO(IO(data.map(_.size).sum), messageSize.toLong)
      _ <- data.traverse_(c => IO(assert(clue(c.size) <= clue(frameSize))))
    } yield ()
  }

  test("H2Stream(open) sendMessageBody empty message should send one empty Data frame") {
    val config = defaultSettings

    for {
      sq <- streamAndQueue(config, H2Stream.StreamState.Open)
      (stream, queue) = sq
      _ <- testMessageSize(stream, queue, 0, 0, 1)
    } yield ()
  }

  test("H2Stream(open) sendMessageBody frameSize=16384 should send one Data frame") {
    val frameSize = 16 << 10
    val config = defaultSettings.copy(
      maxFrameSize = SettingsMaxFrameSize(frameSize)
    )

    for {
      sq <- streamAndQueue(config, H2Stream.StreamState.Open)
      (stream, queue) = sq
      _ <- testMessageSize(stream, queue, frameSize, frameSize, 1)
    } yield ()
  }
}
