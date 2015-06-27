package org.http4s.server.middleware

import java.nio.charset.StandardCharsets

import org.http4s.{Http4sSpec, Uri, Request, Response}
import org.http4s.server.HttpService
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
      trans1(req).run.status must equal (Ok)
      trans2(req).run.status must equal (Ok)
      service(req).run.status must equal (NotFound)
    }

    "Not match a request missing the prefix" in {
      val req = Request(uri = Uri(path = "/foo"))
      trans1(req).run.status must equal (NotFound)
      trans2(req).run.status must equal (NotFound)
      service(req).run.status must equal (Ok)
    }

    "Split the Uri into scriptName and pathInfo" in {
      val req = Request(uri = Uri(path = "/http4s/checkattr"))
      val resp = trans1(req).run
      resp.status must equal (Ok)
      val s = new String(resp.body.runLog.run.reduce(_ ++ _).toArray, StandardCharsets.UTF_8)
      s must equal("/http4s /checkattr")
    }
  }

}
