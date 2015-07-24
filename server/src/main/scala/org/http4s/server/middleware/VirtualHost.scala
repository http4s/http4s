package org.http4s
package server
package middleware

import headers.Host
import Status.{BadRequest, NotFound}

/** Middleware for virtual host mapping
  *
  * The `VirtualHost` middleware allows multiple services to be mapped
  * based on the [[Host]] header of the [[Request]].
  */
object VirtualHost {

  /** Specification of the virtual host service and predicate.
    *
    * The predicate receives the the Host header information with the port
    * filled in, if possible, using the request Uri or knowledge of the
    * security of the underlying transport protocol.
    */
  case class HostService(service: HttpService, p: Host => Boolean)

  /** Create a [[HostService]] that will match based on the exact host string
    * (discounting case) and port, if the port is given. If the port is not
    * given, it is ignored.
    */
  def exact(service: HttpService, requestHost: String, port: Option[Int] = None): HostService =
    HostService(service, h => h.host.equalsIgnoreCase(requestHost) && (port.isEmpty || port == h.port))

  /** Create a [[HostService]] that will match based on the host string allowing
    * for wildcard matching of the lowercase host string and port, if the port is 
    * given. If the port is not given, it is ignored.
    */
  def wildcard(service: HttpService, wildcardHost: String, port: Option[Int] = None): HostService =
    regex(service, wildcardHost.replace("*", "\\w+").replace(".", "\\.").replace("-", "\\-"), port)

  /** Create a [[HostService]] that uses a regular expression to match the host
    * string (which will be provided in lower case form) and port, if the port
    * is given. If the port is not given, it is ignored.
    */
  def regex(service: HttpService, hostRegex: String, port: Option[Int] = None): HostService = {
    val r = hostRegex.r
    HostService(service, h => r.findFirstIn(h.host.toLowerCase).nonEmpty && (port.isEmpty || port == h.port))
  }


  def apply(first: HostService, rest: HostService*): HttpService = {

    val all = (first +: rest).toVector

    Service.lift { req =>
      req.headers.get(Host) match {
        case None =>
          Response(BadRequest).withBody("Host header required.")

        case Some(h) =>
          // Fill in the host port if possible
          val host = h.port match {
            case Some(_) => h
            case None =>
              h.copy(port = req.uri.port.orElse(req.isSecure.map(if (_) 443 else 80)))
          }

          all.collectFirst { case HostService(s,p) if p(host) => s(req) }
             .getOrElse(Response(NotFound).withBody(s"Host '$host' not found."))
      }
    }
  }
}
