package org.http4s
package server
package middleware

import java.nio.charset.StandardCharsets

import org.http4s.Status._

import org.specs2.mutable.Specification

class UriTranslationSpec extends Http4sSpec {

  val service = HttpService {
    case r if r.pathInfo == "/foo" => Response(Ok).withBody("foo")

    case r if r.pathInfo == "/checkattr" =>
      val s = r.scriptName + " " + r.pathInfo
      Response(Ok).withBody(s)

    case r => Response.notFound(r)
  }

  val trans1 = URITranslation.translateRoot("/http4s")(service)
  val trans2 = URITranslation.translateRoot("http4s")(service)

  "UriTranslation" should {
    "match a matching request" in {
      val req = Request(uri = Uri(path = "/http4s/foo"))
      trans1.apply(req).run.status must_== (Ok)
      trans2.apply(req).run.status must_== (Ok)
      service.apply(req).run.status must_== (NotFound)
    }

    "Not match a request missing the prefix" in {
      val req = Request(uri = Uri(path = "/foo"))
      trans1.apply(req).run.status must_== (NotFound)
      trans2.apply(req).run.status must_== (NotFound)
      service.apply(req).run.status must_== (Ok)
    }

    "Split the Uri into scriptName and pathInfo" in {
      val req = Request(uri = Uri(path = "/http4s/checkattr"))
      val resp = trans1.apply(req).run
      resp.status must_== (Ok)
      val s = new String(resp.body.runLog.run.reduce(_ ++ _).toArray, StandardCharsets.UTF_8)
      s must_== ("/http4s /checkattr")
    }
  }

}
