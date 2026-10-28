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

package org.http4s.ember.client.internal

import cats.MonadThrow
import cats.effect.kernel.Resource
import cats.effect.kernel.Temporal
import cats.syntax.all._
import com.comcast.ip4s.Dns
import com.comcast.ip4s.Host
import com.comcast.ip4s.Hostname
import com.comcast.ip4s.IDN
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.Ipv4Address
import com.comcast.ip4s.Ipv6Address
import com.comcast.ip4s.Port
import com.comcast.ip4s.SocketAddress
import fs2.Chunk
import fs2.io.net.Network
import fs2.io.net.Socket
import fs2.io.net.SocketOption
import fs2.io.net.tls.TLSContext
import org.http4s.Uri
import org.http4s.ember.client.HttpProxy
import org.http4s.ember.client.Proxy
import org.http4s.ember.client.ProxyException
import org.http4s.ember.client.Socks4Proxy
import org.http4s.ember.client.Socks5Credentials
import org.http4s.ember.client.Socks5Proxy
import org.http4s.ember.core.Util

import java.nio.charset.StandardCharsets
import java.util.Arrays
import scala.concurrent.duration.Duration

private[client] object ProxyConnector {
  private val HttpHeaderTerminator = Array[Byte](13, 10, 13, 10)
  private val Socks5NoAuth = 0.toByte
  private val Socks5UsernamePassword = 2.toByte

  def connect[F[_]: Temporal: Network](
      proxy: Proxy,
      target: SocketAddress[Host],
      tlsContextOpt: Option[TLSContext[F]],
      enableEndpointValidation: Boolean,
      enableServerNameIndication: Boolean,
      additionalSocketOptions: List[SocketOption],
      maxResponseHeaderSize: Int,
      timeout: Duration,
  ): Resource[F, Socket[F]] = {
    implicit val dns: Dns[F] = Network[F].dns

    val tunnel = for {
      proxyAddress <- Resource.eval(address[F](proxy))
      _ <- Resource.eval(validate(proxy, tlsContextOpt, maxResponseHeaderSize))
      rawSocket <- Network[F].connect(proxyAddress, additionalSocketOptions)
      socket <- proxy match {
        case HttpProxy(Uri.Scheme.https, _, _, _) =>
          tlsContextOpt.fold[Resource[F, Socket[F]]](
            Resource.raiseError[F, Socket[F], Throwable](new ProxyException.MissingTlsContext)
          )(
            _.clientBuilder(rawSocket)
              .withParameters(
                Util.mkClientTLSParameters(
                  Some(proxyAddress),
                  enableEndpointValidation,
                  enableServerNameIndication,
                )
              )
              .build
              .widen[Socket[F]]
          )
        case _ => rawSocket.pure[Resource[F, *]]
      }
      _ <- Resource.eval(negotiate(proxy, socket, target, maxResponseHeaderSize))
    } yield socket

    timeoutAcquire(tunnel, timeout, new ProxyException.Timeout(proxy, timeout))
  }

  // Bounds connecting to the proxy, any TLS handshake with it, and the negotiation. Resource has
  // no timeout, so acquisition is bounded through allocated, which releases whatever it acquired
  // when cancelled.
  private def timeoutAcquire[F[_]: Temporal, A](
      resource: Resource[F, A],
      timeout: Duration,
      onTimeout: => Throwable,
  ): Resource[F, A] =
    Resource.applyFull[F, A] { poll =>
      poll(
        Util.timeoutToMaybe(
          resource.allocated,
          timeout,
          Temporal[F].raiseError[(A, F[Unit])](onTimeout),
        )
      ).map { case (a, release) => (a, (_: Resource.ExitCase) => release) }
    }

  private def address[F[_]: MonadThrow](proxy: Proxy): F[SocketAddress[Host]] =
    proxy match {
      case HttpProxy(scheme, host, port, _) =>
        if (scheme == Uri.Scheme.http || scheme == Uri.Scheme.https) {
          val defaultPort = if (scheme == Uri.Scheme.https) 443 else 80
          port
            .orElse(Port.fromInt(defaultPort))
            .map(SocketAddress(host, _))
            .liftTo[F](new ProxyException.UnsupportedScheme(scheme))
        } else new ProxyException.UnsupportedScheme(scheme).raiseError[F, SocketAddress[Host]]
      case Socks4Proxy(host, port, _) => SocketAddress(host, port).pure[F]
      case Socks5Proxy(host, port, _) => SocketAddress(host, port).pure[F]
    }

  private def validate[F[_]: MonadThrow](
      proxy: Proxy,
      tlsContextOpt: Option[TLSContext[F]],
      maxResponseHeaderSize: Int,
  ): F[Unit] =
    proxy match {
      case HttpProxy(Uri.Scheme.https, _, _, _) if tlsContextOpt.isEmpty =>
        (new ProxyException.MissingTlsContext).raiseError[F, Unit]
      case _: HttpProxy if maxResponseHeaderSize <= 0 =>
        new ProxyException.InvalidResponseHeaderSize(maxResponseHeaderSize).raiseError[F, Unit]
      case _ => ().pure[F]
    }

  private[internal] def negotiate[F[_]: MonadThrow: Dns](
      proxy: Proxy,
      socket: Socket[F],
      target: SocketAddress[Host],
      maxResponseHeaderSize: Int,
  ): F[Unit] =
    proxy match {
      case proxy: HttpProxy => httpConnect(socket, target, proxy, maxResponseHeaderSize)
      case proxy: Socks4Proxy => socks4Connect(socket, target, proxy)
      case proxy: Socks5Proxy => socks5Connect(socket, target, proxy)
    }

  private def httpConnect[F[_]: MonadThrow](
      socket: Socket[F],
      target: SocketAddress[Host],
      proxy: HttpProxy,
      maxResponseHeaderSize: Int,
  ): F[Unit] = {
    val authority = authorityForm(target)
    val authorization = proxy.credentials.fold("")(credentials =>
      s"Proxy-Authorization: Basic ${credentials.token}\r\n"
    )
    val request =
      s"CONNECT $authority HTTP/1.1\r\nHost: $authority\r\n$authorization\r\n"

    socket.write(Chunk.array(request.getBytes(StandardCharsets.ISO_8859_1))) *>
      readHttpConnectStatus(socket, maxResponseHeaderSize).flatMap { case (status, statusLine) =>
        if (status / 100 == 2) ().pure[F]
        else new ProxyException.HttpConnectFailure(status, statusLine).raiseError[F, Unit]
      }
  }

  private def readHttpConnectStatus[F[_]: MonadThrow](
      socket: Socket[F],
      maxResponseHeaderSize: Int,
  ): F[(Int, String)] =
    if (maxResponseHeaderSize <= 0)
      new ProxyException.InvalidResponseHeaderSize(maxResponseHeaderSize)
        .raiseError[F, (Int, String)]
    else {
      val buffer = new Array[Byte](maxResponseHeaderSize)

      def go(offset: Int, matched: Int): F[Array[Byte]] =
        if (offset >= maxResponseHeaderSize)
          new ProxyException.ResponseTooLarge(maxResponseHeaderSize).raiseError[F, Array[Byte]]
        else
          socket.read(1).flatMap {
            case Some(chunk) if chunk.nonEmpty =>
              val byte = chunk(0)
              buffer(offset) = byte
              val nextMatched =
                if (byte == HttpHeaderTerminator(matched)) matched + 1
                else if (byte == HttpHeaderTerminator(0)) 1
                else 0
              if (nextMatched == HttpHeaderTerminator.length)
                Arrays.copyOf(buffer, offset + 1).pure[F]
              else go(offset + 1, nextMatched)
            case Some(_) => go(offset, matched)
            case None =>
              new ProxyException.UnexpectedEof("HTTP CONNECT response").raiseError[F, Array[Byte]]
          }

      go(0, 0).flatMap { bytes =>
        val firstLineEnd =
          bytes.indices.dropRight(1).find(i => bytes(i) == 13 && bytes(i + 1) == 10)
        firstLineEnd
          .map(end => new String(bytes, 0, end, StandardCharsets.ISO_8859_1))
          .flatMap { line =>
            line.split(" ", 3).toList match {
              case version :: status :: _ if version.startsWith("HTTP/") =>
                Either
                  .catchOnly[NumberFormatException](status.toInt)
                  .toOption
                  .map(status => (status, line))
              case _ => None
            }
          }
          .liftTo[F](new ProxyException.InvalidHttpResponse)
      }
    }

  private def socks4Connect[F[_]: MonadThrow: Dns](
      socket: Socket[F],
      target: SocketAddress[Host],
      proxy: Socks4Proxy,
  ): F[Unit] = {
    val username = proxy.username.getOrElse("").getBytes(StandardCharsets.UTF_8)
    if (username.contains(0.toByte))
      new ProxyException.InvalidCredentials("SOCKS4 user ID contains a NUL byte")
        .raiseError[F, Unit]
    else
      resolveIpv4(target.host).flatMap { ipv4 =>
        val request =
          Array[Byte](4, 1) ++ portBytes(target.port) ++ ipv4.toBytes ++ username ++ Array[Byte](0)
        socket.write(Chunk.array(request)) *>
          readExactly(socket, 8, "SOCKS4 response").flatMap { response =>
            val status = response(1) & 0xff
            if (response(0) != 0) (new ProxyException.InvalidSocks4Response).raiseError[F, Unit]
            else if (status == 90) ().pure[F]
            else new ProxyException.Socks4ConnectFailure(status).raiseError[F, Unit]
          }
      }
  }

  // SOCKS4 only understands IPv4 addresses, so the target has to be resolved client side.
  private def resolveIpv4[F[_]: MonadThrow: Dns](host: Host): F[Ipv4Address] =
    host
      .resolveAll[F]
      .flatMap(
        _.collectFirst { case address: Ipv4Address => address }
          .liftTo[F](new ProxyException.MissingSocks4Address(host))
      )

  private def socks5Connect[F[_]: MonadThrow](
      socket: Socket[F],
      target: SocketAddress[Host],
      proxy: Socks5Proxy,
  ): F[Unit] = {
    val methods =
      proxy.credentials.fold(Array(Socks5NoAuth))(_ => Array(Socks5NoAuth, Socks5UsernamePassword))

    for {
      _ <- socket.write(Chunk.array(Array[Byte](5, methods.length.toByte) ++ methods))
      selection <- readExactly(socket, 2, "SOCKS5 method selection")
      _ <-
        if (selection(0) != 5) (new ProxyException.InvalidSocks5Response).raiseError[F, Unit]
        else ().pure[F]
      _ <- selection(1) match {
        // The proxy decides whether to authenticate, so configured credentials go unused when it
        // selects no authentication.
        case Socks5NoAuth => ().pure[F]
        case Socks5UsernamePassword =>
          proxy.credentials.fold(
            new ProxyException.UnexpectedSocks5AuthMethod(Socks5UsernamePassword.toInt)
              .raiseError[F, Unit]
          )(socks5Authenticate(socket, _))
        case method if method == 0xff.toByte =>
          (new ProxyException.Socks5NoAcceptableAuthMethod).raiseError[F, Unit]
        case method =>
          new ProxyException.UnexpectedSocks5AuthMethod(method & 0xff).raiseError[F, Unit]
      }
      address <- socks5Address[F](target.host)
      _ <- socket.write(Chunk.array(Array[Byte](5, 1, 0) ++ address ++ portBytes(target.port)))
      response <- readExactly(socket, 4, "SOCKS5 CONNECT response")
      _ <-
        if (response(0) != 5 || response(2) != 0)
          (new ProxyException.InvalidSocks5Response).raiseError[F, Unit]
        else if (response(1) != 0)
          new ProxyException.Socks5ConnectFailure(response(1) & 0xff).raiseError[F, Unit]
        else ().pure[F]
      addressLength <- response(3) match {
        case 1 => 4.pure[F]
        case 4 => 16.pure[F]
        case 3 => readExactly(socket, 1, "SOCKS5 bound address length").map(b => b(0) & 0xff)
        case _ => (new ProxyException.InvalidSocks5Response).raiseError[F, Int]
      }
      _ <- readExactly(socket, addressLength + 2, "SOCKS5 bound address and port")
    } yield ()
  }

  private def socks5Authenticate[F[_]: MonadThrow](
      socket: Socket[F],
      credentials: Socks5Credentials,
  ): F[Unit] = {
    val username = credentials.username.getBytes(StandardCharsets.UTF_8)
    val password = credentials.password.getBytes(StandardCharsets.UTF_8)

    if (username.isEmpty || password.isEmpty || username.length > 255 || password.length > 255)
      new ProxyException.InvalidCredentials(
        "SOCKS5 username and password must contain between 1 and 255 bytes"
      )
        .raiseError[F, Unit]
    else {
      val request =
        Array[Byte](1, username.length.toByte) ++ username ++
          Array[Byte](password.length.toByte) ++ password
      socket.write(Chunk.array(request)) *>
        readExactly(socket, 2, "SOCKS5 authentication response").flatMap { response =>
          if (response(0) == 1 && response(1) == 0) ().pure[F]
          else (new ProxyException.Socks5AuthenticationFailure).raiseError[F, Unit]
        }
    }
  }

  // Prefers the domain name address type so the proxy resolves the host. RFC 1928, section 5.
  private def socks5Address[F[_]: MonadThrow](host: Host): F[Array[Byte]] =
    host match {
      case ipv4: Ipv4Address => (Array[Byte](1) ++ ipv4.toBytes).pure[F]
      case ipv6: Ipv6Address => (Array[Byte](4) ++ ipv6.toBytes).pure[F]
      case host =>
        val name = asciiHost(host).getBytes(StandardCharsets.US_ASCII)
        if (name.isEmpty || name.length > 255)
          new ProxyException.InvalidSocks5Target(host).raiseError[F, Array[Byte]]
        else (Array[Byte](3, name.length.toByte) ++ name).pure[F]
    }

  // The authority form required by CONNECT. RFC 9110, section 9.3.6.
  private def authorityForm(target: SocketAddress[Host]): String =
    target.host match {
      case _: Ipv6Address => s"[${asciiHost(target.host)}]:${target.port}"
      case host => s"${asciiHost(host)}:${target.port}"
    }

  private def asciiHost(host: Host): String =
    host match {
      case hostname: Hostname => hostname.normalized.toString
      case idn: IDN => idn.hostname.normalized.toString
      case address: IpAddress => address.toString
    }

  private def readExactly[F[_]: MonadThrow](
      socket: Socket[F],
      size: Int,
      context: String,
  ): F[Array[Byte]] =
    socket.readN(size).flatMap { chunk =>
      if (chunk.size == size) chunk.toArray.pure[F]
      else new ProxyException.UnexpectedEof(context).raiseError[F, Array[Byte]]
    }

  private def portBytes(port: Port): Array[Byte] = {
    val value = port.value
    Array((value >>> 8).toByte, value.toByte)
  }
}
