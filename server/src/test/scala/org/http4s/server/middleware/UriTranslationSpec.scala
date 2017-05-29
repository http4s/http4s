package org.http4s
package server
package middleware

import cats.effect._
import org.http4s.dsl._

class UriTranslationSpec extends Http4sSpec {

  val service = HttpService[IO] {
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
      val req = Request[IO](uri = Uri(path = "/http4s/foo"))
      trans1.orNotFound(req) must returnStatus (Ok)
      trans2.orNotFound(req) must returnStatus (Ok)
      service.orNotFound(req) must returnStatus (NotFound)
    }

    "Not match a request missing the prefix" in {
      val req = Request[IO](uri = Uri(path = "/foo"))
      trans1.orNotFound(req) must returnStatus (NotFound)
      trans2.orNotFound(req) must returnStatus (NotFound)
      service.orNotFound(req) must returnStatus (Ok)
    }

    "Split the Uri into scriptName and pathInfo" in {
      val req = Request[IO](uri = Uri(path = "/http4s/checkattr"))
      val resp = trans1.orNotFound(req).unsafeRunSync()
      resp.status must be (Ok)
      resp must haveBody ("/http4s /checkattr")
    }
  }
}
