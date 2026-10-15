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

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.syntax.all._
import fs2.Chunk
import scodec.bits.ByteVector

import java.io.IOException
import java.net.SocketException

class RuntimeInfoSuite extends RuntimeDiagnostics.Suite {
  // Scripted peers intentionally emit invalid traffic; conformance cases supply their own scope.
  implicit private def codecSettings[F[_]: cats.ApplicativeThrow]: Http2PeerSettings[F] =
    Http2PeerSettings.untracked[F]

  import ScriptedPeer.peer

  private val resets = Vector(
    "macOS errno" -> new IOException("errno(54) Connection reset by peer"),
    "Linux errno" -> new IOException("errno(104) Connection reset by peer"),
    "socket reset" -> new SocketException("Connection reset"),
    "socket reset by peer" -> new SocketException("Connection reset by peer"),
  )

  test("Native recognizes only the specific connection-reset I/O errors and their causes") {
    resets.foreach { case (_, error) =>
      assert(RuntimeInfo.isConnectionReset(error))
      assert(RuntimeInfo.isConnectionReset(new IOException("read failed", error)))
    }
    Vector(
      new IOException("errno(53) Connection reset by peer"),
      new IOException("errno(54) Connection aborted"),
      new IOException("Connection reset by peer"),
      new IOException("prefix errno(54) Connection reset by peer"),
      new IOException("errno(104) Connection reset by peer suffix"),
      new IOException(null: String),
      new SocketException("Broken pipe"),
      new IllegalStateException("errno(54) Connection reset by peer"),
    ).foreach(error => assert(!RuntimeInfo.isConnectionReset(error)))
  }

  resets.foreach { case (name, error) =>
    test(s"raw reads preserve every buffered byte when accepting $name") {
      val bytes = Http1Wire.ascii("abc")
      for {
        p <- peer(bytes, fragment = 1, whenEmpty = IO.raiseError(error))
        rejected <- p._1.readToEnd().attempt
        received <- p._1.readToEnd(allowReset = true)
        closed <- p._1.readToEnd()
      } yield {
        assertEquals(rejected, Left(error))
        assertEquals(received, bytes)
        assertEquals(closed, ByteVector.empty)
      }
    }

    test(s"HTTP/2 accepts $name only before a frame starts") {
      val frame = Http2Wire.data(1, Http1Wire.ascii("abc"))
      for {
        empty <- peer(ByteVector.empty, whenEmpty = IO.raiseError(error))
          .flatMap(p => Http2Wire.readFrameOrClose(p._1))
        p <- peer(Http2Wire.encode(frame), fragment = 1, whenEmpty = IO.raiseError(error))
        received <- Http2Wire.readFrameOrClose(p._1)
        closed <- Http2Wire.readFrameOrClose(p._1)
      } yield {
        assertEquals(empty, None)
        assertEquals(received, Some(frame))
        assertEquals(closed, None)
      }
    }

    test(s"HTTP/2 rejects truncated frames ending with $name") {
      val header = ByteVector.fromValidHex("000009000000000001")
      Vector(header.take(1), header.take(8), header, header ++ Http1Wire.ascii("abc"))
        .traverse_ { bytes =>
          peer(bytes, fragment = 1, whenEmpty = IO.raiseError(error))
            .flatMap(p => Http2Wire.readFrameOrClose(p._1).attempt)
            .map(result => assertEquals(result, Left(error)))
        }
    }
  }

  private def closedWrite(shape: String): Throwable = {
    val message = "errno(54) Connection reset by peer"
    shape match {
      case "errno32" => new IOException("errno(32) Broken pipe")
      case "errno54" => new IOException(message)
      case "errno104" => new IOException("errno(104) Connection reset by peer")
      case "subclass" => new IOException(message) {}
      case "wrapper" => new IOException("wrapper", new IOException(message))
      case "cause" => new IOException(message, new IOException("other"))
      case "suppressed" =>
        val error = new IOException(message)
        error.addSuppressed(new IOException("other"))
        error
      case "socket-timeout" => new java.net.SocketTimeoutException(message)
      case "timeout" =>
        val error = new IOException(message)
        error.addSuppressed(new java.util.concurrent.TimeoutException("deadline"))
        error
      case "adapter" =>
        val error = new IOException(message)
        error.addSuppressed(new AdapterFailure("fixture write", new IOException("fixture")))
        error.addSuppressed(new java.util.concurrent.TimeoutException("deadline"))
        error
      case _ => new IOException("unrelated write error")
    }
  }

  private val goAway = Http2Wire.encode(
    Http2Wire.Frame(Http2Wire.GoAway, 0, 0, Http2Wire.uint32(0) ++ Http2Wire.uint32(6))
  )

