package org.http4s.server.blaze

import java.net.InetSocketAddress
import java.util.concurrent.ThreadFactory

import cats.effect.{ConcurrentEffect, Resource, Timer}
import org.http4s.HttpApp
import org.http4s.blaze.channel.ChannelOptions
import org.http4s.server._

import scala.concurrent.ExecutionContext

object BlazeServer {
  def make[F[_]](
      config: Config,
      socketAddress: Option[InetSocketAddress] = None,
      executionContext: Option[ExecutionContext] = None,
      selectorThreadFactory: Option[ThreadFactory] = None,
      sslContextBits: Option[SSLContextBits] = None,
      httpApp: Option[HttpApp[F]] = None,
      serviceErrorHandler: Option[ServiceErrorHandler[F]] = None,
      channelOptions: Option[ChannelOptions] = None)(
      implicit CE: ConcurrentEffect[F],
      T: Timer[F]): Resource[F, Server[F]] = {

    implicit class BuilderOps[A](val c: A) {
      def withOption[O](o: Option[O])(f: A => O => A): A = o match {
        case Some(value) => f(c)(value)
        case None => c
      }
    }

    BlazeServerBuilder[F]
      .withOption(config.host.zip(config.port)) { s =>
        {
          case (host, port) =>
            s.bindSocketAddress(InetSocketAddress.createUnresolved(host, port))
        }
      }
      .withOption(config.responseHeaderTimeout)(_.withResponseHeaderTimeout)
      .withOption(config.idleTimeout)(_.withIdleTimeout)
      .withOption(config.nio2)(_.withNio2)
      .withOption(config.connectorPoolSize)(_.withConnectorPoolSize)
      .withOption(config.bufferSize)(_.withBufferSize)
      .withOption(config.webSockets)(_.withWebSockets)
      .withOption(config.enableHttp2)(_.enableHttp2)
      .withOption(config.maxRequestLineLength)(_.withMaxRequestLineLength)
      .withOption(config.maxHeadersLength)(_.withMaxHeadersLength)
      .withOption(config.chunkBufferMaxSize)(_.withChunkBufferMaxSize)
      .withOption(config.banner)(_.withBanner)
      .withOption(socketAddress)(_.bindSocketAddress)
      .withOption(config.keyStoreBits) { s =>
        {
          case KeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth) =>
            s.withSSL(keyStore, keyManagerPassword, protocol, trustStore, clientAuth)
        }
      }
      .withOption(executionContext)(_.withExecutionContext)
      .withOption(selectorThreadFactory)(_.withSelectorThreadFactory)
      .withOption(sslContextBits) { s =>
        {
          case SSLContextBits(sslContext, clientAuth) => s.withSSLContext(sslContext, clientAuth)
        }
      }
      .withOption(httpApp)(_.withHttpApp)
      .withOption(serviceErrorHandler)(_.withServiceErrorHandler)
      .withOption(channelOptions)(_.withChannelOptions)
      .resource
  }
}
