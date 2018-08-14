package org.http4s
package server
package middleware

import cats.effect._
import org.http4s.dsl.io._

class TranslateUriSpec extends Http4sSpec {

  val service = HttpService[IO] {
    case _ -> Root / "foo" =>
      Ok("foo")

    case r @ _ -> Root / "checkattr" =>
      val s = r.scriptName + " " + r.pathInfo
      Ok(s)
  }

  val trans1 = TranslateUri("/http4s")(service)
  val trans2 = TranslateUri("http4s")(service)

  "UriTranslation" should {
    "match a matching request" in {
      val req = Request[IO](uri = Uri(path = "/http4s/foo"))
      trans1.orNotFound(req) must returnStatus(Ok)
      trans2.orNotFound(req) must returnStatus(Ok)
      service.orNotFound(req) must returnStatus(NotFound)
    }

    "not match a request missing the prefix" in {
      val req = Request[IO](uri = Uri(path = "/foo"))
      trans1.orNotFound(req) must returnStatus(NotFound)
      trans2.orNotFound(req) must returnStatus(NotFound)
      service.orNotFound(req) must returnStatus(Ok)
    }

    "not match a request with a different prefix" in {
      val req = Request[IO](uri = Uri(path = "/http5s/foo"))
      trans1.orNotFound(req) must returnStatus(NotFound)
      trans2.orNotFound(req) must returnStatus(NotFound)
      service.orNotFound(req) must returnStatus(NotFound)
    }

    "split the Uri into scriptName and pathInfo" in {
      val req = Request[IO](uri = Uri(path = "/http4s/checkattr"))
      val resp = trans1.orNotFound(req).unsafeRunSync()
      resp.status must be(Ok)
      resp must haveBody("/http4s /checkattr")
    }

    "do nothing for an empty or / prefix" in {
      val emptyPrefix = TranslateUri("")(service)
      val slashPrefix = TranslateUri("/")(service)

      val req = Request[IO](uri = Uri(path = "/foo"))
      emptyPrefix.orNotFound(req) must returnStatus(Ok)
      slashPrefix.orNotFound(req) must returnStatus(Ok)
    }
  }
}
