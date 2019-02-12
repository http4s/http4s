package org.http4s
package server
package blaze

import cats._
import cats.data.Kleisli
import cats.implicits._
import cats.effect._
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.{KeyStore, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLEngine, TrustManagerFactory}
import org.http4s.blaze.{BuildInfo => BlazeBuildInfo}
import org.http4s.blaze.channel.{
  ChannelOptions,
  DefaultPoolSize,
  ServerChannel,
  ServerChannelGroup,
  SocketConnection
}
import org.http4s.blaze.channel.nio1.NIO1SocketServerGroup
import org.http4s.blaze.channel.nio2.NIO2SocketServerGroup
import org.http4s.blaze.http.http2.server.ALPNServerSelector
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.SSLStage
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.{BlazeBackendBuilder, tickWheelResource}
import org.http4s.server.ServerRequestKeys
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.log4s.getLogger
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import _root_.io.chrisdavenport.vault._
import scodec.bits.ByteVector

/**
  * BlazeBuilder is the component for the builder pattern aggregating
  * different components to finally serve requests.
  *
  * Variables:
  * @param socketAddress: Socket Address the server will be mounted at
  * @param executionContext: Execution Context the underlying blaze futures
  *    will be executed upon.
  * @param responseHeaderTimeout: Time from when the request is made until a
  *    response line is generated before a 503 response is returned and the
  *    `HttpApp` is canceled
  * @param idleTimeout: Period of Time a connection can remain idle before the
  *    connection is timed out and disconnected.
  *    Duration.Inf disables this feature.
  * @param isNio2: Whether or not to use NIO2 or NIO1 Socket Server Group
  * @param connectorPoolSize: Number of worker threads for the new Socket Server Group
  * @param bufferSize: Buffer size to use for IO operations
  * @param enableWebsockets: Enables Websocket Support
  * @param sslBits: If defined enables secure communication to the server using the
  *    sslContext
  * @param isHttp2Enabled: Whether or not to enable Http2 Server Features
  * @param maxRequestLineLength: Maximum request line to parse
  *    If exceeded returns a 400 Bad Request.
  * @param maxHeadersLen: Maximum data that composes the headers.
  *    If exceeded returns a 400 Bad Request.
  * @param chunkBufferMaxSize Size of the buffer that is used when Content-Length header is not specified.
  * @param serviceMounts: The services that are mounted on this server to serve.
  *    These services get assembled into a Router with the longer prefix winning.
  * @param serviceErrorHandler: The last resort to recover and generate a response
  *    this is necessary to recover totality from the error condition.
  * @param banner: Pretty log to display on server start. An empty sequence
  *    such as Nil disables this
  */
