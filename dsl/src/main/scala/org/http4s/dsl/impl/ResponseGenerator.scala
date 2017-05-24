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
trait EmptyResponseGenerator extends Any with ResponseGenerator {
  def apply[F[_]]()(implicit F: Applicative[F]): F[Response[F]] = F.pure(Response(status))
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
trait EntityResponseGenerator extends Any with EmptyResponseGenerator {
  def apply[F[_], A](body: A)(implicit F: Applicative[F], FM: FlatMap[F], w: EntityEncoder[F, A]): F[Response[F]] =
    apply(body, Headers.empty)(F, FM, w)

  def apply[F[_], A](body: A, headers: Headers)
                    (implicit F: Applicative[F], FM: FlatMap[F], w: EntityEncoder[F, A]): F[Response[F]] = {
    var h = w.headers ++ headers
    w.toEntity(body).flatMap { entity =>
      entity.length.foreach(l => h = h.put(`Content-Length`(l)))
      F.pure(Response(status = status, headers = h, body = entity.body))
    }
  }
}

trait LocationResponseGenerator extends Any with ResponseGenerator {
  def apply[F[_]](location: Uri)(implicit F: Applicative[F]): F[Response[F]] =
    F.pure(Response[F](status).putHeaders(Location(location)))
}

trait WwwAuthenticateResponseGenerator extends Any with ResponseGenerator {
  def apply[F[_]](challenge: Challenge, challenges: Challenge*)(implicit F: Applicative[F]): F[Response[F]] =
    F.pure(Response[F](status).putHeaders(`WWW-Authenticate`(challenge, challenges: _*)))
}

trait ProxyAuthenticateResponseGenerator extends Any with ResponseGenerator {
  def apply[F[_]](challenge: Challenge, challenges: Challenge*)(implicit F: Applicative[F]): F[Response[F]] =
    F.pure(Response[F](status).putHeaders(`Proxy-Authenticate`(challenge, challenges: _*)))
}
