/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package middleware

import cats.ApplicativeError
import cats.data.Kleisli
import fs2._

import scala.util.control.NoStackTrace

object EntityLimiter {
  final case class EntityTooLarge(limit: Long) extends Exception with NoStackTrace

  val DefaultMaxEntitySize: Long = 2L * 1024L * 1024L // 2 MB default

  def apply[F[_], G[_], B](http: Kleisli[F, Request[G], B], limit: Long = DefaultMaxEntitySize)(
      implicit G: ApplicativeError[G, Throwable]): Kleisli[F, Request[G], B] =
    Kleisli { req =>
      http(req.withBodyStream(req.body.through(takeLimited(limit))))
    }

  def httpRoutes[F[_]: ApplicativeError[*[_], Throwable]](
      httpRoutes: HttpRoutes[F],
      limit: Long = DefaultMaxEntitySize): HttpRoutes[F] =
    apply(httpRoutes, limit)

  def httpApp[F[_]: ApplicativeError[*[_], Throwable]](
      httpApp: HttpApp[F],
      limit: Long = DefaultMaxEntitySize): HttpApp[F] =
    apply(httpApp, limit)

  private def takeLimited[F[_]](n: Long)(implicit
      F: ApplicativeError[F, Throwable]): Pipe[F, Byte, Byte] =
    _.pull
      .take(n)
      .flatMap {
        case Some(_) => Pull.raiseError[F](EntityTooLarge(n))
        case None => Pull.done
      }
      .stream
}
