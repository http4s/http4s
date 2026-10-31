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

package org.http4s.ember.client

import cats.effect.IO
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import fs2.io.net.Network
import fs2.io.net.Socket
import org.http4s._
import org.http4s.implicits._

import java.nio.charset.StandardCharsets

/** Asserts on the bytes Ember writes to a proxy, against a socket that speaks the proxy side of
  * each protocol by hand.
  */
private final case class Socks5Request(addressType: Int, target: String, port: Int)

class ProxyProtocolSuite extends Http4sSuite {

  private val origin: Uri = uri"http://origin.example:8080/"
  private val ipOrigin: Uri = uri"http://127.0.0.1:8080/"

  private val credentials: BasicCredentials = BasicCredentials("user", "pass")

  private val okResponse: String = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok"
  private val connectEstablished: String = "HTTP/1.1 200 Connection established\r\n\r\n"

  // Version 5, succeeded, reserved, IPv4, 0.0.0.0, port 0. RFC 1928, section 6.
  private val socks5Succeeded: Array[Byte] = Array[Byte](5, 0, 0, 1, 0, 0, 0, 0, 0, 0)

  // Null, request granted, then the ignored port and address. SOCKS4 reply.
  private val socks4Granted: Array[Byte] = Array[Byte](0, 90, 0, 0, 0, 0, 0, 0)

  private def httpProxy(proxyPort: Port): HttpProxy =
    HttpProxy(Uri.Scheme.http, host"127.0.0.1", proxyPort.some)

  private def write(socket: Socket[IO], bytes: Array[Byte]): IO[Unit] =
    socket.write(Chunk.array(bytes))

  private def writeAscii(socket: Socket[IO], value: String): IO[Unit] =
    write(socket, value.getBytes(StandardCharsets.ISO_8859_1))

  /** Reads exactly `size` bytes. */
  private def readExactly(socket: Socket[IO], size: Int): IO[Array[Byte]] = {
    def go(acc: Array[Byte]): IO[Array[Byte]] =
      if (acc.length >= size) acc.pure[IO]
      else
        socket.read(size - acc.length).flatMap {
          case Some(chunk) if chunk.nonEmpty => go(acc ++ chunk.toArray[Byte])
          case _ =>
            IO.raiseError(
              new IllegalStateException(s"End of input after ${acc.length} of $size bytes")
            )
        }

    go(Array.emptyByteArray)
  }

  private def readAscii(socket: Socket[IO], size: Int): IO[String] =
    readExactly(socket, size).map(new String(_, StandardCharsets.ISO_8859_1))

  /** Reads an HTTP message prelude, up to and including the blank line. */
  private def readPrelude(socket: Socket[IO]): IO[String] = {
    def go(acc: String): IO[String] =
      if (acc.endsWith("\r\n\r\n")) acc.pure[IO]
      else readExactly(socket, 1).flatMap(byte => go(acc + (byte(0) & 0xff).toChar))

    go("")
  }

  /** Reads the entity following a prelude, sized by its `Content-Length`. */
  private def readEntity(socket: Socket[IO], prelude: String): IO[String] =
    prelude.linesIterator
      .map(_.toLowerCase)
      .collectFirst { case line if line.startsWith("content-length:") => line }
      .flatMap(line =>
        Either
          .catchOnly[NumberFormatException](line.dropWhile(_ != ':').drop(1).trim.toInt)
          .toOption
      )
      .fold("".pure[IO])(readAscii(socket, _))

  private def readUntilNul(socket: Socket[IO]): IO[String] = {
    def go(acc: List[Byte]): IO[String] =
      readExactly(socket, 1).flatMap { byte =>
        if (byte(0) == 0) new String(acc.reverse.toArray, StandardCharsets.ISO_8859_1).pure[IO]
        else go(byte(0) :: acc)
      }

    go(Nil)
  }

  /** Reads a SOCKS5 request whose target is a domain name. */
  private def readSocks5Request(socket: Socket[IO]): IO[Socks5Request] =
    for {
      header <- readExactly(socket, 5) // version, command, reserved, address type, name length
      body <- readExactly(socket, (header(4) & 0xff) + 2)
      target = new String(body.dropRight(2), StandardCharsets.US_ASCII)
      port = ((body(body.length - 2) & 0xff) << 8) | (body(body.length - 1) & 0xff)
    } yield {
      assertEquals(header(0).toInt, 5, "SOCKS5 version")
      assertEquals(header(1).toInt, 1, "SOCKS5 CONNECT command")
      Socks5Request(header(3) & 0xff, target, port)
    }

