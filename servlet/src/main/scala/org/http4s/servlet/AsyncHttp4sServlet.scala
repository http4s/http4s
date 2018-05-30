package org.http4s
package servlet

import cats.data.OptionT
import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2.async
import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.http4s.server._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

class AsyncHttp4sServlet[F[_]](
    service: HttpRoutes[F],
    asyncTimeout: Duration = Duration.Inf,
    implicit private[this] val executionContext: ExecutionContext = ExecutionContext.global,
    private[this] var servletIo: ServletIo[F],
    serviceErrorHandler: ServiceErrorHandler[F])(implicit F: ConcurrentEffect[F])
    extends Http4sServlet[F](service, servletIo) {
  private val asyncTimeoutMillis = if (asyncTimeout.isFinite()) asyncTimeout.toMillis else -1 // -1 == Inf

  private[this] val optionTSync = Sync[OptionT[F, ?]]

  override def init(config: ServletConfig): Unit = {
    super.init(config)

    verifyServletIo(servletApiVersion)
    logServletIo()
  }

  // TODO This is a dodgy check.  It will have already triggered class loading of javax.servlet.WriteListener.
  // Remove when we can break binary compatibility.
  private def verifyServletIo(servletApiVersion: ServletApiVersion): Unit = servletIo match {
    case NonBlockingServletIo(chunkSize) if servletApiVersion < ServletApiVersion(3, 1) =>
      logger.warn(
        "Non-blocking servlet I/O requires Servlet API >= 3.1. Falling back to blocking I/O.")
      servletIo = BlockingServletIo[F](chunkSize)
    case _ => // cool
  }

  private def logServletIo(): Unit =
    logger.info(servletIo match {
      case BlockingServletIo(chunkSize) => s"Using blocking servlet I/O with chunk size $chunkSize"
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
      async.unsafeRunAsync(
        toRequest(servletRequest).fold(
          onParseFailure(_, servletResponse, bodyWriter),
          handleRequest(ctx, _, bodyWriter)
        )) {
        case Right(()) =>
          IO(ctx.complete())
        case Left(t) =>
          IO(errorHandler(servletRequest, servletResponse)(t))
      }
    } catch errorHandler(servletRequest, servletResponse)

  private def handleRequest(
      ctx: AsyncContext,
      request: Request[F],
      bodyWriter: BodyWriter[F]): F[Unit] = {
    val timeout = F.async[Unit] { cb =>
      ctx.addListener(new AsyncTimeoutHandler(cb))
    }
    val response = Async.shift(executionContext) *>
      optionTSync
        .suspend(serviceFn(request))
        .getOrElse(Response.notFound)
        .recoverWith(serviceErrorHandler(request))
    val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
    F.race(timeout, response).flatMap {
      case Left(()) =>
        // TODO replace with F.never in cats-effect-1.0
        // The F.never is so we don't interrupt the rendering of the timeout response
        renderResponse(Response.timeout[F], servletResponse, bodyWriter, F.async(cb => ()))
      case Right(resp) =>
        renderResponse(resp, servletResponse, bodyWriter, timeout)
    }
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
      async
        .unsafeRunAsync(renderResponse(response, servletResponse, NullBodyWriter, F.pure(()))) {
          _ =>
            IO {
              if (servletRequest.isAsyncStarted)
                servletRequest.getAsyncContext.complete()
            }
        }
  }

  private class AsyncTimeoutHandler(cb: Callback[Unit]) extends AbstractAsyncListener {
    override def onTimeout(event: AsyncEvent): Unit = {
      val req = event.getAsyncContext.getRequest.asInstanceOf[HttpServletRequest]
      logger.info(s"Request timed out: ${req.getMethod} ${req.getServletPath}${req.getPathInfo}")
      cb(Right(()))
    }
  }
}

object AsyncHttp4sServlet {
  def apply[F[_]: ConcurrentEffect](
      service: HttpRoutes[F],
      asyncTimeout: Duration = Duration.Inf,
      executionContext: ExecutionContext = ExecutionContext.global): AsyncHttp4sServlet[F] =
    new AsyncHttp4sServlet[F](
      service,
      asyncTimeout,
      executionContext,
      BlockingServletIo[F](DefaultChunkSize),
      DefaultServiceErrorHandler
    )
}