  private def rejectionPeer(
      error: Throwable,
      tail: ByteVector,
      whenEmpty: IO[Option[Chunk[Byte]]] = IO.pure(None),
  ): IO[(RawPeer[IO], IO[ByteVector], IO[Int])] = {
    val prefix = Vector(
      Http2Wire.settings(),
      Http2Wire.settings(ack = true),
      Http2Wire.Frame(Http2Wire.Ping, 0, 0, Http1Wire.ascii("12345678")),
    )
      .foldLeft(ByteVector.empty)((bytes, frame) => bytes ++ Http2Wire.encode(frame))
    for {
      input <- Ref.of[IO, ByteVector](prefix ++ tail)
      writes <- Ref.of[IO, Int](0)
      transcript <- Transcript.create[IO]()
      raw <- RawPeer.create[IO](
        (n: Int) =>
          input
            .modify(bytes => (bytes.drop(n.toLong), bytes.take(n.toLong)))
            .flatMap(bytes =>
              if (bytes.isEmpty) whenEmpty else IO.pure(Some(Chunk.byteVector(bytes)))
            ),
        (_: Chunk[Byte]) =>
          writes.updateAndGet(_ + 1).flatMap(n => if (n == 4) IO.raiseError(error) else IO.unit),
        IO.unit,
        transcript,
      )
    } yield (raw, input.get, writes.get)
  }

  for (
    shape <- Vector(
      "errno32",
      "errno54",
      "errno104",
      "subclass",
      "wrapper",
      "cause",
      "suppressed",
      "socket-timeout",
      "timeout",
      "adapter",
      "ordinary",
    )
  )
    test(s"Native incidental write classification requires independent rejection: $shape") {
      val error = closedWrite(shape)
      val recognized = shape == "errno32" || shape == "errno54" || shape == "errno104"
      assertEquals(RuntimeInfo.isClosedWrite(error), recognized)
      for {
        p <- rejectionPeer(error, goAway)
        result <- WireCodecsSuite
          .runH2ServerCase("h2.server.reject-priority-length-4", Resource.pure(p._1))
          .attempt
        unread <- p._2
        writes <- p._3
      } yield {
        val expected =
          if (recognized) "pass"
          else if (shape == "socket-timeout" || shape == "timeout") "inconclusive"
          else "error"
        assertEquals(WireSuite.outcomeStatus(result), expected)
        assertEquals(unread, if (recognized) ByteVector.empty else goAway)
        assertEquals(writes, 4)
        if (!recognized && expected == "error") {
          val original = if (shape == "adapter") error.getSuppressed.head else error
          assertEquals(result, Left(original))
        }
      }
    }

  for {
    shape <- Vector("errno54", "errno104")
    tail <- Vector(
      "eof",
      "goaway",
      "malformed-tail",
      "partial-header",
      "partial-payload",
      "ordinary",
      "timeout",
      "adapter",
    )
  }
    test(s"Native $shape reply failure still checks the independent drain: $tail") {
      val readError = tail match {
        case "timeout" => new java.net.SocketTimeoutException("read deadline")
        case "adapter" => new AdapterFailure("fixture read", new IOException("fixture"))
        case _ => new IOException("unrelated read error")
      }
      val ending =
        if (tail == "ordinary" || tail == "timeout" || tail == "adapter")
          IO.raiseError[Option[Chunk[Byte]]](readError)
        else IO.pure(Option.empty[Chunk[Byte]])
      val bytes = tail match {
        case "eof" => ByteVector.empty
        case "malformed-tail" =>
          goAway ++ Http2Wire.encode(Http2Wire.Frame(Http2Wire.Ping, 0, 0, ByteVector.empty))
        case "partial-header" => goAway ++ ByteVector(0.toByte)
        case "partial-payload" =>
          goAway ++ Http2Wire.encode(Http2Wire.data(1, ByteVector.empty), Some(1))
        case _ => goAway
      }
      for {
        p <- rejectionPeer(closedWrite(shape), bytes, ending)
        result <- WireCodecsSuite
          .runH2ServerCase("h2.server.reject-priority-length-4", Resource.pure(p._1))
          .attempt
        unread <- p._2
      } yield {
        val expected = tail match {
          case "eof" | "goaway" => "pass"
          case "ordinary" | "adapter" => "error"
          case "timeout" => "inconclusive"
          case _ => "fail"
        }
        assertEquals(WireSuite.outcomeStatus(result), expected)
        assertEquals(unread, ByteVector.empty)
        if (expected == "error") assertEquals(result, Left(readError))
      }
    }

  for (shape <- Vector("errno54", "errno104"))
    test(
      s"Native $shape write failure cannot complete a held-open peer and cancellation releases it"
    ) {
      for {
        draining <- Deferred[IO, Unit]
        released <- Ref.of[IO, Boolean](false)
        completed <- Deferred[IO, Either[Throwable, Unit]]
        p <- rejectionPeer(closedWrite(shape), goAway, draining.complete(()).void *> IO.never)
        result <- WireCodecsSuite
          .runH2ServerCase(
            "h2.server.reject-priority-length-4",
            Resource.make(IO.pure(p._1))(_ => released.set(true)),
          )
          .attempt
          .flatMap(completed.complete)
          .start
          .bracket { fiber =>
            for {
              _ <- draining.get
              completion <- completed.tryGet
              before <- released.get
              _ <- IO { assertEquals(completion, None); assertEquals(before, false) }
              _ <- fiber.cancel
              outcome <- fiber.join
            } yield outcome
          }(_.cancel)
        after <- released.get
        unread <- p._2
      } yield {
        assert(result.isCanceled)
        assertEquals(after, true)
        assertEquals(unread, ByteVector.empty)
      }
    }
}
