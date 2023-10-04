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
import cats.MonadThrow
import cats.data.Kleisli
import fs2._

import scala.util.control.NoStackTrace

object EntityLimiter {
  final case class EntityTooLarge(limit: Long) extends Exception with NoStackTrace

  val DefaultMaxEntitySize: Long = 2L * 1024L * 1024L // 2 MB default

  def apply[F[_], G[_], B](http: Kleisli[F, Request[G], B], limit: Long = DefaultMaxEntitySize)(
      implicit
      G: ApplicativeThrow[G],
      F: ApplicativeThrow[F],
  ): Kleisli[F, Request[G], B] =
    Kleisli[F, Request[G], B] { req =>
      req.entity match {
        case Entity.Empty =>
          http.run(req)

        case Entity.Strict(chunk) =>
          if (chunk.size.toLong < limit) http.run(req)
          else F.raiseError[B](EntityTooLarge(limit))

        case Entity.Streamed(_, _) =>
          http.run(req.pipeBodyThrough[G](takeLimited(limit)))
      }
    }

  def httpRoutes[F[_]: MonadThrow](
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
        case Some(rest) => // if rest is empty then we won't raise an error
          (rest >> Stream.raiseError(EntityTooLarge(n))).pull.echo
        case None =>
          Pull.done
      }
      .stream
}
