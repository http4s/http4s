package org.http4s
package server
package middleware

import fs2._
import scala.util.control.NoStackTrace

object EntityLimiter {

  final case class EntityTooLarge(limit: Long) extends Exception with NoStackTrace

  val DefaultMaxEntitySize: Long = 2L * 1024L * 1024L // 2 MB default

  def apply[F[_]](
      @deprecatedName('service) routes: HttpRoutes[F],
      limit: Long = DefaultMaxEntitySize): HttpRoutes[F] =
    routes.local { req: Request[F] =>
      req.withBodyStream(req.body.through(takeLimited(limit)))
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
