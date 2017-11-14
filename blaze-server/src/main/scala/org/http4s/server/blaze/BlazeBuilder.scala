package org.http4s
package server
package blaze

import cats.effect._
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.{KeyStore, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLEngine, TrustManagerFactory}
import org.http4s.blaze.{BuildInfo => BlazeBuildInfo}
import org.http4s.blaze.channel
import org.http4s.blaze.channel.SocketConnection
import org.http4s.blaze.channel.nio1.NIO1SocketServerGroup
import org.http4s.blaze.channel.nio2.NIO2SocketServerGroup
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.{QuietTimeoutStage, SSLStage}
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.log4s.getLogger
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class BlazeBuilder[F[_]](
    socketAddress: InetSocketAddress,
    executionContext: ExecutionContext,
    idleTimeout: Duration,
    isNio2: Boolean,
    connectorPoolSize: Int,
    bufferSize: Int,
    enableWebSockets: Boolean,
    sslBits: Option[SSLConfig],
    isHttp2Enabled: Boolean,
    maxRequestLineLen: Int,
    maxHeadersLen: Int,
    serviceMounts: Vector[ServiceMount[F]],
    serviceErrorHandler: ServiceErrorHandler[F]
)(implicit F: Effect[F])
    extends ServerBuilder[F]
    with IdleTimeoutSupport[F]
    with SSLKeyStoreSupport[F]
    with SSLContextSupport[F]
    with server.WebSocketSupport[F] {
  type Self = BlazeBuilder[F]

  private[this] val logger = getLogger

  private def copy(
      socketAddress: InetSocketAddress = socketAddress,
      executionContext: ExecutionContext = executionContext,
      idleTimeout: Duration = idleTimeout,
      isNio2: Boolean = isNio2,
      connectorPoolSize: Int = connectorPoolSize,
      bufferSize: Int = bufferSize,
      enableWebSockets: Boolean = enableWebSockets,
      sslBits: Option[SSLConfig] = sslBits,
      http2Support: Boolean = isHttp2Enabled,
      maxRequestLineLen: Int = maxRequestLineLen,
      maxHeadersLen: Int = maxHeadersLen,
      serviceMounts: Vector[ServiceMount[F]] = serviceMounts,
      serviceErrorHandler: ServiceErrorHandler[F] = serviceErrorHandler): Self =
    new BlazeBuilder(
      socketAddress,
      executionContext,
      idleTimeout,
      isNio2,
      connectorPoolSize,
      bufferSize,
      enableWebSockets,
      sslBits,
      http2Support,
      maxRequestLineLen,
      maxHeadersLen,
      serviceMounts,
      serviceErrorHandler
    )

  /** Configure HTTP parser length limits
    *
    * These are to avoid denial of service attacks due to,
    * for example, an infinite request line.
    *
    * @param maxRequestLineLen maximum request line to parse
    * @param maxHeadersLen maximum data that compose headers
    */
  def withLengthLimits(
      maxRequestLineLen: Int = maxRequestLineLen,
      maxHeadersLen: Int = maxHeadersLen): Self =
    copy(maxRequestLineLen = maxRequestLineLen, maxHeadersLen = maxHeadersLen)

  override def withSSL(
      keyStore: StoreInfo,
      keyManagerPassword: String,
      protocol: String,
      trustStore: Option[StoreInfo],
      clientAuth: Boolean): Self = {
    val bits = KeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth)
    copy(sslBits = Some(bits))
  }

  override def withSSLContext(sslContext: SSLContext, clientAuth: Boolean): Self =
    copy(sslBits = Some(SSLContextBits(sslContext, clientAuth)))

  override def bindSocketAddress(socketAddress: InetSocketAddress): Self =
    copy(socketAddress = socketAddress)

  override def withExecutionContext(executionContext: ExecutionContext): BlazeBuilder[F] =
    copy(executionContext = executionContext)

  override def withIdleTimeout(idleTimeout: Duration): Self = copy(idleTimeout = idleTimeout)

  def withConnectorPoolSize(size: Int): Self = copy(connectorPoolSize = size)

  def withBufferSize(size: Int): Self = copy(bufferSize = size)

  def withNio2(isNio2: Boolean): Self = copy(isNio2 = isNio2)

  override def withWebSockets(enableWebsockets: Boolean): Self =
    copy(enableWebSockets = enableWebsockets)

  def enableHttp2(enabled: Boolean): Self = copy(http2Support = enabled)

  override def mountService(service: HttpService[F], prefix: String): Self = {
    val prefixedService =
      if (prefix.isEmpty || prefix == "/") service
      else {
        val newCaret = prefix match {
          case "/" => 0
          case x if x.startsWith("/") => x.length
          case x => x.length + 1
        }

        service.local { req: Request[F] =>
          req.withAttribute(Request.Keys.PathInfoCaret(newCaret))
        }
      }
    copy(serviceMounts = serviceMounts :+ ServiceMount[F](prefixedService, prefix))
  }

  def withServiceErrorHandler(serviceErrorHandler: ServiceErrorHandler[F]): Self =
    copy(serviceErrorHandler = serviceErrorHandler)

  def start: F[Server[F]] = F.delay {
    val aggregateService = Router(serviceMounts.map(mount => mount.prefix -> mount.service): _*)

    def resolveAddress(address: InetSocketAddress) =
      if (address.isUnresolved) new InetSocketAddress(address.getHostName, address.getPort)
      else address

    val pipelineFactory = { conn: SocketConnection =>
      def requestAttributes(secure: Boolean) =
        (conn.local, conn.remote) match {
          case (local: InetSocketAddress, remote: InetSocketAddress) =>
            AttributeMap(
              AttributeEntry(
                Request.Keys.ConnectionInfo,
                Request.Connection(
                  local = local,
                  remote = remote,
                  secure = secure
                )))
          case _ =>
            AttributeMap.empty
        }

      def http1Stage(secure: Boolean) =
        Http1ServerStage(
          aggregateService,
          requestAttributes(secure = secure),
          executionContext,
          enableWebSockets,
          maxRequestLineLen,
          maxHeadersLen,
          serviceErrorHandler
        )

      def http2Stage(engine: SSLEngine) =
        ProtocolSelector(
          engine,
          aggregateService,
          maxRequestLineLen,
          maxHeadersLen,
          requestAttributes(secure = true),
          executionContext,
          serviceErrorHandler
        )

      def prependIdleTimeout(lb: LeafBuilder[ByteBuffer]) =
        if (idleTimeout.isFinite) lb.prepend(new QuietTimeoutStage[ByteBuffer](idleTimeout))
        else lb

      getContext() match {
        case Some((ctx, clientAuth)) =>
          val engine = ctx.createSSLEngine()
          engine.setUseClientMode(false)
          engine.setNeedClientAuth(clientAuth)

          var lb = LeafBuilder(
            if (isHttp2Enabled) http2Stage(engine)
            else http1Stage(secure = true)
          )
          lb = prependIdleTimeout(lb)
          lb.prepend(new SSLStage(engine))

        case None =>
          if (isHttp2Enabled) logger.warn("HTTP/2 support requires TLS. Falling back to HTTP/1.")
          var lb = LeafBuilder(http1Stage(secure = false))
          lb = prependIdleTimeout(lb)
          lb
      }
    }

    val factory =
      if (isNio2)
        NIO2SocketServerGroup.fixedGroup(connectorPoolSize, bufferSize)
      else
        NIO1SocketServerGroup.fixedGroup(connectorPoolSize, bufferSize)

    val address = resolveAddress(socketAddress)

    // if we have a Failure, it will be caught by the effect
    val serverChannel = factory.bind(address, pipelineFactory).get

    val server = new Server[F] {
      override def shutdown: F[Unit] = F.delay {
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

    logger.info(
      s"http4s v${BuildInfo.version} on blaze v${BlazeBuildInfo.version} started at ${Server
        .baseUri(address, sslBits.isDefined)}")
    server
  }

  private def getContext(): Option[(SSLContext, Boolean)] = sslBits.map {
    case KeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth) =>
      val ksStream = new FileInputStream(keyStore.path)
      val ks = KeyStore.getInstance("JKS")
      ks.load(ksStream, keyStore.password.toCharArray)
      ksStream.close()

      val tmf = trustStore.map { auth =>
        val ksStream = new FileInputStream(auth.path)

        val ks = KeyStore.getInstance("JKS")
        ks.load(ksStream, auth.password.toCharArray)
        ksStream.close()

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

        tmf.init(ks)
        tmf.getTrustManagers
      }

      val kmf = KeyManagerFactory.getInstance(
        Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
          .getOrElse(KeyManagerFactory.getDefaultAlgorithm))

      kmf.init(ks, keyManagerPassword.toCharArray)

      val context = SSLContext.getInstance(protocol)
      context.init(kmf.getKeyManagers, tmf.orNull, null)

      (context, clientAuth)

    case SSLContextBits(context, clientAuth) =>
      (context, clientAuth)
  }
}

object BlazeBuilder {
  def apply[F[_]](implicit F: Effect[F]): BlazeBuilder[F] =
    new BlazeBuilder(
      socketAddress = ServerBuilder.DefaultSocketAddress,
      executionContext = ExecutionContext.global,
      idleTimeout = IdleTimeoutSupport.DefaultIdleTimeout,
      isNio2 = false,
      connectorPoolSize = channel.defaultPoolSize,
      bufferSize = 64 * 1024,
      enableWebSockets = true,
      sslBits = None,
      isHttp2Enabled = false,
      maxRequestLineLen = 4 * 1024,
      maxHeadersLen = 40 * 1024,
      serviceMounts = Vector.empty,
      serviceErrorHandler = DefaultServiceErrorHandler
    )
}

private final case class ServiceMount[F[_]](service: HttpService[F], prefix: String)
