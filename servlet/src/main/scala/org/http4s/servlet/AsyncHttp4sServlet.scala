/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package servlet

import cats.effect.kernel.Async
import cats.effect.kernel.Deferred
import cats.effect.std.Dispatcher
import cats.syntax.all._
import org.http4s.server._

import javax.servlet._
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import scala.concurrent.duration.Duration

class AsyncHttp4sServlet[F[_]](
    service: HttpApp[F],
    asyncTimeout: Duration = Duration.Inf,
    servletIo: ServletIo[F],
    serviceErrorHandler: ServiceErrorHandler[F],
    dispatcher: Dispatcher[F],
)(implicit F: Async[F])
    extends Http4sServlet[F](service, servletIo, dispatcher) {
  private val asyncTimeoutMillis =
    if (asyncTimeout.isFinite) asyncTimeout.toMillis else -1 // -1 == Inf

  override def init(config: ServletConfig): Unit = {
    super.init(config)
    logServletIo()
  }

  private def logServletIo(): Unit =
    logger.info(servletIo match {
      case BlockingServletIo(chunkSize) =>
        s"Using blocking servlet I/O with chunk size $chunkSize"
      case NonBlockingServletIo(chunkSize) =>
        s"Using non-blocking servlet I/O with chunk size $chunkSize"
    })

  override def service(
      servletRequest: HttpServletRequest,
      servletResponse: HttpServletResponse,
  ): Unit =
    try {
      val ctx = servletRequest.startAsync()
      ctx.setTimeout(asyncTimeoutMillis)
      // Must be done on the container thread for Tomcat's sake when using async I/O.
      val bodyWriter = servletIo.initWriter(servletResponse)
      val result = F
        .attempt(
          toRequest(servletRequest).fold(
            onParseFailure(_, servletResponse, bodyWriter),
            handleRequest(ctx, _, bodyWriter),
          )
        )
        .flatMap {
          case Right(()) => F.delay(ctx.complete)
          case Left(t) => errorHandler(servletRequest, servletResponse)(t)
        }
      dispatcher.unsafeRunAndForget(result)
    } catch errorHandler(servletRequest, servletResponse).andThen(dispatcher.unsafeRunSync _)

  private def handleRequest(
      ctx: AsyncContext,
      request: Request[F],
      bodyWriter: BodyWriter[F],
  ): F[Unit] =
    Deferred[F, Unit].flatMap { gate =>
      // It is an error to add a listener to an async context that is
      // already completed, so we must take care to add the listener
      // before the response can complete.

      val timeout =
        F.async[Response[F]](cb =>
          gate.complete(ctx.addListener(new AsyncTimeoutHandler(cb))).as(Option.empty[F[Unit]])
        )
      val response =
        gate.get *>
          F.defer(serviceFn(request))
            .recoverWith(serviceErrorHandler(request))
      val servletResponse = ctx.getResponse.asInstanceOf[HttpServletResponse]
      F.race(timeout, response).flatMap(r => renderResponse(r.merge, servletResponse, bodyWriter))
    }

  private def errorHandler(
      servletRequest: ServletRequest,
      servletResponse: HttpServletResponse,
  ): PartialFunction[Throwable, F[Unit]] = {
    case t: Throwable if servletResponse.isCommitted =>
      F.delay(logger.error(t)("Error processing request after response was committed"))

    case t: Throwable =>
      val response = Response[F](Status.InternalServerError)
      // We don't know what I/O mode we're in here, and we're not rendering a body
      // anyway, so we use a NullBodyWriter.
      val f = renderResponse(response, servletResponse, NullBodyWriter) *>
        F.delay(
          if (servletRequest.isAsyncStarted)
            servletRequest.getAsyncContext.complete()
        )
      F.delay(logger.error(t)("Error processing request")) *> F
        .attempt(f)
        .flatMap {
          case Right(()) => F.unit
          case Left(e) => F.delay(logger.error(e)("Error in error handler"))
        }
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
  def apply[F[_]: Async](
      service: HttpApp[F],
      asyncTimeout: Duration = Duration.Inf,
      dispatcher: Dispatcher[F],
  ): AsyncHttp4sServlet[F] =
    new AsyncHttp4sServlet[F](
      service,
      asyncTimeout,
      NonBlockingServletIo[F](DefaultChunkSize),
      DefaultServiceErrorHandler,
      dispatcher,
    )
}
