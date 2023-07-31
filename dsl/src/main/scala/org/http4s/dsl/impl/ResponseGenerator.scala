/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package dsl
package impl

import cats.Applicative
import cats.Monad
import cats.arrow.FunctionK
import cats.syntax.all._
import org.http4s.headers._

import ResponseGenerator.addEntityLength

trait ResponseGenerator extends Any {
  def status: Status
}

private[impl] object ResponseGenerator {
  def addEntityLength[G[_]](entity: Entity[G], headers: Headers): Headers =
    headers.put(entity.length.flatMap(x => `Content-Length`.fromLong(x).toOption))
}

/**  Helper for the generation of a [[org.http4s.Response]] which will not contain a body
  *
  * While it is possible to for the [[org.http4s.Response]] manually, the EntityResponseGenerators
  * offer shortcut syntax to make intention clear and concise.
  *
  * @example {{{
  * val resp: F[Response] = Status.Continue()
  * }}}
  */
trait EmptyResponseGenerator[F[_], G[_]] extends Any with ResponseGenerator {
  def apply()(implicit F: Applicative[F]): F[Response[G]] = F.pure(Response[G](status))

  def headers(header: Header.ToRaw, _headers: Header.ToRaw*)(implicit
      F: Applicative[F]
  ): F[Response[G]] =
    F.pure(Response[G](status, headers = Headers(header :: _headers.toList)))
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
  def liftG: FunctionK[G, F]

  def apply()(implicit F: Applicative[F]): F[Response[G]] =
    F.pure(Response[G](status, headers = Headers(List(`Content-Length`.zero))))

  def headers(header: Header.ToRaw, _headers: Header.ToRaw*)(implicit
      F: Applicative[F]
  ): F[Response[G]] =
    F.pure(
      Response[G](
        status,
        headers = Headers(`Content-Length`.zero) ++ Headers(header :: _headers.toList),
      )
    )

  def apply[A](body: G[A])(implicit F: Monad[F], w: EntityEncoder[G, A]): F[Response[G]] =
    F.flatMap(liftG(body))(apply[A](_))

  def apply[A](body: A, headers: Header.ToRaw*)(implicit
      F: Applicative[F],
      w: EntityEncoder[G, A],
  ): F[Response[G]] = {
    val h = w.headers |+| Headers(headers)
    val entity = w.toEntity(body)
    F.pure(Response[G](status = status, headers = addEntityLength(entity, h), body = entity.body))
  }
}

/** Helper for the generation of a [[org.http4s.Response]] which may contain
  * a Location header and may contain a body.
  *
  * A 300, 301, 302, 303, 307 and 308 status SHOULD contain a Location header, which
  * distinguishes this from other `EntityResponseGenerator`s.
  */
trait LocationResponseGenerator[F[_], G[_]] extends Any with EntityResponseGenerator[F, G] {
  def apply(location: Location)(implicit F: Applicative[F]): F[Response[G]] =
    F.pure(Response[G](status = status, headers = Headers(`Content-Length`.zero, location)))

  def apply[A](location: Location, body: A, headers: Header.ToRaw*)(implicit
      F: Applicative[F],
      w: EntityEncoder[G, A],
  ): F[Response[G]] = {
    val h = w.headers |+| Headers(location, headers)
    val entity = w.toEntity(body)
    F.pure(Response[G](status = status, headers = addEntityLength(entity, h), body = entity.body))
  }
}

/** Helper for the generation of a [[org.http4s.Response]] which must contain
  * a WWW-Authenticate header and may contain a body.
  *
  * A 401 status MUST contain a `WWW-Authenticate` header, which
  * distinguishes this from other `ResponseGenerator`s.
  */
trait WwwAuthenticateResponseGenerator[F[_], G[_]] extends Any with ResponseGenerator {
  def apply(authenticate: `WWW-Authenticate`, headers: Header.ToRaw*)(implicit
      F: Applicative[F]
  ): F[Response[G]] =
    F.pure(
      Response[G](status, headers = Headers(`Content-Length`.zero, authenticate, headers))
    )

  def apply[A](authenticate: `WWW-Authenticate`, body: A, headers: Header.ToRaw*)(implicit
      F: Applicative[F],
      w: EntityEncoder[G, A],
  ): F[Response[G]] = {
    val h = w.headers |+| Headers(authenticate, headers)
    val entity = w.toEntity(body)
    F.pure(Response[G](status = status, headers = addEntityLength(entity, h), body = entity.body))
  }
}

/** Helper for the generation of a [[org.http4s.Response]] which must contain
  * an Allow header and may contain a body.
  *
  * A 405 status MUST contain an `Allow` header, which
  * distinguishes this from other `ResponseGenerator`s.
  */
trait AllowResponseGenerator[F[_], G[_]] extends Any with ResponseGenerator {
  def apply(allow: Allow, headers: Header.ToRaw*)(implicit F: Applicative[F]): F[Response[G]] =
    F.pure(Response[G](status, headers = Headers(`Content-Length`.zero, allow, headers)))

  def apply[A](allow: Allow, body: A, headers: Header.ToRaw*)(implicit
      F: Applicative[F],
      w: EntityEncoder[G, A],
  ): F[Response[G]] = {
    val h = w.headers |+| Headers(allow, headers)
    val entity = w.toEntity(body)
    F.pure(Response[G](status = status, headers = addEntityLength(entity, h), body = entity.body))
  }
}

/** Helper for the generation of a [[org.http4s.Response]] which must contain
  * a Proxy-Authenticate header and may contain a body.
  *
  * A 407 status MUST contain a `Proxy-Authenticate` header, which
  * distinguishes this from other `EntityResponseGenerator`s.
  */
trait ProxyAuthenticateResponseGenerator[F[_], G[_]] extends Any with ResponseGenerator {
  def apply(authenticate: `Proxy-Authenticate`, headers: Header.ToRaw*)(implicit
      F: Applicative[F]
  ): F[Response[G]] =
    F.pure(
      Response[G](status, headers = Headers(`Content-Length`.zero, authenticate, headers))
    )

  def apply[A](authenticate: `Proxy-Authenticate`, body: A, headers: Header.ToRaw*)(implicit
      F: Applicative[F],
      w: EntityEncoder[G, A],
  ): F[Response[G]] = {
    val h = w.headers |+| Headers(authenticate, headers)
    val entity = w.toEntity(body)
    F.pure(Response[G](status = status, headers = addEntityLength(entity, h), body = entity.body))
  }
}