class BlazeServerBuilder[F[_]](
    socketAddress: InetSocketAddress,
    executionContext: ExecutionContext,
    responseHeaderTimeout: Duration,
    idleTimeout: Duration,
    isNio2: Boolean,
    connectorPoolSize: Int,
    bufferSize: Int,
    enableWebSockets: Boolean,
    sslBits: Option[SSLConfig],
    isHttp2Enabled: Boolean,
    maxRequestLineLen: Int,
    maxHeadersLen: Int,
    chunkBufferMaxSize: Int,
    httpApp: HttpApp[F],
    serviceErrorHandler: ServiceErrorHandler[F],
    banner: immutable.Seq[String],
    val channelOptions: ChannelOptions
)(implicit protected val F: ConcurrentEffect[F], timer: Timer[F])
    extends ServerBuilder[F]
    with BlazeBackendBuilder[Server[F]] {
  type Self = BlazeServerBuilder[F]

  private[this] val logger = getLogger

  private def copy(
      socketAddress: InetSocketAddress = socketAddress,
      executionContext: ExecutionContext = executionContext,
      idleTimeout: Duration = idleTimeout,
      responseHeaderTimeout: Duration = responseHeaderTimeout,
      isNio2: Boolean = isNio2,
      connectorPoolSize: Int = connectorPoolSize,
      bufferSize: Int = bufferSize,
      enableWebSockets: Boolean = enableWebSockets,
      sslBits: Option[SSLConfig] = sslBits,
      http2Support: Boolean = isHttp2Enabled,
      maxRequestLineLen: Int = maxRequestLineLen,
      maxHeadersLen: Int = maxHeadersLen,
      chunkBufferMaxSize: Int = chunkBufferMaxSize,
      httpApp: HttpApp[F] = httpApp,
      serviceErrorHandler: ServiceErrorHandler[F] = serviceErrorHandler,
      banner: immutable.Seq[String] = banner,
      channelOptions: ChannelOptions = channelOptions
  ): Self =
    new BlazeServerBuilder(
      socketAddress,
      executionContext,
      responseHeaderTimeout,
      idleTimeout,
      isNio2,
      connectorPoolSize,
      bufferSize,
      enableWebSockets,
      sslBits,
      http2Support,
      maxRequestLineLen,
      maxHeadersLen,
      chunkBufferMaxSize,
      httpApp,
      serviceErrorHandler,
      banner,
      channelOptions
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

  def withSSL(
      keyStore: StoreInfo,
      keyManagerPassword: String,
      protocol: String = "TLS",
      trustStore: Option[StoreInfo] = None,
      clientAuth: SSLClientAuthMode = SSLClientAuthMode.NotRequested): Self = {
    val bits = KeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth)
    copy(sslBits = Some(bits))
  }

  def withSSLContext(
      sslContext: SSLContext,
      clientAuth: SSLClientAuthMode = SSLClientAuthMode.NotRequested): Self =
    copy(sslBits = Some(SSLContextBits(sslContext, clientAuth)))

  override def bindSocketAddress(socketAddress: InetSocketAddress): Self =
    copy(socketAddress = socketAddress)

  def withExecutionContext(executionContext: ExecutionContext): BlazeServerBuilder[F] =
    copy(executionContext = executionContext)

  def withIdleTimeout(idleTimeout: Duration): Self = copy(idleTimeout = idleTimeout)

  def withResponseHeaderTimeout(responseHeaderTimeout: Duration): Self =
    copy(responseHeaderTimeout = responseHeaderTimeout)

  def withConnectorPoolSize(size: Int): Self = copy(connectorPoolSize = size)

  def withBufferSize(size: Int): Self = copy(bufferSize = size)

  def withNio2(isNio2: Boolean): Self = copy(isNio2 = isNio2)

  def withWebSockets(enableWebsockets: Boolean): Self =
    copy(enableWebSockets = enableWebsockets)

  def enableHttp2(enabled: Boolean): Self = copy(http2Support = enabled)

  def withHttpApp(httpApp: HttpApp[F]): Self =
    copy(httpApp = httpApp)

  def withServiceErrorHandler(serviceErrorHandler: ServiceErrorHandler[F]): Self =
    copy(serviceErrorHandler = serviceErrorHandler)

  def withBanner(banner: immutable.Seq[String]): Self =
    copy(banner = banner)

  def withChannelOptions(channelOptions: ChannelOptions): BlazeServerBuilder[F] =
    copy(channelOptions = channelOptions)

  def withMaxRequestLineLength(maxRequestLineLength: Int): BlazeServerBuilder[F] =
    copy(maxRequestLineLen = maxRequestLineLength)

  def withMaxHeadersLength(maxHeadersLength: Int): BlazeServerBuilder[F] =
    copy(maxHeadersLen = maxHeadersLength)

  def withChunkBufferMaxSize(chunkBufferMaxSize: Int): BlazeServerBuilder[F] =
    copy(chunkBufferMaxSize = chunkBufferMaxSize)

  private def pipelineFactory(
      scheduler: TickWheelExecutor
  )(conn: SocketConnection): Future[LeafBuilder[ByteBuffer]] = {
    def requestAttributes(secure: Boolean, optionalSslEngine: Option[SSLEngine]): () => Vault =
      (conn.local, conn.remote) match {
        case (local: InetSocketAddress, remote: InetSocketAddress) =>
          () =>
            Vault.empty
              .insert(
                Request.Keys.ConnectionInfo,
                Request.Connection(
                  local = local,
                  remote = remote,
                  secure = secure
                )
              )
              .insert(
                ServerRequestKeys.SecureSession,
                //Create SSLSession object only for https requests and if current SSL session is not empty. Here, each
                //condition is checked inside a "flatMap" to handle possible "null" values
                Alternative[Option]
                  .guard(secure)
                  .flatMap(_ => optionalSslEngine)
                  .flatMap(engine => Option(engine.getSession))
                  .flatMap { session =>
                    (
                      Option(session.getId).map(ByteVector(_).toHex),
                      Option(session.getCipherSuite),
                      Option(session.getCipherSuite).map(SSLContextFactory.deduceKeyLength),
                      SSLContextFactory.getCertChain(session).some).mapN(SecureSession.apply)
                  }
              )
        case _ =>
          () =>
            Vault.empty
      }

    def http1Stage(secure: Boolean, engine: Option[SSLEngine]) =
      Http1ServerStage(
        httpApp,
        requestAttributes(secure = secure, engine),
        executionContext,
        enableWebSockets,
        maxRequestLineLen,
        maxHeadersLen,
        chunkBufferMaxSize,
        serviceErrorHandler,
        responseHeaderTimeout,
        idleTimeout,
        scheduler
      )

    def http2Stage(engine: SSLEngine): ALPNServerSelector =
      ProtocolSelector(
        engine,
        httpApp,
        maxRequestLineLen,
        maxHeadersLen,
        chunkBufferMaxSize,
        requestAttributes(secure = true, engine.some),
        executionContext,
        serviceErrorHandler,
        responseHeaderTimeout,
        idleTimeout,
        scheduler
      )

    Future.successful {
      getContext() match {
        case Some((ctx, clientAuth)) =>
          val engine = ctx.createSSLEngine()
          engine.setUseClientMode(false)

          clientAuth match {
            case SSLClientAuthMode.NotRequested =>
              engine.setWantClientAuth(false)
              engine.setNeedClientAuth(false)

            case SSLClientAuthMode.Requested =>
              engine.setWantClientAuth(true)

            case SSLClientAuthMode.Required =>
              engine.setNeedClientAuth(true)
          }

          LeafBuilder(
            if (isHttp2Enabled) http2Stage(engine)
            else http1Stage(secure = true, engine.some)
          ).prepend(new SSLStage(engine))

        case None =>
          if (isHttp2Enabled)
            logger.warn("HTTP/2 support requires TLS. Falling back to HTTP/1.")
          LeafBuilder(http1Stage(secure = false, None))
      }
    }
  }

  def resource: Resource[F, Server[F]] = tickWheelResource.flatMap { scheduler =>
    def resolveAddress(address: InetSocketAddress) =
      if (address.isUnresolved) new InetSocketAddress(address.getHostName, address.getPort)
      else address

    val mkFactory: Resource[F, ServerChannelGroup] = Resource.make(F.delay {
      if (isNio2)
        NIO2SocketServerGroup.fixedGroup(connectorPoolSize, bufferSize, channelOptions)
      else
        NIO1SocketServerGroup.fixedGroup(connectorPoolSize, bufferSize, channelOptions)
    })(factory => F.delay { factory.closeGroup() })

    def mkServerChannel(factory: ServerChannelGroup): Resource[F, ServerChannel] =
      Resource.make(F.delay {
        val address = resolveAddress(socketAddress)

        // if we have a Failure, it will be caught by the effect
        factory.bind(address, pipelineFactory(scheduler)).get
      })(serverChannel => F.delay { serverChannel.close() })

    def logStart(server: Server[F]): Resource[F, Unit] =
      Resource.liftF(F.delay {
        Option(banner)
          .filter(_.nonEmpty)
          .map(_.mkString("\n", "\n", ""))
          .foreach(logger.info(_))

        logger.info(
          s"http4s v${BuildInfo.version} on blaze v${BlazeBuildInfo.version} started at ${server.baseUri}")
      })

    mkFactory
      .flatMap(mkServerChannel)
      .map[Server[F]] { serverChannel =>
        new Server[F] {
          val address: InetSocketAddress =
            serverChannel.socketAddress

          val isSecure = sslBits.isDefined

          override def toString: String =
            s"BlazeServer($address)"
        }
      }
      .flatTap(logStart)
  }

  private def getContext(): Option[(SSLContext, SSLClientAuthMode)] = sslBits.map {
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

object BlazeServerBuilder {
  def apply[F[_]](implicit F: ConcurrentEffect[F], timer: Timer[F]): BlazeServerBuilder[F] =
    new BlazeServerBuilder(
      socketAddress = defaults.SocketAddress,
      executionContext = ExecutionContext.global,
      responseHeaderTimeout = 1.minute,
      idleTimeout = defaults.IdleTimeout,
      isNio2 = false,
      connectorPoolSize = DefaultPoolSize,
      bufferSize = 64 * 1024,
      enableWebSockets = true,
      sslBits = None,
      isHttp2Enabled = false,
      maxRequestLineLen = 4 * 1024,
      maxHeadersLen = 40 * 1024,
      chunkBufferMaxSize = 1024 * 1024,
      httpApp = defaultApp[F],
      serviceErrorHandler = DefaultServiceErrorHandler[F],
      banner = defaults.Banner,
      channelOptions = ChannelOptions(Vector.empty)
    )

  private def defaultApp[F[_]: Applicative]: HttpApp[F] =
    Kleisli(_ => Response[F](Status.NotFound).pure[F])
}
