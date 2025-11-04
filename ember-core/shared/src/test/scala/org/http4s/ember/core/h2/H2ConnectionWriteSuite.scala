package org.http4s.ember.core.h2

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.std.Queue
import com.comcast.ip4s.GenSocketAddress
import com.comcast.ip4s.Host
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.Port
import com.comcast.ip4s.SocketAddress
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import fs2.io.ClosedChannelException
import fs2.io.file.FileHandle
import fs2.io.net.Socket
import fs2.io.net.SocketException
import fs2.io.net.SocketMetrics
import fs2.io.net.SocketOption
import org.http4s.Http4sSuite
import org.typelevel.log4cats.noop.NoOpLogger
import scodec.bits.ByteVector

import java.nio.charset.StandardCharsets

private object H2ConnectionWriteSuiteHelpers {
  private val loopbackHost: Host = Host.fromString("localhost").get
  private val loopbackPort: Port = Port.fromInt(8080).get
  private val loopbackAddress: SocketAddress[Host] = SocketAddress(loopbackHost, loopbackPort)
  private val loopbackIp: SocketAddress[IpAddress] =
    SocketAddress(IpAddress.fromString("127.0.0.1").get, loopbackPort)

  private final class StubSocket(writeAction: Chunk[Byte] => IO[Unit]) extends Socket[IO] {
    override def address: GenSocketAddress = loopbackIp
    override def peerAddress: GenSocketAddress = loopbackIp
    override def supportedOptions: IO[Set[SocketOption.Key[?]]] = IO.pure(Set.empty)
    override def getOption[A](key: SocketOption.Key[A]): IO[Option[A]] = IO.pure(None)
    override def setOption[A](key: SocketOption.Key[A], value: A): IO[Unit] = IO.unit
    override def metrics: SocketMetrics =
      throw new NotImplementedError("metrics not used in tests")
    override def read(maxBytes: Int): IO[Option[Chunk[Byte]]] = IO.pure(None)
    override def readN(numBytes: Int): IO[Chunk[Byte]] = IO.pure(Chunk.empty)
    override def reads: Stream[IO, Byte] = Stream.empty
    override def endOfInput: IO[Unit] = IO.unit
    override def endOfOutput: IO[Unit] = IO.unit
    override def write(bytes: Chunk[Byte]): IO[Unit] = writeAction(bytes)
    override def writes: Pipe[IO, Byte, Nothing] = _.chunks.evalMap(write).drain
    override def sendFile(
        file: FileHandle[IO],
        offset: Long,
        count: Long,
        chunkSize: Int,
    ): Stream[IO, Nothing] =
      Stream.raiseError[IO](new NotImplementedError("sendFile is not used in tests"))
    override def isOpen: IO[Boolean] = IO.pure(true)
    override def localAddress: IO[SocketAddress[IpAddress]] = IO.pure(loopbackIp)
    override def remoteAddress: IO[SocketAddress[IpAddress]] = IO.pure(loopbackIp)
  }

  def mkConnection(writeAction: Chunk[Byte] => IO[Unit]): IO[H2Connection[IO]] = {
    val socket = new StubSocket(writeAction)
    val settings = H2Frame.Settings.ConnectionSettings.default

    for {
      mapRef <- Ref.of[IO, Map[Int, H2Stream[IO]]](Map.empty)
      stateRef <- H2Connection.initState[IO](
        settings,
        settings.initialWindowSize,
        settings.initialWindowSize,
      )
      outgoing <- Queue.unbounded[IO, Chunk[H2Frame]]
      created <- Queue.unbounded[IO, Int]
      closed <- Queue.unbounded[IO, Int]
      hpack <- Hpack.create[IO]
      settingsAck <- Deferred[IO, Either[Throwable, H2Frame.Settings.ConnectionSettings]]
    } yield new H2Connection[IO](
      Right(loopbackAddress),
      H2Connection.ConnectionType.Client,
      settings,
      mapRef,
      stateRef,
      outgoing,
      created,
      closed,
      hpack,
      Resource.unit[IO],
      settingsAck,
      ByteVector.empty,
      socket,
      NoOpLogger[IO],
    )
  }
}

class H2ConnectionWriteSuite extends Http4sSuite {
  import H2ConnectionWriteSuiteHelpers._

  test("writeWithClosedCheck converts ClosedChannelException to SocketException with cause") {
    val closed = new ClosedChannelException

    mkConnection(_ => IO.raiseError(closed))
      .flatMap(_.writeWithClosedCheck(Chunk.empty).attempt)
      .map {
        case Left(err: SocketException) =>
          assertEquals(err.getMessage, "Socket closed when attempting to write")
          assertEquals(err.getCause, closed)
        case Left(other) =>
          fail(s"Expected SocketException, but received $other")
        case Right(_) =>
          fail("Expected writeWithClosedCheck to fail when the channel is closed")
      }
  }

  test("writeWithClosedCheck propagates non-channel failures unchanged") {
    val boom = new IllegalStateException("boom")

    mkConnection(_ => IO.raiseError(boom))
      .flatMap(_.writeWithClosedCheck(Chunk.empty).attempt)
      .map {
        case Left(err) => assertEquals(err, boom)
        case Right(_) => fail("Expected the original failure to propagate")
      }
  }

  test("writeWithClosedCheck succeeds when the socket write completes") {
    val payload = Chunk.array("pong".getBytes(StandardCharsets.UTF_8))

    for {
      recorded <- Ref.of[IO, Option[Chunk[Byte]]](None)
      _ <- mkConnection(bytes => recorded.set(Some(bytes))).flatMap(_.writeWithClosedCheck(payload))
      seen <- recorded.get
    } yield assertEquals(seen.map(_.toList), Some(payload.toList))
  }
}
