package org.http4s
package server
package middleware

import cats.implicits._
import org.http4s.server.syntax._
import org.http4s.dsl._
import org.http4s.headers._

class GZipSpec extends Http4sSpec {
  "GZip" should {
    "fall through if the route doesn't match" in {
      val service = GZip(HttpService.empty) |+| HttpService {
        case GET -> Root =>
          Ok("pong")
      }
      val req = Request(Method.GET, Uri.uri("/"))
        .putHeaders(`Accept-Encoding`(ContentCoding.gzip))
      val resp = service.orNotFound(req).unsafeRun
      resp.status must_== (Status.Ok)
      resp.headers.get(`Content-Encoding`) must beNone
    }
  }
}
