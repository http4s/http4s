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

  def apply[F[_], G[_], B](
      @deprecatedName('service) http: Kleisli[F, Request[G], B],
      limit: Long = DefaultMaxEntitySize)(
      implicit G: ApplicativeError[G, Throwable]): Kleisli[F, Request[G], B] =
    Kleisli { req =>
      http(req.withBodyStream(req.body.through(takeLimited(limit))))
    }

  private def takeLimited[F[_]](n: Long)(
      implicit F: ApplicativeError[F, Throwable]): Pipe[F, Byte, Byte] =
    _.pull
      .take(n)
      .flatMap {
        case Some(_) => Pull.raiseError[F](EntityTooLarge(n))
        case None => Pull.done
      }
      .stream
}
