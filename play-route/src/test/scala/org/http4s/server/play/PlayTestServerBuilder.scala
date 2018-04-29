package org.http4s.server.play

import java.net.InetSocketAddress

import cats.effect._
import org.http4s.HttpService
import org.http4s.server.{Server, ServerBuilder, ServiceErrorHandler}
import play.api.{Configuration, Environment, Mode}

import scala.collection.immutable
import scala.concurrent.ExecutionContext

class PlayTestServerBuilder[F[_]](
    hostname: String,
    services: Vector[(HttpService[F], String)],
    executionContext: ExecutionContext,
    port: Int,
)(implicit F: Effect[F])
    extends ServerBuilder[F] {
  type Self = PlayTestServerBuilder[F]

  private implicit val ec: ExecutionContext = executionContext

  private def copy(
      hostname: String = hostname,
      port: Int = port,
      executionContext: ExecutionContext = executionContext,
      services: Vector[(HttpService[F], String)] = services
  ): Self =
    new PlayTestServerBuilder(hostname, services, executionContext, port)

  override def mountService(service: HttpService[F], prefix: String): Self =
    copy(
      services = services :+ service -> prefix
    )

  def start: F[Server[F]] = F.delay {

    val serverA = {
      import play.core.server.{AkkaHttpServer, _}
      val serverConfig =
        ServerConfig(
          port = Some(port),
          address = hostname
        ).copy(configuration = Configuration.load(Environment.simple()), mode = Mode.Test)
      AkkaHttpServer.fromRouterWithComponents(serverConfig) { _ =>
        services
          .map {
            case (service, prefix) =>
              PlayRouteBuilder
                .withPrefix(prefix, new PlayRouteBuilder(service).build)
          }
          .foldLeft(PartialFunction.empty: _root_.play.api.routing.Router.Routes)(_.orElse(_))
      }
    }

    val server = new Server[F] {
      override def shutdown: F[Unit] = F.delay { serverA.stop() }

      override def onShutdown(f: => Unit): this.type =
        this

      override def toString: String =
        s"PlayServer($address)"

      override def address: InetSocketAddress = new InetSocketAddress(hostname, port)

      override def isSecure: Boolean = false
    }
    server
  }

  override def bindSocketAddress(socketAddress: InetSocketAddress): PlayTestServerBuilder[F] = this

  override def withExecutionContext(executionContext: ExecutionContext): PlayTestServerBuilder[F] =
    copy(executionContext = executionContext)

  /** Sets the handler for errors thrown invoking the service.  Is not
    * guaranteed to be invoked on errors on the server backend, such as
    * parsing a request or handling a context timeout.
    */
  override def withServiceErrorHandler(
      serviceErrorHandler: ServiceErrorHandler[F]): PlayTestServerBuilder[F] = this

  /** Set the banner to display when the server starts up */
  override def withBanner(banner: immutable.Seq[String]): PlayTestServerBuilder[F] = this
}

object PlayTestServerBuilder {
  def apply[F[_]](implicit F: Effect[F]): PlayTestServerBuilder[F] =
    new PlayTestServerBuilder(
      hostname = ServerBuilder.DefaultSocketAddress.getHostString,
      services = Vector.empty,
      port = ServerBuilder.DefaultHttpPort,
      executionContext = ExecutionContext.global
    )
}
