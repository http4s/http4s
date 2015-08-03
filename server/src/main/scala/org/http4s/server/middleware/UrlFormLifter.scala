package org.http4s
package server.middleware

import org.http4s.server.{Service, HttpService}

import scalaz.concurrent.Task


object UrlFormLifter {

  def apply(service: HttpService): HttpService =  Service.lift { req =>

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
          .decode(req)
          .run
          .flatMap(_.fold(failure, addUrlForm))

      case None => service(req)
    }
  }

  private def failure(failure: ParseFailure): Task[Response] =
    Response(Status.BadRequest).withBody("400 BadRequest.\n" + failure.sanitized)

  private def checkRequest(req: Request): Boolean = {
    req.method == Method.POST || req.method == Method.PUT
  }
}
