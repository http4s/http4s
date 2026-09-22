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
import com.comcast.ip4s._
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import fs2.io.net.Socket
import fs2.io.net.SocketOption
import org.http4s.Http4sSuite
import org.http4s.ember.core.EmberException
import org.typelevel.log4cats.noop.NoOpFactory
import scodec.bits.ByteVector

import scala.concurrent.duration._

/** The h2 read side honours `idleTimeout`, the way the HTTP/1 read does with
  * `timeoutMaybe(socket.read(receiveBufferSize), idleTimeout)`.
  *
  * A server peer that goes silent, either before the preface or after it, must not
  * hold its connection, and one of `maxConnections` slots, indefinitely. An open
  * stream suspends the timeout so a long lived request or response is not reaped.
  */
class H2IdleTimeoutSuite extends Http4sSuite {

  private val addr = SocketAddress(ip"127.0.0.1", port"0")

  private val localSettings = H2Frame.Settings.ConnectionSettings.default

  private val idleTimeout = 1.second

  /** How long we let the read loop run before calling it hung. Generous against
    * a 1 second idle timeout so a slow CI machine cannot fail this spuriously.
    */
  private val patience = 10.seconds

  /** A connected socket that never delivers another byte and never closes,
    * which is what a silent peer looks like to the read loop.
    */
  private val silentSocket: Socket[IO] = new Socket[IO] {
    def read(maxBytes: Int): IO[Option[Chunk[Byte]]] = IO.never
    def readN(numBytes: Int): IO[Chunk[Byte]] = IO.never
    def reads: Stream[IO, Byte] = Stream.never[IO]
    def write(bytes: Chunk[Byte]): IO[Unit] = IO.unit
    def writes: Pipe[IO, Byte, Nothing] = _.drain
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

  private def mkConnection: IO[H2Connection[IO]] =
    for {
      mapRef <- Ref[IO].of(Map.empty[Int, H2Stream[IO]])
      stateRef <- H2Connection.initState[IO](
        H2Frame.Settings.ConnectionSettings.default,
        H2Frame.Settings.ConnectionSettings.default.initialWindowSize,
        localSettings.initialWindowSize,
      )
      outgoing <- Queue.unbounded[IO, Chunk[H2Frame]]
      created <- Queue.unbounded[IO, Int]
      closed <- Queue.unbounded[IO, Int]
      hpack <- Hpack.create[IO](localSettings.maxHeaderListSize.fold(Int.MaxValue)(_.listSize))
      lock <- Semaphore[IO](1)
      ack <- Deferred[IO, Either[Throwable, H2Frame.Settings.ConnectionSettings]]
      logger <- NoOpFactory[IO].fromClass(classOf[H2IdleTimeoutSuite])
    } yield new H2Connection[IO](
      addr,
      H2Connection.ConnectionType.Server,
      idleTimeout, // receiveHeadersTimeout
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
      silentSocket,
      logger,
    )

  test("the preface read gives up on a peer that sends nothing") {
    for {
      outcome <- H2Server
        .checkConnectionPreface[IO](silentSocket, idleTimeout)
        .attempt
        .map(Option(_))
        .timeoutTo(patience, IO.pure(None))
      _ = outcome match {
        case Some(Left(_: EmberException.ReadTimeout)) => ()
        case Some(other) =>
          fail(s"expected the preface read to time out, got $other")
        case None =>
          fail(
            s"the preface read was still blocked on socket.read after $patience with " +
              s"an idleTimeout of $idleTimeout, so a connection that sends nothing " +
              "never reaches a protocol at all and is never reaped"
          )
      }
    } yield ()
  }

  test("readLoop gives up on a peer that stops sending") {
    for {
      h2 <- mkConnection
      // Some(outcome) if readLoop terminated on its own, either normally or in
      // error. None if it was still waiting on the socket when patience ran out.
      // Note the timeout has to be on the outside: cancelling readLoop runs its
      // guaranteeCase, which sets closed = true, so a cancelled loop is
      // indistinguishable from one that gave up if we inspect state afterwards.
      outcome <- h2.readLoop.attempt.map(Some(_)).timeoutTo(patience, IO.pure(None))
      _ = assert(
        outcome.isDefined,
        clue(
          s"readLoop was still blocked on socket.read after $patience with an " +
            s"idleTimeout of $idleTimeout, so a silent peer holds its connection " +
            "slot indefinitely"
        ),
      )
      // Only meaningful once the loop terminates by itself.
      closed <- h2.state.get.map(_.closed)
      _ = assert(closed, clue("readLoop gave up but left the connection open"))
    } yield ()
  }

  test("readLoop keeps waiting for a stream opened while the timeout was running") {
    for {
      h2 <- mkConnection
      _ <- (IO.sleep(idleTimeout / 2) >> h2.initiateRemoteStreamById(1)).start
      terminated <- h2.readLoop.attempt.as(true).timeoutTo(idleTimeout * 4, IO.pure(false))
      _ = assert(
        !terminated,
        clue(
          "readLoop reaped a connection whose stream was opened while the idle " +
            "timeout was running, so an h2c upgrade or a push promise can be cut off"
        ),
      )
    } yield ()
  }

  test("readLoop keeps waiting while a stream awaits its response") {
    for {
      h2 <- mkConnection
      stream <- h2.initiateRemoteStreamById(1)
      _ <- stream.state.update(_.copy(state = H2Stream.StreamState.HalfClosedRemote))
      terminated <- h2.readLoop.attempt.as(true).timeoutTo(idleTimeout * 4, IO.pure(false))
      _ = assert(
        !terminated,
        clue(
          "readLoop reaped a peer that had sent END_STREAM and was waiting on us, " +
            "so a long lived request or response cannot outlive the idle timeout"
        ),
      )
    } yield ()
  }

  test("readLoop gives up on a peer that stalls mid request") {
    for {
      h2 <- mkConnection
      stream <- h2.initiateRemoteStreamById(1)
      _ <- stream.state.update(_.copy(state = H2Stream.StreamState.Open))
      outcome <- h2.readLoop.attempt.map(Some(_)).timeoutTo(patience, IO.pure(None))
      _ = assert(
        outcome.isDefined,
        clue(
          s"readLoop was still blocked on socket.read after $patience, so headers " +
            "without their body hold the connection slot as long as the peer likes"
        ),
      )
      closed <- h2.state.get.map(_.closed)
      _ = assert(closed, clue("readLoop gave up but left the connection open"))
    } yield ()
  }
}
