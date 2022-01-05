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

import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import org.http4s.server._

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class BlockingHttp4sServlet[F[_]](
    service: HttpApp[F],
    servletIo: BlockingServletIo[F],
    serviceErrorHandler: ServiceErrorHandler[F],
)(implicit F: Effect[F])
    extends Http4sServlet[F](service, servletIo) {
  override def service(
      servletRequest: HttpServletRequest,
      servletResponse: HttpServletResponse,
  ): Unit =
    F.defer {
      val bodyWriter = servletIo.initWriter(servletResponse)

      val render = toRequest(servletRequest).fold(
        onParseFailure(_, servletResponse, bodyWriter),
        handleRequest(_, servletResponse, bodyWriter),
      )

      render
    }.handleErrorWith(errorHandler(servletResponse))
      .toIO
      .unsafeRunSync()

  private def handleRequest(
      request: Request[F],
      servletResponse: HttpServletResponse,
      bodyWriter: BodyWriter[F],
  ): F[Unit] =
    // Note: We're catching silly user errors in the lift => flatten.
    Sync[F]
      .defer(serviceFn(request))
      .recoverWith(serviceErrorHandler(request))
      .flatMap(renderResponse(_, servletResponse, bodyWriter))

  private def errorHandler(servletResponse: HttpServletResponse)(t: Throwable): F[Unit] =
    F.defer {
      if (servletResponse.isCommitted) {
        logger.error(t)("Error processing request after response was committed")
        F.unit
      } else {
        logger.error(t)("Error processing request")
        val response = Response[F](Status.InternalServerError)
        // We don't know what I/O mode we're in here, and we're not rendering a body
        // anyway, so we use a NullBodyWriter.
        renderResponse(response, servletResponse, NullBodyWriter)
      }
    }
}

object BlockingHttp4sServlet {
  def apply[F[_]: Effect: ContextShift](
      service: HttpApp[F],
      blocker: Blocker,
  ): BlockingHttp4sServlet[F] =
    new BlockingHttp4sServlet[F](
      service,
      BlockingServletIo(DefaultChunkSize, blocker),
      DefaultServiceErrorHandler,
    )
}
