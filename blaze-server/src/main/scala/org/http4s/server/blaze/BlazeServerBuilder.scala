/*
 * Copyright 2014 http4s.org
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

package org.http4s
package server
package blaze

import cats.{Alternative, Applicative}
import cats.data.Kleisli
import cats.effect.{Async, Resource, Sync}
import cats.effect.std.Dispatcher
import cats.syntax.all._
import com.comcast.ip4s.{IpAddress, Port, SocketAddress}
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.{KeyStore, Security}
import java.util.concurrent.ThreadFactory
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLEngine, SSLParameters, TrustManagerFactory}
import org.http4s.blaze.{BuildInfo => BlazeBuildInfo}
import org.http4s.blaze.channel.{
  ChannelOptions,
  DefaultPoolSize,
  ServerChannel,
  ServerChannelGroup,
  SocketConnection
}
import org.http4s.blaze.channel.nio1.NIO1SocketServerGroup
import org.http4s.blaze.http.http2.server.ALPNServerSelector
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.SSLStage
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.{BlazeBackendBuilder, tickWheelResource}
import org.http4s.internal.threads.threadFactory
import org.http4s.internal.tls.{deduceKeyLength, getCertChain}
import org.http4s.server.ServerRequestKeys
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.blaze.BlazeServerBuilder._
import org.log4s.getLogger
import org.typelevel.vault._
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scodec.bits.ByteVector

/** BlazeServerBuilder is the component for the builder pattern aggregating
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
  * @param maxConnections: The maximum number of client connections that may be active at any time.
  */
