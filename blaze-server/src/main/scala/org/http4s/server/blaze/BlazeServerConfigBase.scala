package org.http4s
package server
package blaze

import java.io.FileInputStream
import java.security.KeyStore
import java.security.Security
import javax.net.ssl.{TrustManagerFactory, KeyManagerFactory, SSLContext, SSLEngine}
import java.util.concurrent.ExecutorService
import java.net.InetSocketAddress
import java.nio.ByteBuffer

import org.http4s.blaze.channel
import org.http4s.blaze.channel.SocketConnection
import org.http4s.blaze.channel.nio1.NIO1SocketServerGroup
import org.http4s.blaze.channel.nio2.NIO2SocketServerGroup
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.{SSLStage, QuietTimeoutStage}
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.tls.ClientAuth
import org.http4s.util.threads.DefaultPool

import org.log4s.getLogger

import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}

private[blaze] abstract class BlazeServerConfigBase { self: BlazeServerConfig =>
  private[this] val logger = getLogger

  def bindHttp(port: Int = 8080, host: String = "127.0.0.1"): BlazeServerConfig =
    withSocketAddress(InetSocketAddress.createUnresolved(host, port))

  def bindHttps(port: Int = 8443, host: String = "127.0.0.1", sslContext: SSLContext): BlazeServerConfig = {
    withSocketAddress(InetSocketAddress.createUnresolved(host, port))
    withSslContext(sslContext)
  }

  def start: Task[Server] = {
    def aggregateService = Router(serviceMounts.reverse.map { mount =>
      mount.prefix -> mount.service
    }: _*)

    def resolveAddress(address: InetSocketAddress) =
      if (address.isUnresolved) new InetSocketAddress(address.getHostName, address.getPort)
      else address

    def pipelineFactory = { conn: SocketConnection =>
      val requestAttributes =
        (conn.local, conn.remote) match {
          case (l: InetSocketAddress, r: InetSocketAddress) =>
            AttributeMap(AttributeEntry(Request.Keys.ConnectionInfo, Request.Connection(l,r, true)))
          case _ =>
            AttributeMap.empty
        }

      def http1Stage =
        Http1ServerStage(aggregateService, requestAttributes, serviceExecutor.getOrElse(DefaultPool), webSocketsEnabled, maxRequestLineLength, maxHeadersLength)

      def http2Stage(engine: SSLEngine) =
        ProtocolSelector(engine, aggregateService, maxRequestLineLength, maxHeadersLength, requestAttributes, serviceExecutor.getOrElse(DefaultPool))

      def prependIdleTimeout(lb: LeafBuilder[ByteBuffer]) = {
        if (idleTimeout.isFinite) lb.prepend(new QuietTimeoutStage[ByteBuffer](idleTimeout))
        else lb
      }

      sslContext match {
        case Some(ctx) =>
          val engine = ctx.createSSLEngine()
          engine.setUseClientMode(false)
          clientAuth match {
            case ClientAuth.Need => engine.setNeedClientAuth(true)
            case ClientAuth.Want => engine.setWantClientAuth(true)
            case ClientAuth.None => engine.setNeedClientAuth(false)
          }

          var lb = LeafBuilder(
            if (http2Enabled) http2Stage(engine)
            else http1Stage
          )
          lb = prependIdleTimeout(lb)
          lb.prepend(new SSLStage(engine))

        case None =>
          if (http2Enabled) logger.warn("HTTP/2 support requires TLS.")
          var lb = LeafBuilder(http1Stage)
          lb = prependIdleTimeout(lb)
          lb
      }
    }

    for {
      factory <- serverChannelGroupConfig.start
      address = resolveAddress(socketAddress)
      serverChannel <- Task.delay(factory.bind(address, pipelineFactory).get)
      _ = banner.foreach(_.lines.foreach(logger.info(_)))
      _ = logger.info(s"Started http4s-${BuildInfo.version} on blaze backend")
    } yield new Server {
      override def shutdown: Task[Unit] = Task.delay {
        serverChannel.close()
        factory.closeGroup()
      }

      override def onShutdown(f: => Unit): this.type = {
        serverChannel.addShutdownHook(() => f)
        this
      }

      val address: InetSocketAddress =
        serverChannel.socketAddress

      override def toString: String =
        s"BlazeServer($address)"
    }
  }

  def mountService(service: HttpService, prefix: String): BlazeServerConfig = {
    val prefixedService =
      if (prefix.isEmpty || prefix == "/") service
      else {
        val newCaret = prefix match {
          case "/"                    => 0
          case x if x.startsWith("/") => x.length
          case x                      => x.length + 1
        }
        service.local { req: Request =>
          req.withAttribute(Request.Keys.PathInfoCaret(newCaret))
        }
      }
    copy(serviceMounts = ServiceMount(prefixedService, prefix) :: serviceMounts)
  }
}

final case class ServiceMount(service: HttpService, prefix: String)
