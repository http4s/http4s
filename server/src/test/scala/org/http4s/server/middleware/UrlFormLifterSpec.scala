package org.http4s
package server
package middleware

import org.http4s.dsl._

class UrlFormLifterSpec extends Http4sSpec {
  val urlForm = UrlForm("foo" -> "bar")

  val service = UrlFormLifter(HttpService {
    case r @ POST -> _ =>
      r.uri.multiParams.get("foo") match {
        case Some(ps) =>
          Ok(ps.mkString(","))
        case None =>
          BadRequest("No Foo")
      }
  })

  "UrlFormLifter" should {
    "Add application/x-www-form-urlencoded bodies to the query params" in {
      val req = Request(method = POST).withBody(urlForm)
      req.flatMap(service.orNotFound) must returnStatus(Ok)
    }

    "Add application/x-www-form-urlencoded bodies after query params" in {
      val req = Request(method = Method.POST, uri = Uri.uri("/foo?foo=biz")).withBody(urlForm)
      req.flatMap(service.orNotFound) must returnStatus(Ok)
      req.flatMap(service.orNotFound) must returnBody("biz,bar")
    }

    "Ignore Requests that don't have application/x-www-form-urlencoded bodies" in {
      val req = Request(method = Method.POST).withBody("foo")
      req.flatMap(service.orNotFound) must returnStatus(BadRequest)
    }
  }
}
