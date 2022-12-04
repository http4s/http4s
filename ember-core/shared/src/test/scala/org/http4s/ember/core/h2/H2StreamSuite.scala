package org.http4s.ember.core.h2

import cats.syntax.all._
import cats.effect.std.Queue
import org.http4s.{
  EntityEncoder,
  Headers,
  Http4sSuite,
  HttpVersion,
  Message,
  Request,
  Response,
  Status,
}
import org.http4s.ember.core.h2.H2Frame.Settings.SettingsMaxFrameSize
import cats.effect.{Deferred, IO, Ref}
import fs2.Chunk
import org.http4s.ember.core.h2.H2Frame
import org.scalacheck.Gen.choose
import org.scalacheck.effect.PropF.forAllF
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scodec.bits.ByteVector

class H2StreamSuite extends Http4sSuite {
  test("H2Stream should split data frames as defined by client settings") {
    val defaultSettings = H2Frame.Settings.ConnectionSettings.default

    forAllF(choose(16 << 10, 64 << 10)) { frameSize =>
      val config = defaultSettings.copy(
        maxFrameSize = SettingsMaxFrameSize(frameSize)
      )

      def testMessageSize(
          outgoing: Queue[IO, Chunk[H2Frame]],
          stream: H2Stream[IO],
          messageSize: Int,
          frames: Int,
      ) = {
        val sample = Response[IO](Status.Ok, HttpVersion.`HTTP/2`).withEntity("0" * messageSize)

        for {
          _ <- stream.sendMessageBody(sample)
          chunks <- outgoing
            .tryTakeN(None)
            .map(_.flatMap(_.toList).collect { case H2Frame.Data(_, data, _, _) =>
              data
            })
          _ <- assertIO(IO(chunks.size), frames)
          _ <- assertIO(IO(chunks.map(_.size).sum), messageSize.toLong)
          _ <- assertIO_(
            chunks.map(c => assertIOBoolean(IO(c.size < config.maxFrameSize.frameSize))).sequence_
          )
        } yield ()
      }

      for {
        writeBlock <- Deferred[IO, Either[Throwable, Unit]]
        req <- Deferred[IO, Either[Throwable, Request[fs2.Pure]]]
        resp <- Deferred[IO, Either[Throwable, Response[fs2.Pure]]]
        trailers <- Deferred[IO, Either[Throwable, Headers]]
        readBuffer <- Queue.unbounded[IO, Either[Throwable, ByteVector]]

        _ <- writeBlock.complete(Right(()))
        _ <- req.complete(Left(new Exception()))
        _ <- resp.complete(Left(new Exception()))
        _ <- trailers.complete(Right(Headers.empty))

        state <- Ref[IO].of(
          H2Stream.State[IO](
            H2Stream.StreamState.Idle,
            defaultSettings.initialWindowSize.windowSize,
            writeBlock,
            config.initialWindowSize.windowSize,
            req,
            resp,
            trailers,
            readBuffer,
            None,
          )
        )

        hpack <- Hpack.create[IO]

        outgoing <- Queue.unbounded[IO, Chunk[H2Frame]]

        logger <- Slf4jLogger.fromClass[IO](classOf[H2StreamSuite])

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

        _ <- testMessageSize(outgoing, stream, frameSize / 2, 1)
        _ <- testMessageSize(outgoing, stream, frameSize, 1)
        _ <- testMessageSize(outgoing, stream, frameSize * 3 / 2, 2)
        _ <- testMessageSize(outgoing, stream, frameSize * 13 / 2, 7)
        _ <- testMessageSize(outgoing, stream, frameSize * 13, 13)
      } yield ()
    }
  }
}
