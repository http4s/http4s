package org.http4s.server.middleware

import java.nio.charset.StandardCharsets

import org.http4s.{Uri, Request, Response, Header, Headers, HeaderKey}
import org.http4s.server.HttpService
import org.http4s.Status._
import org.http4s.headers._

import org.specs2.mutable.Specification

import scalaz._
import Scalaz._

class CORSSpec extends Specification {

  val service = HttpService {
    case r => Response(Ok).withBody("foo")
  }

  val cors1 = CORS(service)
  val cors2 = CORS(service, CORSConfig(
    anyOrigin = false,
    allowCredentials = false,
    maxAge = 0,
    allowedOrigins = Some(Set("http://allowed.com/"))))

  def headerCheck(h: Header) = h is `Access-Control-Max-Age`
  def matchHeader(hs: Headers, hk: HeaderKey.Extractable, expected: String) =
    hs.get(hk).cata(_.value === expected, 1 === 0)

  "CORS" should {
    "Not showup if unrequested" in {
      val req = Request(uri = Uri(path = "foo"))
      cors1(req).map(_.headers must not contain(headerCheck _)).run
      cors2(req).map(_.headers must not contain(headerCheck _)).run
    }

    "Respect Access-Control-Allow-Credentials" in {
      val req = Request(uri = Uri(path= "foo")).withHeaders(
        Header("Origin", "http://allowed.com/"),
        Header("Access-Control-Request-Method", "GET")
      )
      cors1(req).map((resp: Response) => matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "true")).run
      cors2(req).map((resp: Response) => matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "false")).run
    }
  }

}
