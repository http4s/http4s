package org.http4s
package server

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import org.http4s.server.SSLKeyStoreSupport.StoreInfo

import scala.concurrent.duration._

import fs2._

trait ServerBuilder {
  import ServerBuilder._

  type Self <: ServerBuilder

  def bindSocketAddress(socketAddress: InetSocketAddress): Self

  final def bindHttp(port: Int = DefaultHttpPort, host: String = DefaultHost): Self =
    bindSocketAddress(InetSocketAddress.createUnresolved(host, port))

  final def bindLocal(port: Int): Self = bindHttp(port, DefaultHost)

  final def bindAny(host: String = DefaultHost): Self = bindHttp(0, host)

  def withServiceExecutor(executorService: ExecutorService): Self

  def mountService(service: HttpService, prefix: String = ""): Self

  /** Returns a task to start a server.  The task completes with a
    * reference to the server when it has started.
    */
  def start: Task[Server]

  /** Convenience method to run a server.  The method blocks
    * until the server is started.
    */
  final def run: Server =
    start.unsafeRun
}

object ServerBuilder {
  // Defaults for core server builder functionality
  val LoopbackAddress = InetAddress.getLoopbackAddress.getHostAddress
  val DefaultHost = LoopbackAddress
  val DefaultHttpPort = 8080
  val DefaultSocketAddress = InetSocketAddress.createUnresolved(DefaultHost, DefaultHttpPort)
}

trait IdleTimeoutSupport { this: ServerBuilder =>
  def withIdleTimeout(idleTimeout: Duration): Self
}
object IdleTimeoutSupport {
  val DefaultIdleTimeout = 30.seconds
}

trait AsyncTimeoutSupport { this: ServerBuilder =>
  def withAsyncTimeout(asyncTimeout: Duration): Self
}
object AsyncTimeoutSupport {
  val DefaultAsyncTimeout = 30.seconds
}

sealed trait SSLConfig

final case class KeyStoreBits(keyStore: StoreInfo,
  keyManagerPassword: String,
  protocol: String,
  trustStore: Option[StoreInfo],
  clientAuth: Boolean) extends SSLConfig

final case class SSLContextBits(sslContext: SSLContext, clientAuth: Boolean) extends SSLConfig

trait SSLKeyStoreSupport { this: ServerBuilder =>
  def withSSL(keyStore: StoreInfo,
    keyManagerPassword: String,
              protocol: String = "TLS",
            trustStore: Option[StoreInfo] = None,
            clientAuth: Boolean = false): Self
}
object SSLKeyStoreSupport {
  final case class StoreInfo(path: String, password: String)
}

trait SSLContextSupport { this: ServerBuilder =>
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

trait WebSocketSupport { this: ServerBuilder =>
  /* Enable websocket support */
  def withWebSockets(enableWebsockets: Boolean): Self
}
