/*
 * Copyright 2014 http4s.org
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
package server
package middleware

import cats.Applicative
import cats.data.Kleisli
import org.http4s.Status.BadRequest
import org.http4s.Status.NotFound
import org.http4s.headers.Host

/** Middleware for virtual host mapping
  *
  * The `VirtualHost` middleware allows multiple services to be mapped
  * based on the [[org.http4s.headers.Host]] header of the [[org.http4s.Request]].
  */
object VirtualHost {

  /** Specification of the virtual host service and predicate.
    *
    * The predicate receives the the Host header information with the port
    * filled in, if possible, using the request Uri or knowledge of the
    * security of the underlying transport protocol.
    */
  final case class HostService[F[_], G[_]](http: Http[F, G], p: Host => Boolean)

  /** Create a [[HostService]] that will match based on the exact host string
    * (discounting case) and port, if the port is given. If the port is not
    * given, it is ignored.
    */
  def exact[F[_], G[_]](
      http: Http[F, G],
      requestHost: String,
      port: Option[Int] = None,
  ): HostService[F, G] =
    HostService(http, h => h.host.equalsIgnoreCase(requestHost) && (port.isEmpty || port == h.port))

  /** Create a [[HostService]] that will match based on the host string allowing
    * for wildcard matching of the lowercase host string and port, if the port is
    * given. If the port is not given, it is ignored.
    */
  def wildcard[F[_], G[_]](
      http: Http[F, G],
      wildcardHost: String,
      port: Option[Int] = None,
  ): HostService[F, G] =
    regex(http, wildcardHost.replace("*", "\\w+").replace(".", "\\.").replace("-", "\\-"), port)

  /** Create a [[HostService]] that uses a regular expression to match the host
    * string (which will be provided in lower case form) and port, if the port
    * is given. If the port is not given, it is ignored.
    */
  def regex[F[_], G[_]](
      http: Http[F, G],
      hostRegex: String,
      port: Option[Int] = None,
  ): HostService[F, G] = {
    val r = hostRegex.r
    HostService(
      http,
      h => r.findFirstIn(h.host.toLowerCase).nonEmpty && (port.isEmpty || port == h.port),
    )
  }

  def apply[F[_], G[_]](first: HostService[F, G], rest: HostService[F, G]*)(implicit
      F: Applicative[F],
      W: EntityEncoder[G, String],
  ): Http[F, G] =
    Kleisli { req =>
      req.headers
        .get[Host]
        .fold(F.pure(Response[G](BadRequest).withEntity("Host header required."))) { h =>
          // Fill in the host port if possible
          val host: Host = h.port match {
            case Some(_) => h
            case None =>
              h.copy(port = req.uri.port.orElse(req.isSecure.map(if (_) 443 else 80)))
          }
          (first +: rest).toVector
            .collectFirst { case HostService(s, p) if p(host) => s(req) }
            .getOrElse(F.pure(Response[G](NotFound).withEntity(s"Host '$host' not found.")))
        }
    }
}
