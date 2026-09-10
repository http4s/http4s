/*
 * Copyright 2014 http4s.org
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
package server
package middleware

import cats.ApplicativeThrow
import cats.data.Kleisli
import fs2._
import org.http4s.headers.Connection
import org.http4s.headers.`Content-Length`

import scala.util.control.NoStackTrace

object EntityLimiter {
  final case class EntityTooLarge(limit: Long) extends MessageFailure with NoStackTrace { self =>
    override def message: String = s"Content Too Large [limit=$limit]"

    override def cause: Option[Throwable] = Some(self)

    override def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] = Response(
      Status.PayloadTooLarge,
      httpVersion,
      Headers(
        Connection.close,
        `Content-Length`.zero,
      ),
    )
  }

  val DefaultMaxEntitySize: Long = 2L * 1024L * 1024L // 2 MB default

  def apply[F[_], G[_], B](http: Kleisli[F, Request[G], B], limit: Long = DefaultMaxEntitySize)(
      implicit G: ApplicativeThrow[G]
  ): Kleisli[F, Request[G], B] =
    http.local(_.pipeBodyThrough(takeLimited(limit)))

  def httpRoutes[F[_]: ApplicativeThrow](
      httpRoutes: HttpRoutes[F],
      limit: Long = DefaultMaxEntitySize,
  ): HttpRoutes[F] =
    apply(httpRoutes, limit)

  def httpApp[F[_]: ApplicativeThrow](
      httpApp: HttpApp[F],
      limit: Long = DefaultMaxEntitySize,
  ): HttpApp[F] =
    apply(httpApp, limit)

  private def takeLimited[F[_]](n: Long)(implicit F: ApplicativeThrow[F]): Pipe[F, Byte, Byte] =
    _.pull
      .take(n)
      .flatMap {
        case Some(rest) =>
          // Drain the rest of the entity. If `rest` is empty then we won't raise an error
          rest.last
            .flatMap {
              case Some(_) => Stream.raiseError(EntityTooLarge(n))
              case None => Stream.empty
            }
            .pull
            .echo
        case None =>
          Pull.done
      }
      .stream
}
