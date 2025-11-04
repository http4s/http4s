package org.http4s.ember.client.internal

import cats.effect.IO
import com.comcast.ip4s.{UnixSocketAddress => IpUnixSocketAddress}
import fs2.Chunk
import fs2.io.file.Files
import fs2.io.net.Network
import fs2.io.net.SocketOption
import fs2.io.net.unixsocket.UnixSocketAddress
import org.http4s.Http4sSuite
import org.http4s.Request
import org.http4s.syntax.literals._

import java.nio.charset.StandardCharsets

class ClientHelpersUnixSocketSuite extends Http4sSuite {

  private def skipIfUnsupported[A](ioa: IO[A]): IO[A] =
    ioa.handleErrorWith {
      case _: UnsupportedOperationException =>
        IO.defer {
          assume(false, "Unix sockets not supported")
          IO.never
        }
      case other => IO.raiseError(other)
    }

  test("unixSocket connects to a running unix domain socket server") {
    val program = Files[IO].tempFile(None, "", "sock", None).use { path =>
      val message = "pong"
      val fs2Address = UnixSocketAddress(path.toString)
      val ip4sAddress = IpUnixSocketAddress(path.toString)
      val options = List(
        SocketOption.unixSocketDeleteIfExists(true),
        SocketOption.unixSocketDeleteOnClose(true),
      )

      Network[IO].bind(ip4sAddress, options).use { serverSocket =>
        serverSocket.accept
          .evalMap { socket =>
            socket.write(Chunk.array(message.getBytes(StandardCharsets.UTF_8))) >>
              socket.endOfOutput
          }
          .compile
          .drain
          .background
          .use { _ =>
            val request = Request[IO](uri = uri"http://localhost")

            ClientHelpers
              .unixSocket[IO](
                request,
                fs2Address,
                tlsContextOpt = None,
                enableEndpointValidation = false,
                enableServerNameIndication = false,
                additionalSocketOptions = Nil,
              )
              .use { requestKeySocket =>
                requestKeySocket.socket.read(1024).flatMap {
                  case Some(chunk) =>
                    val received = new String(chunk.toArray, StandardCharsets.UTF_8)
                    IO(assertEquals(received, message))
                  case None => IO(fail("expected response bytes"))
                }
              }
          }
      }
    }

    skipIfUnsupported(program)
  }
}
