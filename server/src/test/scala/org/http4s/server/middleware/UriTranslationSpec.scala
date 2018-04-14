package org.http4s
package server
package middleware

import cats.effect._
import org.http4s.dsl.io._

class UriTranslationSpec extends Http4sSpec {

  val routes = HttpRoutes.of[IO] {
    case _ -> Root / "foo" =>
      Ok("foo")

    case r @ _ -> Root / "checkattr" =>
      val s = r.scriptName + " " + r.pathInfo
      Ok(s)
  }

  val trans1 = URITranslation.translateRoot("/http4s")(routes).orNotFound
  val trans2 = URITranslation.translateRoot("http4s")(routes).orNotFound

  "UriTranslation" should {
    "match a matching request" in {
      val req = Request[IO](uri = Uri(path = "/http4s/foo"))
      trans1(req) must returnStatus(Ok)
      trans2(req) must returnStatus(Ok)
      routes.orNotFound(req) must returnStatus(NotFound)
    }

    "Not match a request missing the prefix" in {
      val req = Request[IO](uri = Uri(path = "/foo"))
      trans1(req) must returnStatus(NotFound)
      trans2(req) must returnStatus(NotFound)
      routes.orNotFound(req) must returnStatus(Ok)
    }

    "Split the Uri into scriptName and pathInfo" in {
      val req = Request[IO](uri = Uri(path = "/http4s/checkattr"))
      val resp = trans1(req).unsafeRunSync()
      resp.status must be(Ok)
      resp must haveBody("/http4s /checkattr")
    }
  }
}
