package org.http4s
package server

import cats.implicits._
import cats.effect._
import cats.effect.concurrent.Ref
import fs2._
import fs2.concurrent.{Signal, SignallingRef}
import java.net.{InetAddress, InetSocketAddress}
import javax.net.ssl.SSLContext
import org.http4s.internal.BackendBuilder
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import scala.collection.immutable

trait ServerBuilder[F[_]] extends BackendBuilder[F, Server[F]] {
  type Self <: ServerBuilder[F]

  protected implicit def F: Concurrent[F]

  def bindSocketAddress(socketAddress: InetSocketAddress): Self

  final def bindHttp(port: Int = defaults.HttpPort, host: String = defaults.Host): Self =
    bindSocketAddress(InetSocketAddress.createUnresolved(host, port))

  final def bindLocal(port: Int): Self = bindHttp(port, defaults.Host)

  final def bindAny(host: String = defaults.Host): Self = bindHttp(0, host)

  /** Sets the handler for errors thrown invoking the service.  Is not
    * guaranteed to be invoked on errors on the server backend, such as
    * parsing a request or handling a context timeout.
    */
  def withServiceErrorHandler(serviceErrorHandler: ServiceErrorHandler[F]): Self

  /** Returns a Server resource.  The resource is not acquired until the
    * server is started and ready to accept requests.
    */
  def resource: Resource[F, Server[F]]

  /**
    * Runs the server as a process that never emits.  Useful for a server
    * that runs for the rest of the JVM's life.
    */
  final def serve: Stream[F, ExitCode] =
    for {
      signal <- Stream.eval(SignallingRef[F, Boolean](false))
      exitCode <- Stream.eval(Ref[F].of(ExitCode.Success))
      serve <- serveWhile(signal, exitCode)
    } yield serve

  /**
    * Runs the server as a Stream that emits only when the terminated signal becomes true.
    * Useful for servers with associated lifetime behaviors.
    */
  final def serveWhile(
      terminateWhenTrue: Signal[F, Boolean],
      exitWith: Ref[F, ExitCode]): Stream[F, ExitCode] =
    Stream.resource(resource) *> (terminateWhenTrue.discrete
      .takeWhile(_ === false)
      .drain ++ Stream.eval(exitWith.get))

  /** Set the banner to display when the server starts up */
  def withBanner(banner: immutable.Seq[String]): Self

  /** Disable the banner when the server starts up */
  final def withoutBanner: Self = withBanner(immutable.Seq.empty)
}

object ServerBuilder {
  @deprecated("Use InetAddress.getLoopbackAddress.getHostAddress", "0.20.0-M2")
  val LoopbackAddress = InetAddress.getLoopbackAddress.getHostAddress
  @deprecated("Use org.http4s.server.defaults.Host", "0.20.0-M2")
  val DefaultHost = defaults.Host
  @deprecated("Use org.http4s.server.defaults.HttpPort", "0.20.0-M2")
  val DefaultHttpPort = defaults.HttpPort
  @deprecated("Use org.http4s.server.defaults.SocketAddress", "0.20.0-M2")
  val DefaultSocketAddress = defaults.SocketAddress
  @deprecated("Use org.http4s.server.defaults.Banner", "0.20.0-M2")
  val DefaultBanner = defaults.Banner
}

object IdleTimeoutSupport {
  @deprecated("Moved to org.http4s.server.defaults.IdleTimeout", "0.20.0-M2")
  val DefaultIdleTimeout = defaults.IdleTimeout
}

object AsyncTimeoutSupport {
  @deprecated("Moved to org.http4s.server.defaults.AsyncTimeout", "0.20.0-M2")
  val DefaultAsyncTimeout = defaults.AsyncTimeout
}

sealed trait SSLConfig

final case class KeyStoreBits(
    keyStore: StoreInfo,
    keyManagerPassword: String,
    protocol: String,
    trustStore: Option[StoreInfo],
    clientAuth: SSLClientAuthMode)
    extends SSLConfig

final case class SSLContextBits(sslContext: SSLContext, clientAuth: SSLClientAuthMode)
    extends SSLConfig

object SSLKeyStoreSupport {
  final case class StoreInfo(path: String, password: String)
}
