package org.http4s
package server
package middleware

import cats._
import cats.data.{Kleisli, OptionT}
import org.http4s.Status.{BadRequest, NotFound}
import org.http4s.headers.Host
import Message.messSyntax._

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
  final case class HostService[F[_]](service: HttpService[F], p: Host => Boolean)

  /** Create a [[HostService]] that will match based on the exact host string
    * (discounting case) and port, if the port is given. If the port is not
    * given, it is ignored.
    */
  def exact[F[_]](
      service: HttpService[F],
      requestHost: String,
      port: Option[Int] = None): HostService[F] =
    HostService(
      service,
      h => h.host.equalsIgnoreCase(requestHost) && (port.isEmpty || port == h.port))

  /** Create a [[HostService]] that will match based on the host string allowing
    * for wildcard matching of the lowercase host string and port, if the port is
    * given. If the port is not given, it is ignored.
    */
  def wildcard[F[_]](
      service: HttpService[F],
      wildcardHost: String,
      port: Option[Int] = None): HostService[F] =
    regex(service, wildcardHost.replace("*", "\\w+").replace(".", "\\.").replace("-", "\\-"), port)

  /** Create a [[HostService]] that uses a regular expression to match the host
    * string (which will be provided in lower case form) and port, if the port
    * is given. If the port is not given, it is ignored.
    */
  def regex[F[_]](
      service: HttpService[F],
      hostRegex: String,
      port: Option[Int] = None): HostService[F] = {
    val r = hostRegex.r
    HostService(
      service,
      h => r.findFirstIn(h.host.toLowerCase).nonEmpty && (port.isEmpty || port == h.port))
  }

  def apply[F[_]](first: HostService[F], rest: HostService[F]*)(
      implicit F: Monad[F],
      W: EntityEncoder[F, String]): HttpService[F] =
    Kleisli { req =>
      req.headers
        .get(Host)
        .fold(OptionT.liftF(Response[F](BadRequest).withBody("Host header required."))) { h =>
          // Fill in the host port if possible
          val host: Host = h.port match {
            case Some(_) => h
            case None =>
              h.copy(port = req.uri.port.orElse(req.isSecure.map(if (_) 443 else 80)))
          }
          (first +: rest).toVector
            .collectFirst { case HostService(s, p) if p(host) => s(req) }
            .getOrElse(OptionT.liftF(Response[F](NotFound).withBody(s"Host '$host' not found.")))
        }
    }
}
