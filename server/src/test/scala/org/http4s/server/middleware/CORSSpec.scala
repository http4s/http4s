package org.http4s
package server
package middleware

import java.nio.charset.StandardCharsets

import org.http4s.dsl._
import org.http4s.headers._
import org.http4s.server.syntax._
import org.specs2.mutable.Specification

import scalaz._
import Scalaz._

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
    allowedOrigins = Set("http://allowed.com/"),
    allowedHeaders = Some(
      Set(
        "User-Agent",
        "Keep-Alive",
        "Content-Type")),
      exposedHeaders = Some(
      Set("x-header"))))

  def headerCheck(h: Header) = h is `Access-Control-Max-Age`
  def matchHeader(hs: Headers, hk: HeaderKey.Extractable, expected: String): Boolean =
    hs.get(hk).cata(_.value === expected, 1 === 0)

  def buildRequest(path: String, method: Method = GET) =
    Request(uri = Uri(path= path), method = method).replaceAllHeaders(
      Header(fn"Origin", fv"http://allowed.com/"),
      Header(fn"Access-Control-Request-Method", fv"GET"))

  "CORS" should {
    "Be omitted when unrequested" in {
      val req = Request(uri = Uri(path = "foo"))
      cors1.orNotFound(req).map(_.headers must not contain(headerCheck _)).unsafePerformSync
      cors2.orNotFound(req).map(_.headers must not contain(headerCheck _)).unsafePerformSync
    }

    "Respect Access-Control-Allow-Credentials" in {
      val req = buildRequest("/foo")
      cors1.orNotFound(req).map(resp => matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "true")).unsafePerformSync
      cors2.orNotFound(req).map(resp => matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "false")).unsafePerformSync
    }

    "Respect Access-Control-Allow-Headers in preflight call" in {
      val req = buildRequest("/foo", OPTIONS)
      cors2.orNotFound(req).map{(resp: Response) =>
        matchHeader(resp.headers, `Access-Control-Allow-Headers`, "User-Agent, Keep-Alive, Content-Type")}.unsafePerformSync
    }

    "Respect Access-Control-Expose-Headers in non-preflight call" in {
      val req = buildRequest("/foo")
      cors2.orNotFound(req).map{(resp: Response) =>
        matchHeader(resp.headers, `Access-Control-Expose-Headers`, "x-header")}.unsafePerformSync
    }

    "Offer a successful reply to OPTIONS on fallthrough" in {
      val req = buildRequest("/unexistant", OPTIONS)
      cors1.orNotFound(req).map(resp => resp.status.isSuccess && matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "true")).unsafePerformSync
      cors2.orNotFound(req).map(resp => resp.status.isSuccess && matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "false")).unsafePerformSync
    }

    "Always respond with 200 and empty body for OPTIONS request" in {
      val req = buildRequest("/bar", OPTIONS)
      cors1.orNotFound(req).map(_.headers must contain(headerCheck _)).unsafePerformSync
      cors2.orNotFound(req).map(_.headers must contain(headerCheck _)).unsafePerformSync
    }

    "Respond with 403 when origin is not valid" in {
      val req = buildRequest("/bar").replaceAllHeaders(Header(fn"Origin", fv"http://blah.com/"))
      cors2.orNotFound(req).map((resp: Response) => resp.status.code == 403).unsafePerformSync
    }
    "Fall through" in {
      val req = buildRequest("/2")
      val s1 = CORS(HttpService { case GET -> Root / "1" => Ok() })
      val s2 = CORS(HttpService { case GET -> Root / "2" => Ok() })
      (s1 orElse s2).orNotFound(req).unsafePerformSync.status must_== Ok
    }
  }
}
