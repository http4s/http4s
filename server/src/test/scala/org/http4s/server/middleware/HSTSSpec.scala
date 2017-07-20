package org.http4s
package server
package middleware

import org.http4s.dsl._
import org.http4s.headers._

import scala.concurrent.duration._

class HSTSSpec extends Http4sSpec {
  "HSTS" should {
    "add the Strict-Transport-Security header" in {
      val service = HSTS(HttpService {
        case GET -> Root =>
          Ok("pong")
      }, maxAge = 365.days)
      val req = Request(Method.GET, Uri.uri("/"))
      val resp = service.orNotFound(req).unsafeRun
      resp.status must_== (Status.Ok)
      resp.headers.get(`Strict-Transport-Security`) must beSome
    }
  }
}
