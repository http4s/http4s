package org.http4s
package server
package blaze

import cats.effect._
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.security.{KeyStore, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import org.http4s.blaze.channel
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.syntax.all._
import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * BlazeBuilder is the component for the builder pattern aggregating
  * different components to finally serve requests.
  *
  * Variables:
  * @param socketAddress: Socket Address the server will be mounted at
  * @param executionContext: Execution Context the underlying blaze futures
  *    will be executed upon.
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
  * @param serviceMounts: The services that are mounted on this server to serve.
  *    These services get assembled into a Router with the longer prefix winning.
  * @param serviceErrorHandler: The last resort to recover and generate a response
  *    this is necessary to recover totality from the error condition.
  * @param banner: Pretty log to display on server start. An empty sequence
  *    such as Nil disables this
  */
@deprecated("Use BlazeServerBuilder instead", "0.19.0-M2")
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
    serviceErrorHandler: ServiceErrorHandler[F],
    banner: immutable.Seq[String]
)(implicit protected val F: ConcurrentEffect[F], timer: Timer[F])
    extends ServerBuilder[F] {
  type Self = BlazeBuilder[F]

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
      serviceErrorHandler: ServiceErrorHandler[F] = serviceErrorHandler,
      banner: immutable.Seq[String] = banner
  ): Self =
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
      serviceErrorHandler,
      banner
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

  def withExecutionContext(executionContext: ExecutionContext): BlazeBuilder[F] =
    copy(executionContext = executionContext)

  def withIdleTimeout(idleTimeout: Duration): Self = copy(idleTimeout = idleTimeout)

  def withConnectorPoolSize(size: Int): Self = copy(connectorPoolSize = size)

  def withBufferSize(size: Int): Self = copy(bufferSize = size)

  def withNio2(isNio2: Boolean): Self = copy(isNio2 = isNio2)

  def withWebSockets(enableWebsockets: Boolean): Self =
    copy(enableWebSockets = enableWebsockets)

  def enableHttp2(enabled: Boolean): Self = copy(http2Support = enabled)

  def mountService(service: HttpRoutes[F], prefix: String): Self = {
    val prefixedService =
      if (prefix.isEmpty || prefix == "/") service
      else {
        val newCaret = (if (prefix.startsWith("/")) 0 else 1) + prefix.length

        service.local { req: Request[F] =>
          req.withAttribute(Request.Keys.PathInfoCaret, newCaret)
        }
      }
    copy(serviceMounts = serviceMounts :+ ServiceMount[F](prefixedService, prefix))
  }

  def withServiceErrorHandler(serviceErrorHandler: ServiceErrorHandler[F]): Self =
    copy(serviceErrorHandler = serviceErrorHandler)

  def withBanner(banner: immutable.Seq[String]): Self =
    copy(banner = banner)

  def resource: Resource[F, Server[F]] = {
    val httpApp = Router(serviceMounts.map(mount => mount.prefix -> mount.service): _*).orNotFound
    var b = BlazeServerBuilder[F]
      .bindSocketAddress(socketAddress)
      .withExecutionContext(executionContext)
      .withIdleTimeout(idleTimeout)
      .withNio2(isNio2)
      .withConnectorPoolSize(connectorPoolSize)
      .withBufferSize(bufferSize)
      .withWebSockets(enableWebSockets)
      .enableHttp2(isHttp2Enabled)
      .withMaxRequestLineLength(maxRequestLineLen)
      .withMaxHeadersLength(maxHeadersLen)
      .withHttpApp(httpApp)
      .withServiceErrorHandler(serviceErrorHandler)
      .withBanner(banner)
    getContext().foreach {
      case (ctx, clientAuth) =>
        b = b.withSSLContext(ctx, clientAuth)
    }
    b.resource
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

@deprecated("Use BlazeServerBuilder instead", "0.20.0-RC1")
object BlazeBuilder {
  def apply[F[_]](implicit F: ConcurrentEffect[F], timer: Timer[F]): BlazeBuilder[F] =
    new BlazeBuilder(
      socketAddress = ServerBuilder.DefaultSocketAddress,
      executionContext = ExecutionContext.global,
      idleTimeout = IdleTimeoutSupport.DefaultIdleTimeout,
      isNio2 = false,
      connectorPoolSize = channel.DefaultPoolSize,
      bufferSize = 64 * 1024,
      enableWebSockets = true,
      sslBits = None,
      isHttp2Enabled = false,
      maxRequestLineLen = 4 * 1024,
      maxHeadersLen = 40 * 1024,
      serviceMounts = Vector.empty,
      serviceErrorHandler = DefaultServiceErrorHandler,
      banner = ServerBuilder.DefaultBanner
    )
}

private final case class ServiceMount[F[_]](service: HttpRoutes[F], prefix: String)
