package org.http4s
package dsl
package impl

import cats._
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
  def apply(headers: Header*)(implicit F: Applicative[F]): F[Response[F]] =
    F.pure(Response(status, headers = Headers(headers: _*)))
}

/** Helper for the generation of a [[org.http4s.Response]] which may contain a body
  *
  * While it is possible to construct the [[org.http4s.Response]]
  * manually, the EntityResponseGenerators offer shortcut syntax to
  * make intention clear and concise.
  *
  * @example {{{
  * val resp: IO[Response] = Ok("Hello world!")
  * }}}
  */
trait EntityResponseGenerator[F[_]] extends Any with ResponseGenerator {
  def apply(headers: Header*)(implicit F: Applicative[F]): F[Response[F]] =
    F.pure(Response(status, headers = Headers(`Content-Length`.zero +: headers: _*)))

  def apply[A](body: A, headers: Header*)(
      implicit F: Monad[F],
      w: EntityEncoder[F, A]): F[Response[F]] = {
    val h = w.headers ++ headers
    val entity = w.toEntity(body)
    val newHeaders = entity.length
      .map { l =>
        `Content-Length`.fromLong(l).fold(_ => h, c => h.put(c))
      }
      .getOrElse(h)
    F.pure(Response(status = status, headers = newHeaders, body = entity.body))
  }
}

trait LocationResponseGenerator[F[_]] extends Any with EntityResponseGenerator[F] {
  @deprecated("Use `apply(Location(location))` instead", "0.18.0-M2")
  def apply(location: Uri)(implicit F: Applicative[F]): F[Response[F]] =
    F.pure(
      Response[F](status = status, headers = Headers(`Content-Length`.zero, Location(location))))
}

trait WwwAuthenticateResponseGenerator[F[_]] extends Any with ResponseGenerator {
  @deprecated("Use ``apply(`WWW-Authenticate`(challenge, challenges)`` instead", "0.18.0-M2")
  def apply(challenge: Challenge, challenges: Challenge*)(
      implicit F: Applicative[F]): F[Response[F]] =
    F.pure(
      Response[F](
        status = status,
        headers = Headers(`Content-Length`.zero, `WWW-Authenticate`(challenge, challenges: _*))
      ))

  /** A 401 status MUST contain a `WWW-Authenticate` header, which
    * distinguishes this from other `EntityResponseGenerator`s.
    */
  def apply(authenticate: `WWW-Authenticate`, headers: Header*)(
      implicit F: Applicative[F]): F[Response[F]] =
    F.pure(
      Response(status, headers = Headers(`Content-Length`.zero +: authenticate +: headers: _*)))

  /** A 401 status MUST contain a `WWW-Authenticate` header, which
    * distinguishes this from other `EntityResponseGenerator`s.
    */
  def apply[A](authenticate: `WWW-Authenticate`, body: A, headers: Header*)(
      implicit F: Monad[F],
      w: EntityEncoder[F, A]): F[Response[F]] = {
    val h = w.headers ++ Headers(authenticate +: headers.toList)
    val entity = w.toEntity(body)
    val newHeaders = entity.length
      .map { l =>
        `Content-Length`.fromLong(l).fold(_ => h, c => h.put(c))
      }
      .getOrElse(h)
    F.pure(Response(status = status, headers = newHeaders, body = entity.body))
  }
}

trait ProxyAuthenticateResponseGenerator[F[_]] extends Any with ResponseGenerator {
  @deprecated("Use ``apply(`Proxy-Authenticate`(challenge, challenges)`` instead", "0.18.0-M2")
  def apply(challenge: Challenge, challenges: Challenge*)(
      implicit F: Applicative[F]): F[Response[F]] =
    F.pure(
      Response[F](
        status = status,
        headers = Headers(`Content-Length`.zero, `Proxy-Authenticate`(challenge, challenges: _*))
      ))

  /** A 407 status MUST contain a `Proxy-Authenticate` header, which
    * distinguishes this from other `EntityResponseGenerator`s.
    */
  def apply(authenticate: `Proxy-Authenticate`, headers: Header*)(
      implicit F: Applicative[F]): F[Response[F]] =
    F.pure(
      Response(status, headers = Headers(`Content-Length`.zero +: authenticate +: headers.toList)))

  /** A 407 status MUST contain a `Proxy-Authenticate` header, which
    * distinguishes this from other `EntityResponseGenerator`s.
    */
  def apply[A](authenticate: `Proxy-Authenticate`, body: A, headers: Header*)(
      implicit F: Monad[F],
      w: EntityEncoder[F, A]): F[Response[F]] = {
    val h = w.headers ++ Headers(authenticate +: headers.toList)
    val entity = w.toEntity(body)
    val newHeaders = entity.length
      .map { l =>
        `Content-Length`.fromLong(l).fold(_ => h, c => h.put(c))
      }
      .getOrElse(h)
    F.pure(Response(status = status, headers = newHeaders, body = entity.body))
  }
}
