package org.http4s
package server
package middleware

import cats.data.Kleisli
import fs2._

import scala.util.control.NoStackTrace

object EntityLimiter {

  final case class EntityTooLarge(limit: Long) extends Exception with NoStackTrace

  val DefaultMaxEntitySize: Long = 2L * 1024L * 1024L // 2 MB default

  def apply[F[_], G[_], B](
      @deprecatedName('service) http: Kleisli[F, Request[G], B],
      limit: Long = DefaultMaxEntitySize): Kleisli[F, Request[G], B] =
    Kleisli { req =>
      http(req.withBodyStream(req.body.through(takeLimited(limit))))
    }

  private def takeLimited[F[_]](n: Long): Pipe[F, Byte, Byte] =
    _.pull
      .take(n)
      .flatMap {
        case Some(_) => Pull.raiseError(EntityTooLarge(n))
        case None => Pull.done
      }
      .stream
}
