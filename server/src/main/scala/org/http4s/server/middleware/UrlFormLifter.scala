package org.http4s
package server
package middleware

import scalaz.concurrent.Task

/** [[Middleware]] for lifting application/x-www-form-urlencoded bodies into the
  * request query params.
  *
  * The params are merged into the existing paras _after_ the existing query params. This
  * means that if the query already contains the pair "foo" -> Some("bar"), parameters on
  * the body must be acessed through `multiParams`.
  */
object UrlFormLifter {

  def apply(service: HttpService, strictDecode: Boolean = false): HttpService =  Service.lift { req =>

    def addUrlForm(form: UrlForm): Task[Response] = {
      val flatForm = form.values.toVector.flatMap{ case (k, vs) => vs.map(v => (k,Some(v))) }
      val params = req.uri.query.toVector ++ flatForm: Vector[(String, Option[String])]
      val newQuery = Query(params :_*)

      val newRequest = req.copy(uri = req.uri.copy(query = newQuery), body = EmptyBody)
      service(newRequest)
    }

    req.headers.get(headers.`Content-Type`) match {
      case Some(headers.`Content-Type`(MediaType.`application/x-www-form-urlencoded`,_)) if checkRequest(req) =>
        UrlForm.entityDecoder
          .decode(req, strictDecode)
          .run
          .flatMap(_.fold(_.toHttpResponse(req.httpVersion), addUrlForm))

      case _ => service(req)
    }
  }

  private def checkRequest(req: Request): Boolean = {
    req.method == Method.POST || req.method == Method.PUT
  }
}