class BlazeServerBuilder[F[_]] private (
    socketAddress: InetSocketAddress,
    executionContextF: F[ExecutionContext],
    responseHeaderTimeout: Duration,
    idleTimeout: Duration,
    connectorPoolSize: Int,
    bufferSize: Int,
    selectorThreadFactory: ThreadFactory,
    enableWebSockets: Boolean,
    sslConfig: SslConfig[F],
    isHttp2Enabled: Boolean,
    maxRequestLineLen: Int,
    maxHeadersLen: Int,
    chunkBufferMaxSize: Int,
    httpApp: HttpApp[F],
    serviceErrorHandler: ServiceErrorHandler[F],
    banner: immutable.Seq[String],
    maxConnections: Int,
    val channelOptions: ChannelOptions
)(implicit protected val F: Async[F])
    extends ServerBuilder[F]
    with BlazeBackendBuilder[Server] {
  type Self = BlazeServerBuilder[F]

  private[this] val logger = getLogger

  private def copy(
      socketAddress: InetSocketAddress = socketAddress,
      executionContextF: F[ExecutionContext] = executionContextF,
      idleTimeout: Duration = idleTimeout,
      responseHeaderTimeout: Duration = responseHeaderTimeout,
      connectorPoolSize: Int = connectorPoolSize,
      bufferSize: Int = bufferSize,
      selectorThreadFactory: ThreadFactory = selectorThreadFactory,
      enableWebSockets: Boolean = enableWebSockets,
      sslConfig: SslConfig[F] = sslConfig,
      http2Support: Boolean = isHttp2Enabled,
      maxRequestLineLen: Int = maxRequestLineLen,
      maxHeadersLen: Int = maxHeadersLen,
      chunkBufferMaxSize: Int = chunkBufferMaxSize,
      httpApp: HttpApp[F] = httpApp,
      serviceErrorHandler: ServiceErrorHandler[F] = serviceErrorHandler,
      banner: immutable.Seq[String] = banner,
      maxConnections: Int = maxConnections,
      channelOptions: ChannelOptions = channelOptions
  ): Self =
    new BlazeServerBuilder(
      socketAddress,
      executionContextF,
      responseHeaderTimeout,
      idleTimeout,
      connectorPoolSize,
      bufferSize,
      selectorThreadFactory,
      enableWebSockets,
      sslConfig,
      http2Support,
      maxRequestLineLen,
      maxHeadersLen,
      chunkBufferMaxSize,
      httpApp,
      serviceErrorHandler,
      banner,
      maxConnections,
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

  @deprecated(
    "Build an `SSLContext` from the first four parameters and use `withSslContext` (note lowercase). To also request client certificates, use `withSslContextAndParameters, calling either `.setWantClientAuth(true)` or `setNeedClientAuth(true)` on the `SSLParameters`.",
    "0.21.0-RC3")
  def withSSL(
      keyStore: StoreInfo,
      keyManagerPassword: String,
      protocol: String = "TLS",
      trustStore: Option[StoreInfo] = None,
      clientAuth: SSLClientAuthMode = SSLClientAuthMode.NotRequested): Self = {
    val bits = new KeyStoreBits[F](keyStore, keyManagerPassword, protocol, trustStore, clientAuth)
    copy(sslConfig = bits)
  }

  @deprecated(
    "Use `withSslContext` (note lowercase). To request client certificates, use `withSslContextAndParameters, calling either `.setWantClientAuth(true)` or `setNeedClientAuth(true)` on the `SSLParameters`.",
    "0.21.0-RC3")
  def withSSLContext(
      sslContext: SSLContext,
      clientAuth: SSLClientAuthMode = SSLClientAuthMode.NotRequested): Self =
    copy(sslConfig = new ContextWithClientAuth[F](sslContext, clientAuth))

  /** Configures the server with TLS, using the provided `SSLContext` and its
    * default `SSLParameters`
    */
  def withSslContext(sslContext: SSLContext): Self =
    copy(sslConfig = new ContextOnly[F](sslContext))

  /** Configures the server with TLS, using the provided `SSLContext` and `SSLParameters`. */
  def withSslContextAndParameters(sslContext: SSLContext, sslParameters: SSLParameters): Self =
    copy(sslConfig = new ContextWithParameters[F](sslContext, sslParameters))

  def withoutSsl: Self =
    copy(sslConfig = new NoSsl[F]())

  override def bindSocketAddress(socketAddress: InetSocketAddress): Self =
    copy(socketAddress = socketAddress)

  def withExecutionContext(executionContext: ExecutionContext): BlazeServerBuilder[F] =
    withExecutionContextF(executionContext.pure[F])

  def withExecutionContextF(executionContextF: F[ExecutionContext]): BlazeServerBuilder[F] =
    copy(executionContextF = executionContextF)

  def withIdleTimeout(idleTimeout: Duration): Self = copy(idleTimeout = idleTimeout)

  def withResponseHeaderTimeout(responseHeaderTimeout: Duration): Self =
    copy(responseHeaderTimeout = responseHeaderTimeout)

  def withConnectorPoolSize(size: Int): Self = copy(connectorPoolSize = size)

  def withBufferSize(size: Int): Self = copy(bufferSize = size)

  def withSelectorThreadFactory(selectorThreadFactory: ThreadFactory): Self =
    copy(selectorThreadFactory = selectorThreadFactory)

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

  def withMaxConnections(maxConnections: Int): BlazeServerBuilder[F] =
    copy(maxConnections = maxConnections)

  private def pipelineFactory(
      scheduler: TickWheelExecutor,
      engineConfig: Option[(SSLContext, SSLEngine => Unit)],
      dispatcher: Dispatcher[F]
  )(conn: SocketConnection): Future[LeafBuilder[ByteBuffer]] = {
    def requestAttributes(secure: Boolean, optionalSslEngine: Option[SSLEngine]): () => Vault =
      (conn.local, conn.remote) match {
        case (local: InetSocketAddress, remote: InetSocketAddress) =>
          () =>
            Vault.empty
              .insert(
                Request.Keys.ConnectionInfo,
                Request.Connection(
                  local = SocketAddress(
                    IpAddress.fromBytes(local.getAddress.getAddress).get,
                    Port.fromInt(local.getPort).get),
                  remote = SocketAddress(
                    IpAddress.fromBytes(remote.getAddress.getAddress).get,
                    Port.fromInt(remote.getPort).get),
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
                      Option(session.getCipherSuite).map(deduceKeyLength),
                      getCertChain(session).some).mapN(SecureSession.apply)
                  }
              )
        case _ =>
          () => Vault.empty
      }

    def http1Stage(executionContext: ExecutionContext, secure: Boolean, engine: Option[SSLEngine]) =
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
        scheduler,
        dispatcher
      )

    def http2Stage(executionContext: ExecutionContext, engine: SSLEngine): ALPNServerSelector =
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
        scheduler,
        dispatcher
      )

    dispatcher.unsafeToFuture {
      executionContextF.map { executionContext =>
        engineConfig match {
          case Some((ctx, configure)) =>
            val engine = ctx.createSSLEngine()
            engine.setUseClientMode(false)
            configure(engine)

            LeafBuilder(
              if (isHttp2Enabled) http2Stage(executionContext, engine)
              else http1Stage(executionContext, secure = true, engine.some)
            ).prepend(new SSLStage(engine))

          case None =>
            if (isHttp2Enabled)
              logger.warn("HTTP/2 support requires TLS. Falling back to HTTP/1.")
            LeafBuilder(http1Stage(executionContext, secure = false, None))
        }
      }
    }
  }

  def resource: Resource[F, Server] = {
    def resolveAddress(address: InetSocketAddress) =
      if (address.isUnresolved) new InetSocketAddress(address.getHostName, address.getPort)
      else address

    val mkFactory: Resource[F, ServerChannelGroup] = Resource.make(F.delay {
      NIO1SocketServerGroup
        .fixed(
          workerThreads = connectorPoolSize,
          bufferSize = bufferSize,
          channelOptions = channelOptions,
          selectorThreadFactory = selectorThreadFactory,
          maxConnections = maxConnections
        )
    })(factory => F.delay(factory.closeGroup()))

    def mkServerChannel(
        factory: ServerChannelGroup,
        scheduler: TickWheelExecutor,
        dispatcher: Dispatcher[F]): Resource[F, ServerChannel] =
      Resource.make(
        for {
          ctxOpt <- sslConfig.makeContext
          engineCfg = ctxOpt.map(ctx => (ctx, sslConfig.configureEngine _))
          address = resolveAddress(socketAddress)
        } yield factory.bind(address, pipelineFactory(scheduler, engineCfg, dispatcher)).get
      )(serverChannel => F.delay(serverChannel.close()))

    def logStart(server: Server): Resource[F, Unit] =
      Resource.eval(F.delay {
        Option(banner)
          .filter(_.nonEmpty)
          .map(_.mkString("\n", "\n", ""))
          .foreach(logger.info(_))

        logger.info(
          s"http4s v${BuildInfo.version} on blaze v${BlazeBuildInfo.version} started at ${server.baseUri}")
      })

    for {
      // blaze doesn't have graceful shutdowns, which means it may continue to submit effects,
      // ever after the server has acknowledged shutdown, so we just need to allocate
      dispatcher <- Resource.eval(Dispatcher[F].allocated.map(_._1))
      scheduler <- tickWheelResource

      _ <- Resource.eval(verifyTimeoutRelations())

      factory <- mkFactory
      serverChannel <- mkServerChannel(factory, scheduler, dispatcher)
      server = new Server {
        val address: InetSocketAddress =
          serverChannel.socketAddress

        val isSecure = sslConfig.isSecure

        override def toString: String =
          s"BlazeServer($address)"
      }

      _ <- logStart(server)
    } yield server
  }

  private def verifyTimeoutRelations(): F[Unit] =
    F.delay {
      if (responseHeaderTimeout.isFinite && responseHeaderTimeout >= idleTimeout)
        logger.warn(
          s"responseHeaderTimeout ($responseHeaderTimeout) is >= idleTimeout ($idleTimeout). " +
            s"It is recommended to configure responseHeaderTimeout < idleTimeout, " +
            s"otherwise timeout responses won't be delivered to clients.")
    }
}

