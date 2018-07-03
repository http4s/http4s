package org.http4s
package servlet

import cats.data.OptionT
import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.http4s.internal.unsafeRunAsync
import org.http4s.server._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

class AsyncHttp4sServlet[F[_]](
    service: HttpRoutes[F],
    asyncTimeout: Duration = Duration.Inf,
    implicit private[this] val executionContext: ExecutionContext = ExecutionContext.global,
    private[this] var servletIo: ServletIo[F],
    serviceErrorHandler: ServiceErrorHandler[F])(implicit F: Effect[F])
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
      unsafeRunAsync(
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
    ctx.addListener(new AsyncTimeoutHandler(request, bodyWriter))
    // Note: We're catching silly user errors in the lift => flatten.
    val response = Async.shift(executionContext) *>
      optionTSync
        .suspend(serviceFn(request))
        .getOrElse(Response.notFound)
        .recoverWith(serviceErrorHandler(request))

    val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
    renderResponse(response, servletResponse, bodyWriter)
  }

  private def errorHandler(
      servletRequest: ServletRequest,
      servletResponse: HttpServletResponse): PartialFunction[Throwable, Unit] = {
    case t: Throwable if servletResponse.isCommitted =>
      logger.error(t)("Error processing request after response was committed")

    case t: Throwable =>
      logger.error(t)("Error processing request")
      val response = F.pure(Response[F](Status.InternalServerError))
      // We don't know what I/O mode we're in here, and we're not rendering a body
      // anyway, so we use a NullBodyWriter.
      unsafeRunAsync(renderResponse(response, servletResponse, NullBodyWriter)) { _ =>
        IO {
          if (servletRequest.isAsyncStarted)
            servletRequest.getAsyncContext.complete()
        }
      }
  }

  private class AsyncTimeoutHandler(request: Request[F], bodyWriter: BodyWriter[F])
      extends AbstractAsyncListener {
    override def onTimeout(event: AsyncEvent): Unit = {
      val ctx = event.getAsyncContext
      val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
      unsafeRunAsync {
        if (!servletResponse.isCommitted) {
          val response =
            F.pure(
              Response[F](Status.InternalServerError)
                .withEntity("Service timed out."))
          renderResponse(response, servletResponse, bodyWriter)
        } else {
          logger.warn(
            s"Async context timed out, but response was already committed: ${request.method} ${request.uri.path}")
          F.unit
        }
      } {
        case Right(()) => IO(ctx.complete())
        case Left(t) => IO(logger.error(t)("Error timing out async context")) *> IO(ctx.complete())
      }
    }
  }
}

object AsyncHttp4sServlet {
  def apply[F[_]: Effect](
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
