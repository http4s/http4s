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

package org.http4s.ember.server

import cats.effect._
import com.comcast.ip4s._
import fs2.io.net.Network
import fs2.io.net.tls._
import fs2.io.net.unixsocket.UnixSocketAddress
import fs2.io.net.unixsocket.UnixSockets
import org.http4s.ember.server.Config._
import org.http4s.ember.server.EmberServerBuilder.Defaults

import scala.annotation.nowarn
import scala.concurrent.duration.Duration

@SuppressWarnings(Array("scalafix:Http4sGeneralLinters.nonValidatingCopyConstructor"))
// Whenever you add a new field to maintain backward compatibility:
//  * add the field to `copy`
//  * create a new `apply`
//  * add a new case to `fromProduct`
//
// The default values in the constructor are not actually applied (defaults from `apply` are).
// But they still need to be present to enable tools like PureConfig.
final case class Config private (
    host: Option[Host] = Host.fromString(Defaults.host),
    port: Port = Port.fromInt(Defaults.port).get,
    tlsConfig: Option[TLSConfig] = None,
    maxConnections: Int = Defaults.maxConnections,
    receiveBufferSize: Int = Defaults.receiveBufferSize,
    maxHeaderSize: Int = Defaults.maxHeaderSize,
    requestHeaderReceiveTimeout: Duration = Defaults.requestHeaderReceiveTimeout,
    idleTimeout: Duration = Defaults.idleTimeout,
    shutdownTimeout: Duration = Defaults.shutdownTimeout,
    unixSocketConfig: Option[UnixSocketConfig] = None,
    enableHttp2: Boolean = Defaults.enableHttp2,
) {

  def toBuilder[F[_]: Async: Network](
      unixSockets: Option[UnixSockets[F]] = None,
      tlsContext: Option[TLSContext[F]] = None,
      makeTLSParams: Option[TLSConfig => TLSParameters] = None,
  ): EmberServerBuilder[F] = EmberServerBuilder.default
    .pipe { builder =>
      host match {
        case Some(value) => builder.withHost(value)
        case None => builder.withoutHost
      }
    }
    .withPort(port)
    .pipe { builder =>
      (tlsContext, makeTLSParams, tlsConfig) match {
        case (Some(tlsContext), Some(makeTLSParams), Some(tlsConfig)) =>
          val tlsParameters = makeTLSParams(tlsConfig)
          builder.withTLS(tlsContext, tlsParameters)
        case (Some(tlsContext), _, _) =>
          builder.withTLS(tlsContext)
        case (None, _, _) =>
          builder.withoutTLS
      }
    }
    .withMaxConnections(maxConnections)
    .withReceiveBufferSize(receiveBufferSize)
    .withMaxHeaderSize(maxHeaderSize)
    .withRequestHeaderReceiveTimeout(requestHeaderReceiveTimeout)
    .withIdleTimeout(idleTimeout)
    .withShutdownTimeout(shutdownTimeout)
    .pipe { builder =>
      (unixSockets, unixSocketConfig) match {
        case (Some(unixSockets), Some(unixSocketConfig)) =>
          builder.withUnixSocketConfig(
            unixSockets,
            unixSocketConfig.unixSocketAddress,
            unixSocketConfig.deleteIfExists,
            unixSocketConfig.deleteOnClose,
          )
        case (_, _) =>
          builder.withoutUnixSocketConfig
      }
    }
    .pipe(builder => if (enableHttp2) builder.withHttp2 else builder.withoutHttp2)

  @nowarn("msg=never used")
  private def copy(
      host: Option[Host],
      port: Port,
      tlsConfig: Option[TLSConfig],
      maxConnections: Int,
      receiveBufferSize: Int,
      maxHeaderSize: Int,
      requestHeaderReceiveTimeout: Duration,
      idleTimeout: Duration,
      shutdownTimeout: Duration,
      unixSocketConfig: Option[UnixSocketConfig],
      enableHttp2: Boolean,
  ): Any = this
}

object Config {
  def apply(
      host: Option[Host] = Host.fromString(Defaults.host),
      port: Port = Port.fromInt(Defaults.port).get,
      tlsConfig: Option[TLSConfig] = None,
      maxConnections: Int = Defaults.maxConnections,
      receiveBufferSize: Int = Defaults.receiveBufferSize,
      maxHeaderSize: Int = Defaults.maxHeaderSize,
      requestHeaderReceiveTimeout: Duration = Defaults.requestHeaderReceiveTimeout,
      idleTimeout: Duration = Defaults.idleTimeout,
      shutdownTimeout: Duration = Defaults.shutdownTimeout,
      unixSocketConfig: Option[UnixSocketConfig] = None,
      enableHttp2: Boolean = Defaults.enableHttp2,
  ): Config = new Config(
    host,
    port,
    tlsConfig,
    maxConnections,
    receiveBufferSize,
    maxHeaderSize,
    requestHeaderReceiveTimeout,
    idleTimeout,
    shutdownTimeout,
    unixSocketConfig,
    enableHttp2,
  )