object BlazeServerBuilder {
  def apply[F[_]](executionContext: ExecutionContext)(implicit F: Async[F]): BlazeServerBuilder[F] =
    apply[F].withExecutionContext(executionContext)

  def apply[F[_]](implicit F: Async[F]): BlazeServerBuilder[F] =
    new BlazeServerBuilder(
      socketAddress = defaults.SocketAddress,
      executionContextF = F.executionContext,
      responseHeaderTimeout = defaults.ResponseTimeout,
      idleTimeout = defaults.IdleTimeout,
      connectorPoolSize = DefaultPoolSize,
      bufferSize = 64 * 1024,
      selectorThreadFactory = defaultThreadSelectorFactory,
      enableWebSockets = true,
      sslConfig = new NoSsl[F](),
      isHttp2Enabled = false,
      maxRequestLineLen = 4 * 1024,
      maxHeadersLen = defaults.MaxHeadersSize,
      chunkBufferMaxSize = 1024 * 1024,
      httpApp = defaultApp[F],
      serviceErrorHandler = DefaultServiceErrorHandler[F],
      banner = defaults.Banner,
      maxConnections = defaults.MaxConnections,
      channelOptions = ChannelOptions(Vector.empty)
    )

  private def defaultApp[F[_]: Applicative]: HttpApp[F] =
    Kleisli(_ => Response[F](Status.NotFound).pure[F])

