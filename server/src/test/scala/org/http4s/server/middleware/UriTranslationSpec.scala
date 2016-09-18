package org.http4s
package server
package middleware

import java.nio.charset.StandardCharsets

import org.http4s.dsl._

class UriTranslationSpec extends Http4sSpec {

  val service = HttpService {
    case _ -> Root / "foo" =>
      Ok("foo")

    case r @ _ -> Root / "checkattr" =>
      val s = r.scriptName + " " + r.pathInfo
      Ok(s)
  }

  val trans1 = URITranslation.translateRoot("/http4s")(service)
  val trans2 = URITranslation.translateRoot("http4s")(service)

  "UriTranslation" should {
    "match a matching request" in {
      val req = Request(uri = Uri(path = "/http4s/foo"))
      trans1.apply(req) must returnStatus (Ok)
      trans2.apply(req) must returnStatus (Ok)
      service.apply(req) must returnStatus (NotFound)
    }

    "Not match a request missing the prefix" in {
      val req = Request(uri = Uri(path = "/foo"))
      trans1.apply(req) must returnStatus (NotFound)
      trans2.apply(req) must returnStatus (NotFound)
      service.apply(req) must returnStatus (Ok)
    }

    "Split the Uri into scriptName and pathInfo" in {
      val req = Request(uri = Uri(path = "/http4s/checkattr"))
      trans1(req) must returnStatus (Ok)
      trans1(req) must returnBody ("/http4s /checkattr")
    }
  }
}
