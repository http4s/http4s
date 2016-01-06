package org.http4s
package server
package middleware

class UrlFormLifterSpec extends Http4sSpec {
  val urlForm = UrlForm("foo" -> "bar")

  val service = UrlFormLifter(HttpService {
    case r@Request(Method.POST,_,_,_,_,_) =>
      r.uri.multiParams.get("foo") match {
        case Some(ps) => Response(status = Status.Ok).withBody(ps.mkString(","))
        case None     => Response(status = Status.BadRequest).withBody("No Foo")
      }
  })

  "UrlFormLifter" should {
    "Add application/x-www-form-urlencoded bodies to the query params" in {
      val req = Request(method = Method.POST).withBody(urlForm).run

      val resp = service.apply(req).run
      resp.status must_== Status.Ok
    }

    "Add application/x-www-form-urlencoded bodies after query params" in {
      val req = Request(method = Method.POST, uri = Uri.uri("/foo?foo=biz")).withBody(urlForm).run

      val resp = service.apply(req).run
      resp.status must_== Status.Ok
      resp.as[String].run must_== "biz,bar"
    }

    "Ignore Requests that don't have application/x-www-form-urlencoded bodies" in {
      val req = Request(method = Method.POST).withBody("foo").run

      val resp = service.apply(req).run
      resp.status must_== Status.BadRequest
    }
  }
}
