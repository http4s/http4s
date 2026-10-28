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
import cats.syntax.all._
import com.comcast.ip4s.Dns
import com.comcast.ip4s.Host
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
import org.http4s.ember.client.Socks4Proxy
import org.http4s.ember.client.Socks5Credentials
import org.http4s.ember.client.Socks5Proxy
import org.http4s.ember.core.Util

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Arrays

private[client] object ProxyConnector {
  private val HttpHeaderTerminator = Array[Byte](13, 10, 13, 10)

  def connect[F[_]: MonadThrow: Network](
      proxy: Proxy,
      target: SocketAddress[Host],
      tlsContextOpt: Option[TLSContext[F]],
      enableEndpointValidation: Boolean,
      enableServerNameIndication: Boolean,
      additionalSocketOptions: List[SocketOption],
      maxResponseHeaderSize: Int,
  ): Resource[F, Socket[F]] =
    for {
      proxyAddress <- Resource.eval(address[F](proxy))
      _ <- Resource.eval(validate(proxy, tlsContextOpt, maxResponseHeaderSize))
      resolvedTarget <- Resource.eval(resolve(proxy, target))
      rawSocket <- Network[F].connect(proxyAddress, additionalSocketOptions)
      socket <- proxy match {
        case HttpProxy(Uri.Scheme.https, _, _, _) =>
          tlsContextOpt.fold[Resource[F, Socket[F]]](
            Resource.raiseError[F, Socket[F], Throwable](MissingProxyTlsContext())
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
      _ <- Resource.eval(negotiate(proxy, socket, resolvedTarget, maxResponseHeaderSize))
    } yield socket

  private def address[F[_]: MonadThrow](proxy: Proxy): F[SocketAddress[Host]] =
    proxy match {
      case HttpProxy(scheme, host, port, _) =>
        if (scheme === Uri.Scheme.http || scheme === Uri.Scheme.https) {
          val defaultPort = if (scheme === Uri.Scheme.https) 443 else 80
          port
            .orElse(Port.fromInt(defaultPort))
            .map(SocketAddress(host, _))
            .liftTo[F](UnsupportedHttpProxyScheme(scheme))
        } else UnsupportedHttpProxyScheme(scheme).raiseError[F, SocketAddress[Host]]
      case Socks4Proxy(host, port, _) => SocketAddress(host, port).pure[F]
      case Socks5Proxy(host, port, _) => SocketAddress(host, port).pure[F]
    }

  private def resolve[F[_]: MonadThrow: Network](
      proxy: Proxy,
      target: SocketAddress[Host],
  ): F[SocketAddress[IpAddress]] = {
    implicit val dns: Dns[F] = Network[F].dns
    val address = proxy match {
      case _: Socks4Proxy =>
        target.host
          .resolveAll[F]
          .flatMap(
            _.collectFirst { case address: Ipv4Address => address }
              .liftTo[F](MissingSocks4Address(target.host))
          )
      case _ => target.host.resolve[F]
    }
    address.map(SocketAddress(_, target.port))
  }

  private def validate[F[_]: MonadThrow](
      proxy: Proxy,
      tlsContextOpt: Option[TLSContext[F]],
      maxResponseHeaderSize: Int,
  ): F[Unit] =
    proxy match {
      case HttpProxy(Uri.Scheme.https, _, _, _) if tlsContextOpt.isEmpty =>
        MissingProxyTlsContext().raiseError[F, Unit]
      case _: HttpProxy if maxResponseHeaderSize <= 0 =>
        InvalidProxyResponseHeaderSize(maxResponseHeaderSize).raiseError[F, Unit]
      case _ => ().pure[F]
    }

  private[internal] def negotiate[F[_]: MonadThrow](
      proxy: Proxy,
      socket: Socket[F],
      target: SocketAddress[IpAddress],
      maxResponseHeaderSize: Int,
  ): F[Unit] =
    proxy match {
      case proxy: HttpProxy => httpConnect(socket, target, proxy, maxResponseHeaderSize)
      case proxy: Socks4Proxy => socks4Connect(socket, target, proxy)
      case proxy: Socks5Proxy => socks5Connect(socket, target, proxy)
    }

  private def httpConnect[F[_]: MonadThrow](
      socket: Socket[F],
      target: SocketAddress[IpAddress],
      proxy: HttpProxy,
      maxResponseHeaderSize: Int,
  ): F[Unit] = {
    val authority = target.toString
    val authorization = proxy.credentials.fold("")(credentials =>
      s"Proxy-Authorization: Basic ${credentials.token}\r\n"
    )
    val request =
      s"CONNECT $authority HTTP/1.1\r\nHost: $authority\r\n$authorization\r\n"

    socket.write(Chunk.array(request.getBytes(StandardCharsets.ISO_8859_1))) *>
      readHttpResponseStatus(socket, maxResponseHeaderSize).flatMap { status =>
        if (status / 100 === 2) ().pure[F]
        else ProxyConnectFailure(status).raiseError[F, Unit]
      }
  }

  private def readHttpResponseStatus[F[_]: MonadThrow](
      socket: Socket[F],
      maxResponseHeaderSize: Int,
  ): F[Int] =
    if (maxResponseHeaderSize <= 0)
      InvalidProxyResponseHeaderSize(maxResponseHeaderSize).raiseError[F, Int]
    else {
      val buffer = new Array[Byte](maxResponseHeaderSize)

      def go(offset: Int, matched: Int): F[Array[Byte]] =
        if (offset >= maxResponseHeaderSize)
          ProxyResponseTooLarge(maxResponseHeaderSize).raiseError[F, Array[Byte]]
        else
          socket.read(1).flatMap {
            case Some(chunk) if chunk.nonEmpty =>
              val byte = chunk(0)
              buffer(offset) = byte
              val nextMatched =
                if (byte === HttpHeaderTerminator(matched)) matched + 1
                else if (byte === HttpHeaderTerminator(0)) 1
                else 0
              if (nextMatched === HttpHeaderTerminator.length)
                Arrays.copyOf(buffer, offset + 1).pure[F]
              else go(offset + 1, nextMatched)
            case Some(_) => go(offset, matched)
            case None => UnexpectedProxyEof("HTTP CONNECT response").raiseError[F, Array[Byte]]
          }

      go(0, 0).flatMap { bytes =>
        val firstLineEnd =
          bytes.indices.dropRight(1).find(i => bytes(i) === 13 && bytes(i + 1) === 10)
        firstLineEnd
          .map(end => new String(bytes, 0, end, StandardCharsets.ISO_8859_1))
          .flatMap { line =>
            line.split(" ", 3).toList match {
              case version :: status :: _ if version.startsWith("HTTP/") =>
                Either.catchOnly[NumberFormatException](status.toInt).toOption
              case _ => None
            }
          }
          .liftTo[F](InvalidHttpProxyResponse())
      }
    }

  private def socks4Connect[F[_]: MonadThrow](
      socket: Socket[F],
      target: SocketAddress[IpAddress],
      proxy: Socks4Proxy,
  ): F[Unit] =
    target.host match {
      case ipv4: Ipv4Address =>
        val username = proxy.username.getOrElse("").getBytes(StandardCharsets.UTF_8)
        if (username.contains(0.toByte))
          InvalidProxyCredentials("SOCKS4 user ID contains a NUL byte").raiseError[F, Unit]
        else {
          val request =
            Array[Byte](4, 1) ++ portBytes(target.port) ++ ipv4.toBytes ++ username ++ Array[Byte](
              0
            )
          socket.write(Chunk.array(request)) *>
            readExactly(socket, 8, "SOCKS4 response").flatMap { response =>
              val status = unsigned(response(1))
              if (response(0) =!= 0) InvalidSocks4Response().raiseError[F, Unit]
              else if (status === 90) ().pure[F]
              else Socks4ConnectFailure(status).raiseError[F, Unit]
            }
        }
      case _: Ipv6Address =>
        MissingSocks4Address(target.host).raiseError[F, Unit]
    }

  private def socks5Connect[F[_]: MonadThrow](
      socket: Socket[F],
      target: SocketAddress[IpAddress],
      proxy: Socks5Proxy,
  ): F[Unit] = {
    val method = proxy.credentials.fold(0)(Function.const(2)).toByte

    for {
      _ <- socket.write(Chunk.array(Array[Byte](5, 1, method)))
      selection <- readExactly(socket, 2, "SOCKS5 method selection")
      _ <-
        if (selection(0) =!= 5) InvalidSocks5Response().raiseError[F, Unit]
        else if (selection(1) === 0xff.toByte) Socks5NoAcceptableAuthMethod().raiseError[F, Unit]
        else if (selection(1) =!= method)
          UnexpectedSocks5AuthMethod(unsigned(selection(1))).raiseError[F, Unit]
        else ().pure[F]
      _ <- proxy.credentials.traverse_(socks5Authenticate(socket, _))
      address = target.host match {
        case ipv4: Ipv4Address => Array[Byte](1) ++ ipv4.toBytes
        case ipv6: Ipv6Address => Array[Byte](4) ++ ipv6.toBytes
      }
      _ <- socket.write(Chunk.array(Array[Byte](5, 1, 0) ++ address ++ portBytes(target.port)))
      response <- readExactly(socket, 4, "SOCKS5 CONNECT response")
      _ <-
        if (response(0) =!= 5 || response(2) =!= 0)
          InvalidSocks5Response().raiseError[F, Unit]
        else if (response(1) =!= 0)
          Socks5ConnectFailure(unsigned(response(1))).raiseError[F, Unit]
        else ().pure[F]
      addressLength <- response(3) match {
        case 1 => 4.pure[F]
        case 4 => 16.pure[F]
        case 3 => readExactly(socket, 1, "SOCKS5 bound address length").map(b => unsigned(b(0)))
        case _ => InvalidSocks5Response().raiseError[F, Int]
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
      InvalidProxyCredentials("SOCKS5 username and password must contain between 1 and 255 bytes")
        .raiseError[F, Unit]
    else {
      val request =
        Array[Byte](1, username.length.toByte) ++ username ++
          Array[Byte](password.length.toByte) ++ password
      socket.write(Chunk.array(request)) *>
        readExactly(socket, 2, "SOCKS5 authentication response").flatMap { response =>
          if (response(0) === 1 && response(1) === 0) ().pure[F]
          else Socks5AuthenticationFailure().raiseError[F, Unit]
        }
    }
  }

  private def readExactly[F[_]: MonadThrow](
      socket: Socket[F],
      size: Int,
      context: String,
  ): F[Array[Byte]] =
    socket.readN(size).flatMap { chunk =>
      if (chunk.size === size) chunk.toArray.pure[F]
      else UnexpectedProxyEof(context).raiseError[F, Array[Byte]]
    }

  private def portBytes(port: Port): Array[Byte] = {
    val value = port.value
    Array((value >>> 8).toByte, value.toByte)
  }

  private def unsigned(byte: Byte): Int = byte & 0xff

  private sealed abstract class ProxyException(message: String) extends IOException(message)

  private final case class UnsupportedHttpProxyScheme(scheme: Uri.Scheme)
      extends ProxyException(s"Unsupported HTTP proxy scheme: ${scheme.value}")

  private final case class MissingProxyTlsContext()
      extends ProxyException("EmberClient is not configured with a TLS context for the HTTPS proxy")

  private final case class ProxyConnectFailure(status: Int)
      extends ProxyException(s"HTTP proxy rejected CONNECT with status $status")

  private final case class ProxyResponseTooLarge(maxSize: Int)
      extends ProxyException(s"HTTP proxy response headers exceed $maxSize bytes")

  private final case class InvalidProxyResponseHeaderSize(maxSize: Int)
      extends ProxyException(s"HTTP proxy response header size must be positive: $maxSize")

  private final case class InvalidHttpProxyResponse()
      extends ProxyException("Invalid HTTP proxy response")

  private final case class UnexpectedProxyEof(context: String)
      extends ProxyException(s"Proxy closed the connection while reading $context")

  private final case class InvalidProxyCredentials(message: String) extends ProxyException(message)

  private final case class MissingSocks4Address(address: Host)
      extends ProxyException(s"No IPv4 address found for SOCKS4 target $address")

  private final case class InvalidSocks4Response()
      extends ProxyException("Invalid SOCKS4 proxy response")

  private final case class Socks4ConnectFailure(status: Int)
      extends ProxyException(s"SOCKS4 proxy rejected CONNECT with status $status")

  private final case class InvalidSocks5Response()
      extends ProxyException("Invalid SOCKS5 proxy response")

  private final case class Socks5NoAcceptableAuthMethod()
      extends ProxyException("SOCKS5 proxy has no acceptable authentication method")

  private final case class UnexpectedSocks5AuthMethod(method: Int)
      extends ProxyException(s"SOCKS5 proxy selected unexpected authentication method $method")

  private final case class Socks5AuthenticationFailure()
      extends ProxyException("SOCKS5 proxy authentication failed")

  private final case class Socks5ConnectFailure(status: Int)
      extends ProxyException(s"SOCKS5 proxy rejected CONNECT with status $status")
}
