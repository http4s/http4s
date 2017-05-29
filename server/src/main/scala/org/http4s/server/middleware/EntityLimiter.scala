package org.http4s
package server
package middleware

import scala.util.control.NoStackTrace

import fs2._
import fs2.Pull._
import fs2.Handle._

object EntityLimiter {

  final case class EntityTooLarge(limit: Long) extends Exception with NoStackTrace

  val DefaultMaxEntitySize: Long = 2L*1024L*1024L // 2 MB default

  def apply[F[_]](service: HttpService[F], limit: Long = DefaultMaxEntitySize): HttpService[F] =
    service.local { req: Request[F] =>
      req.copy(body = req.body.pull(takeLimited(limit)))
    }

  private def takeLimited[F[_]](n: Long)(h: Handle[F, Byte]): Pull[F, Byte, Nothing] =
    h.take(n) flatMap { _.receiveOption {
      case Some(_) => fail(EntityTooLarge(n))
      case _ => done
    }}
}
