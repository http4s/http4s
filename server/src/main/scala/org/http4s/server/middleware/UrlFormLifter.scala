package org.http4s
package server
package middleware

import cats.data.{Kleisli, OptionT}
import cats.effect._

/** [[Middleware]] for lifting application/x-www-form-urlencoded bodies into the
  * request query params.
  *
  * The params are merged into the existing paras _after_ the existing query params. This
  * means that if the query already contains the pair "foo" -> Some("bar"), parameters on
  * the body must be acessed through `multiParams`.
  */
object UrlFormLifter {

  def apply[F[_]: Effect](
      @deprecatedName('service) routes: HttpRoutes[F],
      strictDecode: Boolean = false): HttpRoutes[F] =
    Kleisli { req =>
      def addUrlForm(form: UrlForm): OptionT[F, Response[F]] = {
        val flatForm = form.values.toVector.flatMap { case (k, vs) => vs.map(v => (k, Some(v))) }
        val params = req.uri.query.toVector ++ flatForm: Vector[(String, Option[String])]
        val newQuery = Query(params: _*)

        val newRequest = req
          .withUri(req.uri.copy(query = newQuery))
          .withEmptyBody
        routes(newRequest)
      }

      req.headers.get(headers.`Content-Type`) match {
        case Some(headers.`Content-Type`(MediaType.`application/x-www-form-urlencoded`, _))
            if checkRequest(req) =>
          for {
            decoded <- OptionT.liftF(UrlForm.entityDecoder[F].decode(req, strictDecode).value)
            resp <- decoded.fold(
              mf => OptionT.liftF(mf.toHttpResponse[F](req.httpVersion)),
              addUrlForm
            )
          } yield resp

        case _ => routes(req)
      }
    }

  private def checkRequest[F[_]](req: Request[F]): Boolean =
    req.method == Method.POST || req.method == Method.PUT
}