  /** Runs `proxy` against the socket Ember connects to while `request` runs against the bound
    * port, and returns what the proxy observed alongside the client's outcome.
    */
  private def exchange[A](
      proxy: Socket[IO] => IO[A]
  )(request: Port => IO[String]): IO[(A, Either[Throwable, String])] =
    Network[IO].bind(SocketAddress(ip"127.0.0.1", port"0")).use { server =>
      server.accept.head
        .evalMap(proxy)
        .compile
        .lastOrError
        .both(request(server.address.asIpUnsafe.port).attempt)
    }

  private def through(
      proxy: Proxy,
      request: Request[IO],
      forwarding: Boolean = true,
      ignoredHosts: IgnoredHosts = IgnoredHosts.default,
  ): IO[String] = {
    val builder =
      EmberClientBuilder.default[IO].withProxy(proxy).withProxyIgnoredHosts(ignoredHosts)
    (if (forwarding) builder else builder.withoutProxyForwarding).build
      .use(_.expect[String](request))
  }

  test("forwards an http request to an HttpProxy in absolute form") {
    exchange(socket => readPrelude(socket).flatTap(_ => writeAscii(socket, okResponse))) {
      proxyPort => through(httpProxy(proxyPort), Request[IO](uri = origin))
    }.map { case (prelude, response) =>
      assert(prelude.startsWith("GET http://origin.example:8080/ HTTP/1.1\r\n"), prelude)
      assert(prelude.contains("Host: origin.example:8080\r\n"), prelude)
      assertEquals(response, Right("ok"))
    }
  }

  test("forwards an entity to an HttpProxy") {
    exchange { socket =>
      for {
        prelude <- readPrelude(socket)
        entity <- readEntity(socket, prelude)
        _ <- writeAscii(socket, okResponse)
      } yield (prelude, entity)
    }(proxyPort =>
      through(httpProxy(proxyPort), Request[IO](Method.POST, origin).withEntity("payload"))
    ).map { case ((prelude, entity), response) =>
      assert(prelude.startsWith("POST http://origin.example:8080/ HTTP/1.1\r\n"), prelude)
      assertEquals(entity, "payload")
      assertEquals(response, Right("ok"))
    }
  }

  test("sends Proxy-Authorization with a forwarded request") {
    exchange(socket => readPrelude(socket).flatTap(_ => writeAscii(socket, okResponse))) {
      proxyPort =>
        through(
          httpProxy(proxyPort).copy(credentials = credentials.some),
          Request[IO](uri = origin),
        )
    }.map { case (prelude, response) =>
      assert(prelude.startsWith("GET http://origin.example:8080/ HTTP/1.1\r\n"), prelude)
      assert(prelude.contains(s"Proxy-Authorization: Basic ${credentials.token}\r\n"), prelude)
      assertEquals(response, Right("ok"))
    }
  }

  test("tunnels an http request through an HttpProxy when forwarding is disabled") {
    exchange { socket =>
      for {
        connect <- readPrelude(socket)
        _ <- writeAscii(socket, connectEstablished)
        request <- readPrelude(socket)
        _ <- writeAscii(socket, okResponse)
      } yield (connect, request)
    }(proxyPort => through(httpProxy(proxyPort), Request[IO](uri = origin), forwarding = false))
      .map { case ((connect, request), response) =>
        assert(connect.startsWith("CONNECT origin.example:8080 HTTP/1.1\r\n"), connect)
        assert(connect.contains("Host: origin.example:8080\r\n"), connect)
        assert(request.startsWith("GET / HTTP/1.1\r\n"), request)
        assertEquals(response, Right("ok"))
      }
  }

  test("sends Proxy-Authorization with CONNECT") {
    exchange { socket =>
      readPrelude(socket).flatTap { _ =>
        writeAscii(socket, connectEstablished) *>
          readPrelude(socket) *>
          writeAscii(socket, okResponse)
      }
    } { proxyPort =>
      through(
        httpProxy(proxyPort).copy(credentials = credentials.some),
        Request[IO](uri = origin),
        forwarding = false,
      )
    }.map { case (connect, _) =>
      assert(connect.contains(s"Proxy-Authorization: Basic ${credentials.token}\r\n"), connect)
    }
  }

