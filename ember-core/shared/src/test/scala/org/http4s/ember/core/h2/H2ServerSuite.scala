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
import cats.effect.testkit.TestControl
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import fs2.io.net.Socket
import fs2.io.net.SocketOption
import org.http4s._
import org.http4s.syntax.all._
import org.typelevel.log4cats.noop.NoOpFactory
import scodec.bits.ByteVector

import scala.concurrent.duration._

class H2ServerSuite extends Http4sSuite {

  // Reads come from `input`, one frame per read. Writes are recorded.
  private def stubSocket(input: Queue[IO, Chunk[Byte]], recorded: Ref[IO, ByteVector]): Socket[IO] =
    new Socket[IO] {
      def read(maxBytes: Int): IO[Option[Chunk[Byte]]] = input.take.map(Some(_))
      def write(bytes: Chunk[Byte]): IO[Unit] = recorded.update(_ ++ bytes.toByteVector)
      def peerAddress: GenSocketAddress = SocketAddress(ip"127.0.0.1", port"0")
      // Implement only as necessary...
      def endOfInput: IO[Unit] = ???
      def endOfOutput: IO[Unit] = ???
      def isOpen: IO[Boolean] = ???
      def localAddress: IO[SocketAddress[IpAddress]] = ???
      def readN(numBytes: Int): IO[Chunk[Byte]] = ???
      def reads: Stream[IO, Byte] = ???
      def remoteAddress: IO[SocketAddress[IpAddress]] = ???
      def writes: Pipe[IO, Byte, Nothing] = ???
      def address: GenSocketAddress = ???
      def getOption[A](key: SocketOption.Key[A]): IO[Option[A]] = ???
      def setOption[A](key: SocketOption.Key[A], value: A): IO[Unit] = ???
      def supportedOptions: IO[Set[SocketOption.Key[_]]] = ???
    }

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

  private final class Client(input: Queue[IO, Chunk[Byte]], hpack: Hpack[IO]) {
    def send(frame: H2Frame): IO[Unit] =
      input.offer(Chunk.byteVector(H2Frame.toByteVector(frame)))

    def sendHeaders(id: Int, req: Request[IO], endStream: Boolean): IO[Unit] =
      hpack
        .encodeHeaders(PseudoHeaders.requestToHeaders(req))
        .flatMap(block =>
          send(H2Frame.Headers(id, None, endStream, endHeaders = true, block, None))
        )

    def sendData(id: Int, size: Int): IO[Unit] =
      send(H2Frame.Data(id, ByteVector.fill(size.toLong)(0), None, endStream = false))
  }

  // Runs a server connection for `app` while `client` talks to it, then returns what it wrote.
  private def serve(app: HttpApp[IO])(client: Client => IO[Unit]): IO[Vector[H2Frame]] =
    for {
      input <- Queue.unbounded[IO, Chunk[Byte]]
      writes <- Ref[IO].of(ByteVector.empty)
      logger <- NoOpFactory[IO].fromClass(classOf[H2ServerSuite])
      hpack <- Hpack.create[IO](Int.MaxValue)
      settings = H2Frame.Settings.ConnectionSettings.default
      server <- H2Server
        .fromSocket(stubSocket(input, writes), app, Duration.Inf, 60.seconds, settings, logger)
        .useForever
        .start
      c = new Client(input, hpack)
      _ <- c.send(H2Frame.Settings.ConnectionSettings.toSettings(settings))
      _ <- client(c)
      _ <- server.cancel
      written <- writes.get
    } yield decodeFrames(written)

  private def ends(id: Int): H2Frame => Boolean = {
    case H2Frame.Data(`id`, _, _, true) => true
    case H2Frame.Headers(`id`, _, true, _, _, _) => true
    case _ => false
  }

  private def on(id: Int): H2Frame => Boolean = {
    case f: H2Frame.Data => f.identifier == id
    case f: H2Frame.Headers => f.identifier == id
    case f: H2Frame.WindowUpdate => f.identifier == id
    case f: H2Frame.RstStream => f.identifier == id
    case _ => false
  }

  test("a response that doesn't wait for the request body resets the stream") {
    val ignoreBody = HttpApp[IO](_ => Response[IO](Status.Ok).pure[IO])
    TestControl
      .executeEmbed(
        serve(ignoreBody) { client =>
          client.sendHeaders(1, Request(Method.POST, uri"http://localhost/"), endStream = false) >>
            IO.sleep(1.second) >>
            // The rest of the upload, already on its way when the response went out.
            client.sendData(1, 16384) >>
            client.sendData(1, 16384) >>
            IO.sleep(1.second)
        }
      )
      .map { frames =>
        val reset = frames.indexOf(H2Error.NoError.toRst(1))
        assert(reset > frames.indexWhere(ends(1)), clue(frames))
        assert(!frames.drop(reset + 1).exists(on(1)), clue(frames))
        assertEquals(
          frames.collect { case H2Frame.WindowUpdate(0, increment) => increment },
          Vector(32768),
          clue(frames),
        )
        assert(!frames.exists(_.isInstanceOf[H2Frame.GoAway]), clue(frames))
      }
  }

  test("a slow response that ignores a full request buffer keeps the connection usable") {
    TestControl
      .executeEmbed(
        for {
          respond <- Deferred[IO, Unit]
          app = HttpApp[IO] { req =>
            if (req.method == Method.POST) respond.get.as(Response[IO](Status.Ok))
            else Response[IO](Status.Ok).pure[IO]
          }
          frames <- serve(app) { client =>
            client
              .sendHeaders(1, Request(Method.POST, uri"http://localhost/"), endStream = false) >>
              // More frames than the stream buffers, so the read loop waits for room.
              client.sendData(1, 1).replicateA_(130) >>
              IO.sleep(1.second) >>
              respond.complete(()) >>
              IO.sleep(1.second) >>
              client
                .sendHeaders(3, Request(Method.GET, uri"http://localhost/"), endStream = true) >>
              IO.sleep(1.second)
          }
        } yield frames
      )
      .map(frames => assert(frames.exists(ends(3)), clue(frames)))
  }
}
