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
import cats.effect.testkit.TestControl
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.{Chunk, Pipe, Stream}
import fs2.io.net.{Socket, SocketOption}
import org.http4s._
import org.typelevel.ci.CIString
import org.typelevel.log4cats.noop.NoOpFactory
import scodec.bits.ByteVector

import scala.concurrent.duration._

class H2ServerGraceSuite extends Http4sSuite {
  private val settings = H2Frame.Settings.ConnectionSettings.default
  private val addr = SocketAddress(ip"127.0.0.1", port"0")

  private def socket(
      incoming: Queue[IO, IO[Chunk[Byte]]],
      outgoing: Queue[IO, H2Frame],
  ): Socket[IO] = new Socket[IO] {
    def read(maxBytes: Int): IO[Option[Chunk[Byte]]] = incoming.take.flatten.map(Some(_))
    def write(bytes: Chunk[Byte]): IO[Unit] =
      Stream
        .unfold(bytes.toByteVector)(H2Frame.RawFrame.fromByteVector)
        .evalMap { raw =>
          IO.fromEither(H2Frame.fromRaw(raw).leftMap(e => new RuntimeException(e.toString)))
            .flatMap(outgoing.offer)
        }
        .compile
        .drain
    def reads: Stream[IO, Byte] = Stream.repeatEval(read(8192)).unNoneTerminate.unchunks
    def readN(numBytes: Int): IO[Chunk[Byte]] = reads.take(numBytes.toLong).compile.to(Chunk)
    def writes: Pipe[IO, Byte, Nothing] = _.chunks.evalMap(write).drain
    def endOfInput: IO[Unit] = IO.unit
    def endOfOutput: IO[Unit] = IO.unit
    def isOpen: IO[Boolean] = IO.pure(true)
    def localAddress: IO[SocketAddress[IpAddress]] = IO.pure(addr)
    def remoteAddress: IO[SocketAddress[IpAddress]] = IO.pure(addr)
    def peerAddress: GenSocketAddress = addr
    def address: GenSocketAddress = addr
    def getOption[A](key: SocketOption.Key[A]): IO[Option[A]] = IO.pure(None)
    def setOption[A](key: SocketOption.Key[A], value: A): IO[Unit] = IO.unit
    def supportedOptions: IO[Set[SocketOption.Key[_]]] = IO.pure(Set.empty)
  }

  private def readThrough(outgoing: Queue[IO, H2Frame])(
      last: H2Frame => Boolean
  ): IO[Vector[H2Frame]] =
    Stream
      .fromQueueUnterminated(outgoing)
      .takeThrough(f => !last(f) && !f.isInstanceOf[H2Frame.GoAway])
      .compile
      .toVector

  private def ends(id: Int)(frame: H2Frame): Boolean = frame match {
    case d: H2Frame.Data => d.identifier == id && d.endStream
    case h: H2Frame.Headers => h.identifier == id && h.endStream
    case _ => false
  }

  for (trailers <- List(false, true)) {
    val kind = if (trailers) "trailers" else "DATA"
    test(s"in-flight $kind survives an early response within the grace period") {
      TestControl.executeEmbed {
        (for {
          incoming <- Queue.unbounded[IO, IO[Chunk[Byte]]]
          outgoing <- Queue.unbounded[IO, H2Frame]
          deliver <- Deferred[IO, Unit]
          hpack <- Hpack.create[IO](4096)
          logger <- NoOpFactory[IO].fromClass(classOf[H2ServerGraceSuite])
          request = Request[IO](Method.POST, Uri.unsafeFromString("http://localhost/"))
          first <- hpack.encodeHeaders(PseudoHeaders.requestToHeaders(request))
          late <-
            if (trailers)
              hpack
                .encodeHeaders(NonEmptyList.one(("x-checksum", "abc", false)))
                .map(block => H2Frame.Headers(1, None, true, true, block, None): H2Frame)
            else IO.pure(H2Frame.Data(1, ByteVector(1), None, false): H2Frame)
          next <- hpack.encodeHeaders(
            PseudoHeaders.requestToHeaders(request.putHeaders("x-checksum" -> "abc"))
          )
          send = (frame: H2Frame) =>
            incoming.offer(IO.pure(Chunk.byteVector(H2Frame.toByteVector(frame))))
          _ <- send(H2Frame.Settings.ConnectionSettings.toSettings(settings))
          _ <- send(H2Frame.Headers(1, None, false, true, first, None))
          // Sent before the response; only delivery is delayed.
          _ <- incoming.offer(deliver.get.as(Chunk.byteVector(H2Frame.toByteVector(late))))
          app = HttpApp[IO](req =>
            IO.pure(
              Response[IO]().withEntity(
                req.headers.get(CIString("x-checksum")).fold("early")(_.head.value)
              )
            )
          )
          _ <- H2Server
            .fromSocket(
              socket(incoming, outgoing),
              app,
              Duration.Inf,
              Duration.Inf,
              settings,
              logger,
            )
            .useForever
            .background
            .use { _ =>
              for {
                response <- readThrough(outgoing)(_ == H2Error.NoError.toRst(1))
                _ = assert(response.exists(ends(1)), clue(response))
                _ = assertEquals(response.last, H2Error.NoError.toRst(1), clue(response))
                // Let server cleanup run, staying inside the one-second grace period.
                _ <- IO.sleep(100.millis)
                _ <- deliver.complete(())
                _ <- send(H2Frame.Headers(3, None, true, true, next, None))
                result <- readThrough(outgoing)(ends(3))
                _ = assert(!result.exists(_.isInstanceOf[H2Frame.GoAway]), clue(result))
                body = ByteVector.concat(result.collect {
                  case d: H2Frame.Data if d.identifier == 3 => d.data
                })
                // The trailer case also checks the shared HPACK table.
                _ = assertEquals(body.decodeUtf8, Right("abc"), clue(result))
              } yield ()
            }
        } yield ()).timeout(3.seconds)
      }
    }
  }
}
