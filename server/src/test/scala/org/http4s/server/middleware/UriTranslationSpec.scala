package org.http4s.server.middleware

import java.nio.charset.StandardCharsets

import org.http4s.{Uri, Request, ResponseBuilder}
import org.http4s.server.HttpService
import org.http4s.Status._

import org.specs2.mutable.Specification

class UriTranslationSpec extends Specification {

  val service = HttpService {
    case r if r.pathInfo == "/foo" => ResponseBuilder(Ok, "foo")

    case r if r.pathInfo == "/checkattr" =>
      val s = r.attributes.get(URITranslation.translateRootKey)
               .map(f => f("/cat"))
               .getOrElse("None")
      ResponseBuilder(Ok, s)

    case r => ResponseBuilder.notFound(r)
  }

  val trans1 = URITranslation.translateRoot("/http4s")(service)
  val trans2 = URITranslation.translateRoot("http4s")(service)

  "UriTranslation" should {
    "match a matching request" in {
      val req = Request(uri = Uri(path = "/http4s/foo"))
      trans1(req).run.isDefined must_== true
      trans2(req).run.isDefined must_== true
      service(req).run.get.status must_== NotFound
    }

    "Not match a request missing the prefix" in {
      val req = Request(uri = Uri(path = "/foo"))
      trans1(req).run must_== None
      trans2(req).run must_== None
      service(req).run.isDefined must_== true
    }

    "Add the translation function" in {
      val req = Request(uri = Uri(path = "/http4s/checkattr"))
      val resp = trans1(req).run
      resp.isDefined must_== true
      val s = new String(resp.get.body.runLog.run.reduce(_ ++ _).toArray, StandardCharsets.UTF_8)
      s must_== "/http4s/cat"
    }
  }

}
