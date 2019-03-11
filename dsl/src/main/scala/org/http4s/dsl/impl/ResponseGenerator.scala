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
trait EmptyResponseGenerator[F[_], G[_]] extends Any with ResponseGenerator {
  def apply(headers: Header*)(implicit F: Applicative[F]): F[Response[G]] =
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
trait EntityResponseGenerator[F[_], G[_]] extends Any with ResponseGenerator {
  def apply(headers: Header*)(implicit F: Applicative[F]): F[Response[G]] =
    F.pure(Response[G](status, headers = Headers(`Content-Length`.zero +: headers: _*)))

  def apply[A](body: G[A])(implicit F: Monad[F], w: EntityEncoder[G, A]): F[Response[G]] = {
    val entity = Entity(fs2.Stream.eval(body).flatMap(w.toEntity(_).body))
    val headers = {
      val h = w.headers
      entity.length
        .map { l =>
          `Content-Length`.fromLong(l).fold(_ => h, c => h.put(c))
        }
        .getOrElse(h)
    }

    F.pure(Response[G](status = status, headers = headers, body = entity.body))
  }

  def apply[A](body: A, headers: Header*)(
      implicit F: Monad[F],
      w: EntityEncoder[G, A]): F[Response[G]] = {
    val h = w.headers ++ headers
    val entity = w.toEntity(body)
    val newHeaders = entity.length
      .map { l =>
        `Content-Length`.fromLong(l).fold(_ => h, c => h.put(c))
      }
      .getOrElse(h)
    F.pure(Response[G](status = status, headers = newHeaders, body = entity.body))
  }
}

/** Helper for the generation of a [[org.http4s.Response]] which may contain
  * a Location header and may contain a body.
  *
  * A 300, 301, 302, 303, 307 and 308 status SHOULD contain a Location header, which
  * distinguishes this from other `EntityResponseGenerator`s.
  */
trait LocationResponseGenerator[F[_], G[_]] extends Any with EntityResponseGenerator[F, G] {
  @deprecated("Use `apply(Location(location))` instead", "0.18.0-M2")
  def apply(location: Uri)(implicit F: Applicative[F]): F[Response[G]] =
    F.pure(
      Response[G](status = status, headers = Headers(`Content-Length`.zero, Location(location))))

  def apply(location: Location, headers: Header*)(implicit F: Applicative[F]): F[Response[G]] =
    F.pure(Response[G](status, headers = Headers(`Content-Length`.zero +: location +: headers: _*)))

  def apply[A](location: Location, body: A, headers: Header*)(
      implicit F: Monad[F],
      w: EntityEncoder[G, A]): F[Response[G]] = {
    val h = w.headers ++ Headers(location +: headers.toList)
    val entity = w.toEntity(body)
    val newHeaders = entity.length
      .map { l =>
        `Content-Length`.fromLong(l).fold(_ => h, c => h.put(c))
      }
      .getOrElse(h)
    F.pure(Response[G](status = status, headers = newHeaders, body = entity.body))
  }
}

/** Helper for the generation of a [[org.http4s.Response]] which must contain
  * a WWW-Authenticate header and may contain a body.
  *
  * A 401 status MUST contain a `WWW-Authenticate` header, which
  * distinguishes this from other `ResponseGenerator`s.
  */
trait WwwAuthenticateResponseGenerator[F[_], G[_]] extends Any with ResponseGenerator {
  @deprecated("Use ``apply(`WWW-Authenticate`(challenge, challenges)`` instead", "0.18.0-M2")
  def apply(challenge: Challenge, challenges: Challenge*)(
      implicit F: Applicative[F]): F[Response[G]] =
    F.pure(
      Response[G](
        status = status,
        headers = Headers(`Content-Length`.zero, `WWW-Authenticate`(challenge, challenges: _*))
      ))

  def apply(authenticate: `WWW-Authenticate`, headers: Header*)(
      implicit F: Applicative[F]): F[Response[G]] =
    F.pure(
      Response[G](status, headers = Headers(`Content-Length`.zero +: authenticate +: headers: _*)))

  def apply[A](authenticate: `WWW-Authenticate`, body: A, headers: Header*)(
      implicit F: Monad[F],
      w: EntityEncoder[G, A]): F[Response[G]] = {
    val h = w.headers ++ Headers(authenticate +: headers.toList)
    val entity = w.toEntity(body)
    val newHeaders = entity.length
      .map { l =>
        `Content-Length`.fromLong(l).fold(_ => h, c => h.put(c))
      }
      .getOrElse(h)
    F.pure(Response[G](status = status, headers = newHeaders, body = entity.body))
  }
}

/** Helper for the generation of a [[org.http4s.Response]] which must contain
  * an Allow header and may contain a body.
  *
  * A 405 status MUST contain an `Allow` header, which
  * distinguishes this from other `ResponseGenerator`s.
  */
trait AllowResponseGenerator[F[_], G[_]] extends Any with ResponseGenerator {

  def apply(allow: Allow, headers: Header*)(implicit F: Applicative[F]): F[Response[G]] =
    F.pure(Response[G](status, headers = Headers(`Content-Length`.zero +: allow +: headers.toList)))

  def apply[A](allow: Allow, body: A, headers: Header*)(
      implicit F: Monad[F],
      w: EntityEncoder[G, A]): F[Response[G]] = {
    val h = w.headers ++ Headers(allow +: headers.toList)
    val entity = w.toEntity(body)
    val newHeaders = entity.length
      .map { l =>
        `Content-Length`.fromLong(l).fold(_ => h, c => h.put(c))
      }
      .getOrElse(h)
    F.pure(Response[G](status = status, headers = newHeaders, body = entity.body))
  }

}

/** Helper for the generation of a [[org.http4s.Response]] which must contain
  * a Proxy-Authenticate header and may contain a body.
  *
  * A 407 status MUST contain a `Proxy-Authenticate` header, which
  * distinguishes this from other `EntityResponseGenerator`s.
  */
trait ProxyAuthenticateResponseGenerator[F[_], G[_]] extends Any with ResponseGenerator {
  @deprecated("Use ``apply(`Proxy-Authenticate`(challenge, challenges)`` instead", "0.18.0-M2")
  def apply(challenge: Challenge, challenges: Challenge*)(
      implicit F: Applicative[F]): F[Response[G]] =
    F.pure(
      Response[G](
        status = status,
        headers = Headers(`Content-Length`.zero, `Proxy-Authenticate`(challenge, challenges: _*))
      ))

  def apply(authenticate: `Proxy-Authenticate`, headers: Header*)(
      implicit F: Applicative[F]): F[Response[G]] =
    F.pure(
      Response[G](
        status,
        headers = Headers(`Content-Length`.zero +: authenticate +: headers.toList)))

  def apply[A](authenticate: `Proxy-Authenticate`, body: A, headers: Header*)(
      implicit F: Monad[F],
      w: EntityEncoder[G, A]): F[Response[G]] = {
    val h = w.headers ++ Headers(authenticate +: headers.toList)
    val entity = w.toEntity(body)
    val newHeaders = entity.length
      .map { l =>
        `Content-Length`.fromLong(l).fold(_ => h, c => h.put(c))
      }
      .getOrElse(h)
    F.pure(Response[G](status = status, headers = newHeaders, body = entity.body))
  }
}
