package org.http4s
package servlet

import cats.data.OptionT
import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.http4s.server._
import scala.concurrent.ExecutionContext

class BlockingHttp4sServlet[F[_]](
    service: HttpRoutes[F],
    servletIo: BlockingServletIo[F],
    serviceErrorHandler: ServiceErrorHandler[F])(implicit F: Effect[F])
    extends Http4sServlet[F](service, servletIo) {

  private[this] val optionTSync = Sync[OptionT[F, ?]]

  override def service(
      servletRequest: HttpServletRequest,
      servletResponse: HttpServletResponse): Unit =
    try {
      val bodyWriter = servletIo.initWriter(servletResponse)

      val render = toRequest(servletRequest).fold(
        onParseFailure(_, servletResponse, bodyWriter),
        handleRequest(_, servletResponse, bodyWriter)
      )

      F.runAsync(render) {
          case Right(_) => IO.unit
          case Left(t) => IO(errorHandler(servletResponse)(t))
        }
        .unsafeRunSync()
    } catch errorHandler(servletResponse)

  private def handleRequest(
      request: Request[F],
      servletResponse: HttpServletResponse,
      bodyWriter: BodyWriter[F]): F[Unit] =
    // Note: We're catching silly user errors in the lift => flatten.
    optionTSync
      .suspend(serviceFn(request))
      .getOrElse(Response.notFound)
      .recoverWith(serviceErrorHandler(request))
      .flatMap(renderResponse(_, servletResponse, bodyWriter))

  private def errorHandler(servletResponse: HttpServletResponse): PartialFunction[Throwable, Unit] = {
    case t: Throwable if servletResponse.isCommitted =>
      logger.error(t)("Error processing request after response was committed")

    case t: Throwable =>
      logger.error(t)("Error processing request")
      val response = Response[F](Status.InternalServerError)
      // We don't know what I/O mode we're in here, and we're not rendering a body
      // anyway, so we use a NullBodyWriter.
      val render = renderResponse(response, servletResponse, NullBodyWriter)
      F.runAsync(render)(_ => IO.unit).unsafeRunSync()
  }
}

object BlockingHttp4sServlet {
  def apply[F[_]: Effect: ContextShift](
      service: HttpRoutes[F],
      blockingExecutionContext: ExecutionContext): BlockingHttp4sServlet[F] =
    new BlockingHttp4sServlet[F](
      service,
      BlockingServletIo(DefaultChunkSize, blockingExecutionContext),
      DefaultServiceErrorHandler
    )
}
