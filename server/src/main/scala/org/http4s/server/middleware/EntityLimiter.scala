package org.http4s
package server
package middleware

import scala.util.control.NoStackTrace

import fs2._
import fs2.Pull._
import fs2.Stream.Handle
import org.http4s.batteries._

object EntityLimiter {

  final case class EntityTooLarge(limit: Long) extends Exception with NoStackTrace

  val DefaultMaxEntitySize: Int = 2*1024*1024 // 2 MB default

  def apply(service: HttpService, limit: Int = DefaultMaxEntitySize): HttpService =
    service.local { req: Request =>
      req.copy(body = req.body.pull(takeLimited(limit)))
    }

  private def takeLimited[F[_]](n: Long)(h: Handle[F, Byte]): Pull[F, Byte, Nothing] =
    take(n)(h) flatMap receiveNonemptyOption {
      case Some(_) => fail(EntityTooLarge(n))
      case _ => done
    }
}
