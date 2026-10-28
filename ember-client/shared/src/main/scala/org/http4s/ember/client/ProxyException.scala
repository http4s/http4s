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

import com.comcast.ip4s.Host
import org.http4s.Uri

import scala.concurrent.duration.Duration

/** Indicates a failure to establish a tunnel through a [[Proxy]]. */
sealed trait ProxyException extends RuntimeException

object ProxyException {

  /** The [[Proxy]] is configured with a scheme Ember cannot tunnel through. */
  final class UnsupportedScheme(val scheme: Uri.Scheme) extends ProxyException {
    override def getMessage: String = s"Unsupported HTTP proxy scheme: ${scheme.value}"
  }

  object UnsupportedScheme {
    def unapply(failure: UnsupportedScheme): Option[Uri.Scheme] = Some(failure.scheme)
  }

  /** The client has no `TLSContext` with which to reach an `https` [[Proxy]]. */
  final class MissingTlsContext extends ProxyException {
    override def getMessage: String =
      "EmberClient is not configured with a TLS context for the HTTPS proxy"
  }

  /** The configured credentials cannot be encoded for the proxy's protocol. */
  final class InvalidCredentials(val details: String) extends ProxyException {
    override def getMessage: String = details
  }

  object InvalidCredentials {
    def unapply(failure: InvalidCredentials): Option[String] = Some(failure.details)
  }

  /** SOCKS4 can only reach IPv4 targets, and the target resolved to none. */
  final class MissingSocks4Address(val host: Host) extends ProxyException {
    override def getMessage: String = s"No IPv4 address found for SOCKS4 target $host"
  }

  object MissingSocks4Address {
    def unapply(failure: MissingSocks4Address): Option[Host] = Some(failure.host)
  }

  /** The HTTP proxy answered `CONNECT` with a status outside the 2xx range. */
  final class HttpConnectFailure(val status: Int, val statusLine: String) extends ProxyException {
    override def getMessage: String = s"HTTP proxy rejected CONNECT: $statusLine"
  }

  object HttpConnectFailure {
    def unapply(failure: HttpConnectFailure): Option[(Int, String)] =
      Some((failure.status, failure.statusLine))
  }

  /** The SOCKS4 proxy rejected the request, per RFC 1928's predecessor. */
  final class Socks4ConnectFailure(val status: Int) extends ProxyException {
    override def getMessage: String = s"SOCKS4 proxy rejected CONNECT with status $status"
  }

  object Socks4ConnectFailure {
    def unapply(failure: Socks4ConnectFailure): Option[Int] = Some(failure.status)
  }

  /** The SOCKS5 proxy rejected the request. See RFC 1928, section 6. */
  final class Socks5ConnectFailure(val status: Int) extends ProxyException {
    override def getMessage: String = s"SOCKS5 proxy rejected CONNECT with status $status"
  }

  object Socks5ConnectFailure {
    def unapply(failure: Socks5ConnectFailure): Option[Int] = Some(failure.status)
  }

  /** The SOCKS5 proxy rejected the configured credentials. */
  final class Socks5AuthenticationFailure extends ProxyException {
    override def getMessage: String = "SOCKS5 proxy authentication failed"
  }

  /** The SOCKS5 proxy accepts no authentication method the client offered, which is what a proxy
    * demanding credentials answers when none are configured.
    */
  final class Socks5NoAcceptableAuthMethod extends ProxyException {
    override def getMessage: String = "SOCKS5 proxy has no acceptable authentication method"
  }

  /** Establishing the tunnel exceeded `EmberClientBuilder.withProxyTimeout`. */
  final class Timeout(val proxy: Proxy, val timeout: Duration) extends ProxyException {
    override def getMessage: String =
      s"Connecting through ${describe(proxy)} timed out after $timeout"
  }

  object Timeout {
    def unapply(failure: Timeout): Option[(Proxy, Duration)] =
      Some((failure.proxy, failure.timeout))
  }

  // Describes a proxy without rendering its credentials.
  private def describe(proxy: Proxy): String =
    proxy match {
      case HttpProxy(scheme, host, port, _) =>
        s"${scheme.value} proxy $host${port.fold("")(port => s":$port")}"
      case Socks4Proxy(host, port, _) => s"SOCKS4 proxy $host:$port"
      case Socks5Proxy(host, port, _) => s"SOCKS5 proxy $host:$port"
    }

  private[client] final class InvalidHttpResponse extends ProxyException {
    override def getMessage: String = "Invalid HTTP proxy response"
  }

  private[client] final class ResponseTooLarge(maxSize: Int) extends ProxyException {
    override def getMessage: String = s"HTTP proxy response headers exceed $maxSize bytes"
  }

  private[client] final class InvalidResponseHeaderSize(maxSize: Int) extends ProxyException {
    override def getMessage: String = s"HTTP proxy response header size must be positive: $maxSize"
  }

  private[client] final class UnexpectedEof(context: String) extends ProxyException {
    override def getMessage: String = s"Proxy closed the connection while reading $context"
  }

  private[client] final class InvalidSocks4Response extends ProxyException {
    override def getMessage: String = "Invalid SOCKS4 proxy response"
  }

  private[client] final class InvalidSocks5Response extends ProxyException {
    override def getMessage: String = "Invalid SOCKS5 proxy response"
  }

  private[client] final class UnexpectedSocks5AuthMethod(method: Int) extends ProxyException {
    override def getMessage: String =
      s"SOCKS5 proxy selected unexpected authentication method $method"
  }

  private[client] final class InvalidSocks5Target(host: Host) extends ProxyException {
    override def getMessage: String = s"SOCKS5 target host is not a valid domain name: $host"
  }
}
