package org.http4s
package server
package middleware

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import cats.~>

/** [[Middleware]] for lifting application/x-www-form-urlencoded bodies into the
  * request query params.
  *
  * The params are merged into the existing paras _after_ the existing query params. This
  * means that if the query already contains the pair "foo" -> Some("bar"), parameters on
  * the body must be acessed through `multiParams`.
  */
object UrlFormLifter {

  def apply[F[_]: Sync, G[_]: Sync](f: G ~> F)(
      @deprecatedName('service) http: Kleisli[F, Request[G], Response[G]],
      strictDecode: Boolean = false): Kleisli[F, Request[G], Response[G]] =
    Kleisli { req =>
      def addUrlForm(form: UrlForm): F[Response[G]] = {
        val flatForm = form.values.toVector.flatMap {
          case (k, vs) => vs.toVector.map(v => (k, Some(v)))
        }
        val params = req.uri.query.toVector ++ flatForm: Vector[(String, Option[String])]
        val newQuery = Query(params: _*)

        val newRequest = req
          .withUri(req.uri.copy(query = newQuery))
          .withEmptyBody
        http(newRequest)
      }

      req.headers.get(headers.`Content-Type`) match {
        case Some(headers.`Content-Type`(MediaType.application.`x-www-form-urlencoded`, _))
            if checkRequest(req) =>
          for {
            decoded <- f(UrlForm.entityDecoder[G].decode(req, strictDecode).value)
            resp <- decoded.fold(
              mf => f(mf.toHttpResponse[G](req.httpVersion)),
              addUrlForm
            )
          } yield resp

        case _ => http(req)
      }
    }

  private def checkRequest[F[_]](req: Request[F]): Boolean =
    req.method == Method.POST || req.method == Method.PUT
}
