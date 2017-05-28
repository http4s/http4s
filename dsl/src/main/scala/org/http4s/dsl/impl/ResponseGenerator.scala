package org.http4s
package dsl
package impl

import cats._
import cats.implicits._
import org.http4s.headers._

trait ResponseGenerator extends Any {
  def status: Status
}

/**
 *  Helper for the generation of a [[org.http4s.Response]] which will not contain a body
 *
 * While it is possible to for the [[org.http4s.Response]] manually, the EntityResponseGenerators
 * offer shortcut syntax to make intention clear and concise.
 *
 * @example {{{
 * val resp: F[Response] = Status.Continue()
 * }}}
 */
trait EmptyResponseGenerator[F[_]] extends Any with ResponseGenerator {
  def apply()(implicit F: Applicative[F]): F[Response[F]] = F.pure(Response(status))
}

/** Helper for the generation of a [[org.http4s.Response]] which may contain a body
  *
  * While it is possible to construct the [[org.http4s.Response]] manually, the EntityResponseGenerators
  * offer shortcut syntax to make intention clear and concise.
  *
  * @example {{{
  * val resp: IO[Response] = Ok("Hello world!")
  * }}}
  */
trait EntityResponseGenerator[F[_]] extends Any with EmptyResponseGenerator[F] {
  def apply[A](body: A)(implicit F: Monad[F], w: EntityEncoder[F, A]): F[Response[F]] =
    apply(body, Headers.empty)(F, w)

  def apply[A](body: A, headers: Headers)
                    (implicit F: Monad[F], w: EntityEncoder[F, A]): F[Response[F]] = {
    var h = w.headers ++ headers
    w.toEntity(body).flatMap { entity =>
      entity.length.foreach(l => h = h.put(`Content-Length`(l)))
      F.pure(Response(status = status, headers = h, body = entity.body))
    }
  }
}

trait LocationResponseGenerator[F[_]] extends Any with ResponseGenerator {
  def apply(location: Uri)(implicit F: Applicative[F]): F[Response[F]] =
    F.pure(Response[F](status).putHeaders(Location(location)))
}

trait WwwAuthenticateResponseGenerator[F[_]] extends Any with ResponseGenerator {
  def apply(challenge: Challenge, challenges: Challenge*)(implicit F: Applicative[F]): F[Response[F]] =
    F.pure(Response[F](status).putHeaders(`WWW-Authenticate`(challenge, challenges: _*)))
}

trait ProxyAuthenticateResponseGenerator[F[_]] extends Any with ResponseGenerator {
  def apply(challenge: Challenge, challenges: Challenge*)(implicit F: Applicative[F]): F[Response[F]] =
    F.pure(Response[F](status).putHeaders(`Proxy-Authenticate`(challenge, challenges: _*)))
}
