package org.http4s.ember.server.internal

import cats.effect.IO
import cats.syntax.all._
import com.comcast.ip4s.{UnixSocketAddress => IpUnixSocketAddress}
import fs2.Chunk
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.io.net.Network
import fs2.io.net.unixsocket.{UnixSocketAddress => Fs2UnixSocketAddress}
import org.http4s.Http4sSuite
import org.http4s.HttpApp
import org.http4s.Response
import org.http4s.Status
import org.http4s.ember.server.EmberServerBuilder

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._

class ServerHelpersNetworkUnixSocketSuite extends Http4sSuite {

  private val httpApp: HttpApp[IO] = HttpApp.liftF(
    Response[IO](Status.Ok).withEntity("pong").pure[IO]
  )

  private def skipIfUnsupported[A](ioa: IO[A]): IO[A] =
    ioa.handleErrorWith {
      case _: UnsupportedOperationException =>
        IO.defer {
          assume(false, "Unix sockets not supported")
          IO.never
        }
      case other => IO.raiseError(other)
    }

  private def waitFor(condition: IO[Boolean], remaining: Int): IO[Unit] =
    condition.flatMap {
      case true => IO.unit
      case false if remaining > 0 => IO.sleep(50.millis) >> waitFor(condition, remaining - 1)
      case false => IO(fail("condition was not met in time"))
    }

  test("unixSocketServer accepts unix domain socket connections") {
    val program = Files[IO].tempFile(None, "", "sock", None).use { path =>
      val ip4sAddress = IpUnixSocketAddress(path.toString)
      val builderAddress = Fs2UnixSocketAddress(path.toString)
      val socketPath: Path = path

      Files[IO].deleteIfExists(path) >>
        EmberServerBuilder
          .default[IO]
          .withUnixSocketConfig(builderAddress)
          .withHttpApp(httpApp)
          .build
          .use { _ =>
            val requestBytes =
              "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(
                StandardCharsets.US_ASCII
              )

            def connectAndRead: IO[Unit] =
              Network[IO].connect(ip4sAddress).use { socket =>
                socket.write(Chunk.array(requestBytes)) >>
                  socket.endOfOutput >>
                  socket.reads
                    .through(fs2.text.utf8.decode)
                    .compile
                    .string
                    .map { responseText =>
                      assert(responseText.contains("200 OK"))
                      assert(responseText.contains("pong"))
                    }
              }

            def connectWithRetry(remaining: Int): IO[Unit] =
              connectAndRead.handleErrorWith {
                case _: fs2.io.net.SocketException if remaining > 0 =>
                  IO.sleep(50.millis) >> connectWithRetry(remaining - 1)
                case other => IO.raiseError(other)
              }

            waitFor(Files[IO].exists(socketPath), remaining = 40) >>
              connectWithRetry(remaining = 20)
          }
    }

    skipIfUnsupported(program)
  }

  test("unixSocketServer deletes socket file when deleteOnClose is true") {
    val program = Files[IO].tempFile(None, "", "sock", None).use { path =>
      val builderAddress = Fs2UnixSocketAddress(path.toString)
      val socketPath: Path = path

      val server = EmberServerBuilder
        .default[IO]
        .withUnixSocketConfig(builderAddress, deleteIfExists = true, deleteOnClose = true)
        .withHttpApp(httpApp)
        .build

      Files[IO].deleteIfExists(path) >>
        server.use { _ =>
          waitFor(Files[IO].exists(socketPath), remaining = 40) >>
            Files[IO].exists(socketPath).flatMap(exists => IO(assert(exists)))
        } >>
        Files[IO].exists(socketPath).flatMap(exists => IO(assert(!exists)))
    }

    skipIfUnsupported(program)
  }
}
