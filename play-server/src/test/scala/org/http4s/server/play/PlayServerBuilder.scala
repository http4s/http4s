package org.http4s.server.play

import java.net.InetSocketAddress

import cats.effect._
import org.http4s.HttpService
import org.http4s.server.{Server, ServerBuilder, ServiceErrorHandler}

import scala.collection.immutable
import scala.concurrent.ExecutionContext

class PlayServerBuilder[F[_]](
    hostname: String,
    services: Vector[(HttpService[F], String)],
    executionContext: ExecutionContext,
    port: Int,
)(implicit F: Effect[F])
    extends ServerBuilder[F] {
  type Self = PlayServerBuilder[F]

  private def copy(
      hostname: String = hostname,
      port: Int = port,
      executionContext: ExecutionContext = executionContext,
      services: Vector[(HttpService[F], String)] = services
  ): Self =
    new PlayServerBuilder(hostname, services, executionContext, port)

  override def mountService(service: HttpService[F], prefix: String): Self =
    copy(
      services = services :+ service -> prefix
    )

  def start: F[Server[F]] = F.delay {
    val server = new Server[F] {
      override def shutdown: F[Unit] = F.delay {}

      override def onShutdown(f: => Unit): this.type =
        this

      override def toString: String =
        s"PlayServer($address)"

      override def address: InetSocketAddress = new InetSocketAddress(hostname, port)

      override def isSecure: Boolean = false
    }
    server
  }

  override def bindSocketAddress(socketAddress: InetSocketAddress): PlayServerBuilder[F] = this

  override def withExecutionContext(executionContext: ExecutionContext): PlayServerBuilder[F] =
    copy(executionContext = executionContext)

  /** Sets the handler for errors thrown invoking the service.  Is not
    * guaranteed to be invoked on errors on the server backend, such as
    * parsing a request or handling a context timeout.
    */
  override def withServiceErrorHandler(
      serviceErrorHandler: ServiceErrorHandler[F]): PlayServerBuilder[F] = this

  /** Set the banner to display when the server starts up */
  override def withBanner(banner: immutable.Seq[String]): PlayServerBuilder[F] = this
}

object PlayServerBuilder {
  def apply[F[_]](implicit F: Effect[F]): PlayServerBuilder[F] =
    new PlayServerBuilder(
      hostname = ServerBuilder.DefaultSocketAddress.getHostString,
      services = Vector.empty,
      port = ServerBuilder.DefaultHttpPort,
      executionContext = ExecutionContext.global
    )
}
