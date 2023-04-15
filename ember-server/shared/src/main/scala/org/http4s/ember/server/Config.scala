package org.http4s.ember.server

import cats.effect._
import com.comcast.ip4s._
import fs2.io.net.Network
import fs2.io.net.tls._
import fs2.io.net.unixsocket.{UnixSocketAddress, UnixSockets}
import org.http4s.ember.server.Config.{TLSConfig, UnixSocketConfig}
import org.http4s.ember.server.EmberServerBuilder.Defaults

import java.security.AlgorithmConstraints
import javax.net.ssl.{SNIMatcher, SNIServerName, SSLEngine}
import scala.annotation.nowarn
import scala.concurrent.duration.Duration
import scala.util.chaining._

final case class Config(
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
      algorithmConstraints: Option[AlgorithmConstraints] = None,
      serverNames: Option[List[SNIServerName]] = None,
      sniMatchers: Option[List[SNIMatcher]] = None,
      handshakeApplicationProtocolSelector: Option[(SSLEngine, List[String]) => String] = None,
  ): EmberServerBuilder[F] = EmberServerBuilder.default
    .pipe { builder =>
      host match {
        case Some(value) => builder.withHost(value)
        case None => builder.withoutHost
      }
    }
    .withPort(port)
    .pipe { builder =>
      tlsContext.zip(tlsConfig) match {
        case Some((tlsContext, tlsConfig)) =>
          import tlsConfig._
          val tlsParameters = TLSParameters(
            algorithmConstraints,
            applicationProtocols,
            cipherSuites,
            enableRetransmissions,
            endpointIdentificationAlgorithm,
            maximumPacketSize,
            protocols,
            serverNames,
            sniMatchers,
            useCipherSuitesOrder,
            needClientAuth,
            wantClientAuth,
            handshakeApplicationProtocolSelector,
          )
          builder.withTLS(tlsContext, tlsParameters)
        case None =>
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
      unixSockets.zip(unixSocketConfig) match {
        case Some((unixSockets, unixSocketConfig)) =>
          builder.withUnixSocketConfig(
            unixSockets,
            unixSocketConfig.unixSocketAddress,
            unixSocketConfig.deleteIfExists,
            unixSocketConfig.deleteOnClose,
          )
        case None =>
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
      unixSocketConfig: Option[(UnixSocketAddress, Boolean, Boolean)],
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

  @nowarn("msg=never used")
  private def unapply(c: Config): Any = this

  final case class UnixSocketConfig(
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

    @nowarn("msg=never used")
    private def unapply(c: UnixSocketConfig): Any = this
  }

  final case class TLSConfig(
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

    @nowarn("msg=never used")
    private def unapply(c: TLSConfig): Any = this
  }
}
