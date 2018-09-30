package org.http4s
package servlet

import cats.data.OptionT
import cats.effect._
import cats.effect.concurrent.Deferred
import cats.implicits.{catsSyntaxEither => _, _}
import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.http4s.internal.loggingAsyncCallback
import org.http4s.server._
import scala.concurrent.duration.Duration

class AsyncHttp4sServlet[F[_]](
    service: HttpRoutes[F],
    asyncTimeout: Duration = Duration.Inf,
    private[this] var servletIo: ServletIo[F],
    serviceErrorHandler: ServiceErrorHandler[F])(implicit F: ConcurrentEffect[F])
    extends Http4sServlet[F](service, servletIo) {
  private val asyncTimeoutMillis = if (asyncTimeout.isFinite()) asyncTimeout.toMillis else -1 // -1 == Inf

  private[this] val optionTSync = Sync[OptionT[F, ?]]

  override def init(config: ServletConfig): Unit = {
    super.init(config)
    logServletIo()
  }

  private def logServletIo(): Unit =
    logger.info(servletIo match {
      case BlockingServletIo(chunkSize, _) =>
        s"Using blocking servlet I/O with chunk size $chunkSize"
      case NonBlockingServletIo(chunkSize) =>
        s"Using non-blocking servlet I/O with chunk size $chunkSize"
    })

  override def service(
      servletRequest: HttpServletRequest,
      servletResponse: HttpServletResponse): Unit =
    try {
      val ctx = servletRequest.startAsync()
      ctx.setTimeout(asyncTimeoutMillis)
      // Must be done on the container thread for Tomcat's sake when using async I/O.
      val bodyWriter = servletIo.initWriter(servletResponse)
      F.runAsync(
          toRequest(servletRequest).fold(
            onParseFailure(_, servletResponse, bodyWriter),
            handleRequest(ctx, _, bodyWriter)
          )) {
          case Right(()) =>
            IO(ctx.complete())
          case Left(t) =>
            IO(errorHandler(servletRequest, servletResponse)(t))
        }
        .unsafeRunSync()
    } catch errorHandler(servletRequest, servletResponse)

  private def handleRequest(
      ctx: AsyncContext,
      request: Request[F],
      bodyWriter: BodyWriter[F]): F[Unit] = Deferred[F, Unit].flatMap { gate =>
    // It is an error to add a listener to an async context that is
    // already completed, so we must take care to add the listener
    // before the response can complete.
    val timeout =
      F.asyncF[Response[F]](cb => gate.complete(ctx.addListener(new AsyncTimeoutHandler(cb))))
    val response =
      gate.get *>
        optionTSync
          .suspend(serviceFn(request))
          .getOrElse(Response.notFound)
          .recoverWith(serviceErrorHandler(request))
    val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
    F.race(timeout, response).flatMap(r => renderResponse(r.merge, servletResponse, bodyWriter))
  }

  private def errorHandler(
      servletRequest: ServletRequest,
      servletResponse: HttpServletResponse): PartialFunction[Throwable, Unit] = {
    case t: Throwable if servletResponse.isCommitted =>
      logger.error(t)("Error processing request after response was committed")

    case t: Throwable =>
      logger.error(t)("Error processing request")
      val response = Response[F](Status.InternalServerError)
      // We don't know what I/O mode we're in here, and we're not rendering a body
      // anyway, so we use a NullBodyWriter.
      val f = renderResponse(response, servletResponse, NullBodyWriter) *>
        F.delay(
          if (servletRequest.isAsyncStarted)
            servletRequest.getAsyncContext.complete()
        )
      F.runAsync(f)(loggingAsyncCallback(logger)).unsafeRunSync()
  }

  private class AsyncTimeoutHandler(cb: Callback[Response[F]]) extends AbstractAsyncListener {
    override def onTimeout(event: AsyncEvent): Unit = {
      val req = event.getAsyncContext.getRequest.asInstanceOf[HttpServletRequest]
      logger.info(s"Request timed out: ${req.getMethod} ${req.getServletPath}${req.getPathInfo}")
      cb(Right(Response.timeout[F]))
    }
  }
}

object AsyncHttp4sServlet {
  def apply[F[_]: ConcurrentEffect](
      service: HttpRoutes[F],
      asyncTimeout: Duration = Duration.Inf): AsyncHttp4sServlet[F] =
    new AsyncHttp4sServlet[F](
      service,
      asyncTimeout,
      NonBlockingServletIo[F](DefaultChunkSize),
      DefaultServiceErrorHandler
    )
}
