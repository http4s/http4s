package org.http4s
package server
package middleware

import java.nio.charset.StandardCharsets

import org.http4s.Status._
import org.http4s.Method._
import org.http4s.headers._

import org.specs2.mutable.Specification

class CORSSpec extends Http4sSpec {

  val service = HttpService {
    case req if req.pathInfo == "/foo" => Response(Ok).withBody("foo")
    case req if req.pathInfo == "/bar" => Response(Unauthorized).withBody("bar")
  }

  val cors1 = CORS(service)
  val cors2 = CORS(service, CORSConfig(
    anyOrigin = false,
    allowCredentials = false,
    maxAge = 0,
    allowedOrigins = Some(Set("http://allowed.com/"))))

  def headerCheck(h: Header) = h is `Access-Control-Max-Age`
  def matchHeader(hs: Headers, hk: HeaderKey.Extractable, expected: String) =
    hs.get(hk).fold(false)(_.value === expected)

  def buildRequest(path: String, method: Method = GET) =
    Request(uri = Uri(path= path), method = method).replaceAllHeaders(
      Header("Origin", "http://allowed.com/"),
      Header("Access-Control-Request-Method", "GET"))

  "CORS" should {
    "Be omitted when unrequested" in {
      val req = Request(uri = Uri(path = "foo"))
      cors1.orNotFound(req).map(_.headers) must returnValue(contain(headerCheck _).not)
      cors2.orNotFound(req).map(_.headers) must returnValue(contain(headerCheck _).not)
    }

    "Respect Access-Control-Allow-Credentials" in {
      val req = buildRequest("/foo")
      cors1.orNotFound(req).map((resp: Response) => matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "true")).unsafeRun
      cors2.orNotFound(req).map((resp: Response) => matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "false")).unsafeRun
    }

    "Offer a successful reply to OPTIONS on fallthrough" in {
      val req = buildRequest("/unexistant", OPTIONS)
      cors1.orNotFound(req).map((resp: Response) => resp.status.isSuccess && matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "true")).unsafeRun
      cors2.orNotFound(req).map((resp: Response) => resp.status.isSuccess && matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "false")).unsafeRun
    }

    "Always Respect unsuccesful replies to OPTIONS requests" in {
      val req = buildRequest("/bar", OPTIONS)
      cors1.orNotFound(req).map(_.headers must not contain(headerCheck _)).unsafeRun
      cors2.orNotFound(req).map(_.headers must not contain(headerCheck _)).unsafeRun
    }
  }
}
