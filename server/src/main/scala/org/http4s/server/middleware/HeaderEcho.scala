package org.http4s.server.middleware

import cats.Functor
import cats.data.Kleisli
import org.http4s._
import cats.implicits._
import org.http4s.util.CaseInsensitiveString

object HeaderEcho {

  /**
    * Simple server middleware that adds selected headers present on the request to the response.
    *
    * @param echoHeadersWhen the function that selects which headers to echo on the response
    * @param http [[Http]] to transform
    */
  def apply[F[_]: Functor, G[_]: Functor](echoHeadersWhen: CaseInsensitiveString => Boolean)(
      http: Http[F, G]): Http[F, G] =
    Kleisli { req: Request[G] =>
      val headersToEcho = req.headers.filter(h => echoHeadersWhen(h.name))

      http(req).map(_.putHeaders(headersToEcho.toList: _*))
    }
}