  @nowarn
  def fromProduct(p: Product): Config = p match {
    case p: Product11[
          Option[Host],
          Port,
          Option[TLSConfig],
          Int,
          Int,
          Int,
          Duration,
          Duration,
          Duration,
          Option[UnixSocketConfig],
          Boolean,
        ] =>
      Config(
        p._1,
        p._2,
        p._3,
        p._4,
        p._5,
        p._6,
        p._7,
        p._8,
        p._9,
        p._10,
        p._11,
      )
  }

  @nowarn("msg=never used")
  private def unapply(c: Config): Any = this

  @SuppressWarnings(Array("scalafix:Http4sGeneralLinters.nonValidatingCopyConstructor"))
  // Whenever you add a new field to maintain backward compatibility:
  //  * add the field to `copy`
  //  * create a new `apply`
  //  * add a new case to `fromProduct`
  final case class UnixSocketConfig private (
      unixSocketAddress: UnixSocketAddress,
      deleteIfExists: Boolean,
      deleteOnClose: Boolean,
  ) {
    @nowarn("msg=never used")
    private def copy(
        unixSocketAddress: UnixSocketAddress,
        deleteIfExists: Boolean,
        deleteOnClose: Boolean,
    ): Any = this
  }

  object UnixSocketConfig {
    def apply(
        unixSocketAddress: UnixSocketAddress,
        deleteIfExists: Boolean,
        deleteOnClose: Boolean,
    ): UnixSocketConfig = new UnixSocketConfig(unixSocketAddress, deleteIfExists, deleteOnClose)

    @nowarn
    def fromProduct(p: Product): UnixSocketConfig = p match {
      case p: Product3[
            UnixSocketAddress,
            Boolean,
            Boolean,
          ] =>
        UnixSocketConfig(
          p._1,
          p._2,
          p._3,
        )
    }

    @nowarn("msg=never used")
    private def unapply(c: UnixSocketConfig): Any = this
  }

  @SuppressWarnings(Array("scalafix:Http4sGeneralLinters.nonValidatingCopyConstructor"))
  // Whenever you add a new field to maintain backward compatibility:
  //  * add the field to `copy`
  //  * create a new `apply`
  //  * add a new case to `fromProduct`
  //
  // The default values in the constructor are not actually applied (defaults from `apply` are).
  // But they still need to be present to enable tools like PureConfig.
  final case class TLSConfig private (
      applicationProtocols: Option[List[String]] = None,
      cipherSuites: Option[List[String]] = None,
      enableRetransmissions: Option[Boolean] = None,
      endpointIdentificationAlgorithm: Option[String] = None,
      maximumPacketSize: Option[Int] = None,
      protocols: Option[List[String]] = None,
      useCipherSuitesOrder: Boolean = false,
      needClientAuth: Boolean = false,
      wantClientAuth: Boolean = false,
  ) {
    @nowarn("msg=never used")
    private def copy(
        applicationProtocols: Option[List[String]],
        cipherSuites: Option[List[String]],
        enableRetransmissions: Option[Boolean],
        endpointIdentificationAlgorithm: Option[String],
        maximumPacketSize: Option[Int],
        protocols: Option[List[String]],
        useCipherSuitesOrder: Boolean,
        needClientAuth: Boolean,
        wantClientAuth: Boolean,
    ): Any = this
  }

  object TLSConfig {
    def apply(
        applicationProtocols: Option[List[String]] = None,
        cipherSuites: Option[List[String]] = None,
        enableRetransmissions: Option[Boolean] = None,
        endpointIdentificationAlgorithm: Option[String] = None,
        maximumPacketSize: Option[Int] = None,
        protocols: Option[List[String]] = None,
        useCipherSuitesOrder: Boolean = false,
        needClientAuth: Boolean = false,
        wantClientAuth: Boolean = false,
    ): TLSConfig = new TLSConfig(
      applicationProtocols,
      cipherSuites,
      enableRetransmissions,
      endpointIdentificationAlgorithm,
      maximumPacketSize,
      protocols,
      useCipherSuitesOrder,
      needClientAuth,
      wantClientAuth,
    )

    @nowarn
    def fromProduct(p: Product): TLSConfig = p match {
      case p: Product9[
            Option[List[String]],
            Option[List[String]],
            Option[Boolean],
            Option[String],
            Option[Int],
            Option[List[String]],
            Boolean,
            Boolean,
            Boolean,
          ] =>
        TLSConfig(
          p._1,
          p._2,
          p._3,
          p._4,
          p._5,
          p._6,
          p._7,
          p._8,
          p._9,
        )
    }

    @nowarn("msg=never used")
    private def unapply(c: TLSConfig): Any = this
  }

  implicit private[server] final class ChainingOps[A](private val self: A) extends AnyVal {
    def pipe[B](f: A => B): B = f(self)
  }
}
