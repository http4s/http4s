package org.http4s
package server

import cats.effect._
import fs2._
import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait ServerBuilder[F[_]] {
  import ServerBuilder._

  type Self <: ServerBuilder[F]

  def bindSocketAddress(socketAddress: InetSocketAddress): Self

  final def bindHttp(port: Int = DefaultHttpPort, host: String = DefaultHost): Self =
    bindSocketAddress(InetSocketAddress.createUnresolved(host, port))

  final def bindLocal(port: Int): Self = bindHttp(port, DefaultHost)

  final def bindAny(host: String = DefaultHost): Self = bindHttp(0, host)

  @deprecated("Use withExecutionContext", "0.17")
  def withExecutorService(executorService: ExecutorService): Self =
    withExecutionContext(ExecutionContext.fromExecutorService(executorService))

  @deprecated("Use withExecutionContext", "0.17.0")
  def withServiceExecutor(executorService: ExecutorService): Self =
    withExecutorService(executorService)

  def withExecutionContext(executionContext: ExecutionContext): Self

  /** Sets the handler for errors thrown invoking the service.  Is not
    * guaranteed to be invoked on errors on the server backend, such as
    * parsing a request or handling a context timeout.
    */
  def withServiceErrorHandler(serviceErrorHandler: ServiceErrorHandler[F]): Self

  def mountService(service: HttpService[F], prefix: String = ""): Self

  /** Returns a task to start a server.  The task completes with a
    * reference to the server when it has started.
    */
  def start: F[Server[F]]

  /**
    * Runs the server as a process that never emits.  Useful for a server
    * that runs for the rest of the JVM's life.
    */
  final def serve(implicit F: Async[F]): Stream[F, Nothing] =
    Stream.bracket(start)((_: Server[F]) => Stream.eval_(F.async[Unit](_ => ())), _.shutdown)
}

object ServerBuilder {
  // Defaults for core server builder functionality
  val LoopbackAddress = InetAddress.getLoopbackAddress.getHostAddress
  val DefaultHost = LoopbackAddress
  val DefaultHttpPort = 8080
  val DefaultSocketAddress = InetSocketAddress.createUnresolved(DefaultHost, DefaultHttpPort)
}

trait IdleTimeoutSupport[F[_]] { this: ServerBuilder[F] =>
  def withIdleTimeout(idleTimeout: Duration): Self
}
object IdleTimeoutSupport {
  val DefaultIdleTimeout = 30.seconds
}

trait AsyncTimeoutSupport[F[_]] { this: ServerBuilder[F] =>
  def withAsyncTimeout(asyncTimeout: Duration): Self
}
object AsyncTimeoutSupport {
  val DefaultAsyncTimeout = 30.seconds
}

sealed trait SSLConfig

final case class KeyStoreBits(
    keyStore: StoreInfo,
    keyManagerPassword: String,
    protocol: String,
    trustStore: Option[StoreInfo],
    clientAuth: Boolean)
    extends SSLConfig

final case class SSLContextBits(sslContext: SSLContext, clientAuth: Boolean) extends SSLConfig

trait SSLKeyStoreSupport[F[_]] { this: ServerBuilder[F] =>
  def withSSL(
      keyStore: StoreInfo,
      keyManagerPassword: String,
      protocol: String = "TLS",
      trustStore: Option[StoreInfo] = None,
      clientAuth: Boolean = false): Self
}
object SSLKeyStoreSupport {
  final case class StoreInfo(path: String, password: String)
}

trait SSLContextSupport[F[_]] { this: ServerBuilder[F] =>
  def withSSLContext(sslContext: SSLContext, clientAuth: Boolean = false): Self
}

/*
trait MetricsSupport { this: ServerBuilder =>
  /**
 * Triggers collection of backend-specific Metrics into the specified `MetricRegistry`.
 */
  def withMetricRegistry(metricRegistry: MetricRegistry): Self

  /** Sets the prefix for metrics gathered by the server.*/
  def withMetricPrefix(metricPrefix: String): Self
}
object MetricsSupport {
  val DefaultPrefix = "org.http4s.server"
}
 */

trait WebSocketSupport[F[_]] { this: ServerBuilder[F] =>
  /* Enable websocket support */
  def withWebSockets(enableWebsockets: Boolean): Self
}