  private def defaultThreadSelectorFactory: ThreadFactory =
    threadFactory(name = n => s"blaze-selector-${n}", daemon = false)

  private sealed trait SslConfig[F[_]] {
    def makeContext: F[Option[SSLContext]]
    def configureEngine(sslEngine: SSLEngine): Unit
    def isSecure: Boolean
  }

  private final class KeyStoreBits[F[_]](
      keyStore: StoreInfo,
      keyManagerPassword: String,
      protocol: String,
      trustStore: Option[StoreInfo],
      clientAuth: SSLClientAuthMode)(implicit F: Sync[F])
      extends SslConfig[F] {
    def makeContext =
      F.delay {
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
        context.some
      }
    def configureEngine(engine: SSLEngine) =
      configureEngineFromSslClientAuthMode(engine, clientAuth)
    def isSecure = true
  }

  private class ContextOnly[F[_]](sslContext: SSLContext)(implicit F: Applicative[F])
      extends SslConfig[F] {
    def makeContext = F.pure(sslContext.some)
    def configureEngine(engine: SSLEngine) = {
      val _ = engine
      ()
    }
    def isSecure = true
  }

  private class ContextWithParameters[F[_]](sslContext: SSLContext, sslParameters: SSLParameters)(
      implicit F: Applicative[F])
      extends SslConfig[F] {
    def makeContext = F.pure(sslContext.some)
    def configureEngine(engine: SSLEngine) = engine.setSSLParameters(sslParameters)
    def isSecure = true
  }

  private class ContextWithClientAuth[F[_]](sslContext: SSLContext, clientAuth: SSLClientAuthMode)(
      implicit F: Applicative[F])
      extends SslConfig[F] {
    def makeContext = F.pure(sslContext.some)
    def configureEngine(engine: SSLEngine) =
      configureEngineFromSslClientAuthMode(engine, clientAuth)
    def isSecure = true
  }

  private class NoSsl[F[_]]()(implicit F: Applicative[F]) extends SslConfig[F] {
    def makeContext = F.pure(None)
    def configureEngine(engine: SSLEngine) = {
      val _ = engine
      ()
    }
    def isSecure = false
  }

  private def configureEngineFromSslClientAuthMode(
      engine: SSLEngine,
      clientAuthMode: SSLClientAuthMode) =
    clientAuthMode match {
      case SSLClientAuthMode.Required => engine.setNeedClientAuth(true)
      case SSLClientAuthMode.Requested => engine.setWantClientAuth(true)
      case SSLClientAuthMode.NotRequested => ()
    }
}