  test("offers only no-authentication to a Socks5Proxy without credentials") {
    exchange { socket =>
      for {
        greeting <- readExactly(socket, 3)
        _ <- write(socket, Array[Byte](5, 0))
        request <- readSocks5Request(socket)
        _ <- write(socket, socks5Succeeded)
        _ <- readPrelude(socket)
        _ <- writeAscii(socket, okResponse)
      } yield (greeting.toList, request)
    }(proxyPort => through(Socks5Proxy(host"127.0.0.1", proxyPort), Request[IO](uri = origin)))
      .map { case ((greeting, request), response) =>
        assertEquals(greeting, List[Byte](5, 1, 0))
        assertEquals(
          request,
          Socks5Request(addressType = 3, target = "origin.example", port = 8080),
        )
        assertEquals(response, Right("ok"))
      }
  }

  test("negotiates username and password with a Socks5Proxy") {
    exchange { socket =>
      for {
        greeting <- readExactly(socket, 4)
        _ <- write(socket, Array[Byte](5, 2))
        header <- readExactly(socket, 2) // subnegotiation version, username length
        username <- readAscii(socket, header(1) & 0xff)
        length <- readExactly(socket, 1)
        password <- readAscii(socket, length(0) & 0xff)
        _ <- write(socket, Array[Byte](1, 0))
        _ <- readSocks5Request(socket)
        _ <- write(socket, socks5Succeeded)
        _ <- readPrelude(socket)
        _ <- writeAscii(socket, okResponse)
      } yield (greeting.toList, header(0).toInt, username, password)
    } { proxyPort =>
      val proxy =
        Socks5Proxy(host"127.0.0.1", proxyPort, Socks5Credentials("user", "pass").some)
      through(proxy, Request[IO](uri = origin))
    }.map { case ((greeting, version, username, password), response) =>
      assertEquals(greeting, List[Byte](5, 2, 0, 2))
      assertEquals(version, 1)
      assertEquals(username, "user")
      assertEquals(password, "pass")
      assertEquals(response, Right("ok"))
    }
  }

  test("reaches an ignored host directly") {
    // Nothing listens on port 1, so were the host not ignored the client would fail there rather
    // than reach the origin bound below.
    val proxy = HttpProxy(Uri.Scheme.http, host"127.0.0.1", port"1".some, credentials.some)

    Network[IO]
      .bind(SocketAddress(ip"127.0.0.1", port"0"))
      .use { server =>
        val origin = Uri.unsafeFromString(s"http://127.0.0.1:${server.address.asIpUnsafe.port}/")

        for {
          recorded <- IO.ref(Option.empty[String])
          serve = server.accept.head
            .evalMap(socket =>
              readPrelude(socket)
                .flatMap(prelude => recorded.set(prelude.some) *> writeAscii(socket, okResponse))
            )
            .compile
            .drain
          response <- serve.background.surround(through(proxy, Request[IO](uri = origin)).attempt)
          prelude <- recorded.get
        } yield (prelude, response)
      }
      .map { case (prelude, response) =>
        assertEquals(response, Right("ok"))
        // Origin form, and no credentials, because the proxy was never involved.
        assert(prelude.exists(_.startsWith("GET / HTTP/1.1\r\n")), prelude.toString)
        assert(!prelude.exists(_.contains("Proxy-Authorization")), prelude.toString)
      }
  }

  test("sends the target address and user id to a Socks4Proxy") {
    exchange { socket =>
      for {
        header <- readExactly(socket, 8) // version, command, port, address
        userId <- readUntilNul(socket)
        _ <- write(socket, socks4Granted)
        _ <- readPrelude(socket)
        _ <- writeAscii(socket, okResponse)
      } yield (header.toList, userId)
    } { proxyPort =>
      through(
        Socks4Proxy(host"127.0.0.1", proxyPort, "socksuser".some),
        Request[IO](uri = ipOrigin),
        ignoredHosts = IgnoredHosts.none,
      )
    }.map { case ((header, userId), response) =>
      assertEquals(header, List[Byte](4, 1, 31, -112, 127, 0, 0, 1))
      assertEquals(userId, "socksuser")
      assertEquals(response, Right("ok"))
    }
  }
}
