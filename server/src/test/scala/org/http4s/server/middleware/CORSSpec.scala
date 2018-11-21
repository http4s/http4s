package org.http4s
package server
package middleware

import cats.effect._
import cats.implicits._
import org.http4s.dsl.io._
import org.http4s.headers._

class CORSSpec extends Http4sSpec {

  val routes = HttpRoutes.of[IO] {
    case req if req.pathInfo == "/foo" => Response[IO](Ok).withEntity("foo").pure[IO]
    case req if req.pathInfo == "/bar" => Response[IO](Unauthorized).withEntity("bar").pure[IO]
  }

  val cors1 = CORS(routes)
  val cors2 = CORS(
    routes,
    CORSConfig(
      anyOrigin = false,
      allowCredentials = false,
      maxAge = 0,
      allowedOrigins = Set("http://allowed.com"),
      allowedHeaders = Some(Set("User-Agent", "Keep-Alive", "Content-Type")),
      exposedHeaders = Some(Set("x-header"))
    )
  )

  def headerCheck(h: Header) = h.is(`Access-Control-Max-Age`)
  def matchHeader(hs: Headers, hk: HeaderKey.Extractable, expected: String) =
    hs.get(hk).fold(false)(_.value === expected)

  def buildRequest(path: String, method: Method = GET) =
    Request[IO](uri = Uri(path = path), method = method).withHeaders(
      Header("Origin", "http://allowed.com"),
      Header("Access-Control-Request-Method", "GET"))

  "CORS" should {
    "Be omitted when unrequested" in {
      val req = buildRequest("foo")
      cors1.orNotFound(req).map(_.headers) must returnValue(contain(headerCheck _).not)
      cors2.orNotFound(req).map(_.headers) must returnValue(contain(headerCheck _).not)
    }

    "Respect Access-Control-Allow-Credentials" in {
      val req = buildRequest("/foo")
      cors1
        .orNotFound(req)
        .map(resp => matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "true"))
        .unsafeRunSync()
      cors2
        .orNotFound(req)
        .map(resp => matchHeader(resp.headers, `Access-Control-Allow-Credentials`, "false"))
        .unsafeRunSync()
    }

    "Respect Access-Control-Allow-Headers in preflight call" in {
      val req = buildRequest("/foo", OPTIONS)
      cors2
        .orNotFound(req)
        .map { resp =>
          matchHeader(
            resp.headers,
            `Access-Control-Allow-Headers`,
            "User-Agent, Keep-Alive, Content-Type")
        }
        .unsafeRunSync()
    }

    "Respect Access-Control-Expose-Headers in non-preflight call" in {
      val req = buildRequest("/foo")
      cors2
        .orNotFound(req)
        .map { resp =>
          matchHeader(resp.headers, `Access-Control-Expose-Headers`, "x-header")
        }
        .unsafeRunSync()
    }

    "Respect Access-Control-Allow-Headers in preflight call" in {
      val req = buildRequest("/foo", OPTIONS)
      cors2
        .orNotFound(req)
        .map { resp =>
          matchHeader(
            resp.headers,
            `Access-Control-Allow-Headers`,
            "User-Agent, Keep-Alive, Content-Type")
        }
        .unsafeRunSync()
    }

    "Respect Access-Control-Expose-Headers in non-preflight call" in {
      val req = buildRequest("/foo")
      cors2
        .orNotFound(req)
        .map { resp =>
          matchHeader(resp.headers, `Access-Control-Expose-Headers`, "x-header")
        }
        .unsafeRunSync()
    }

    "Offer a successful reply to OPTIONS on fallthrough" in {
      val req = buildRequest("/unexistant", OPTIONS)
      cors1
        .orNotFound(req)
        .map(
          resp =>
            resp.status.isSuccess && matchHeader(
              resp.headers,
              `Access-Control-Allow-Credentials`,
              "true"))
        .unsafeRunSync()
      cors2
        .orNotFound(req)
        .map(
          resp =>
            resp.status.isSuccess && matchHeader(
              resp.headers,
              `Access-Control-Allow-Credentials`,
              "false"))
        .unsafeRunSync()
    }

    "Always respond with 200 and empty body for OPTIONS request" in {
      val req = buildRequest("/bar", OPTIONS)
      cors1.orNotFound(req).map(_.headers must contain(headerCheck _)).unsafeRunSync()
      cors2.orNotFound(req).map(_.headers must contain(headerCheck _)).unsafeRunSync()
    }

    "Respond with 403 when origin is not valid" in {
      val req = buildRequest("/bar").withHeaders(Header("Origin", "http://blah.com/"))
      cors2.orNotFound(req).map(resp => resp.status.code == 403).unsafeRunSync()
    }

    "Fall through" in {
      val req = buildRequest("/2")
      val routes1 = CORS(HttpRoutes.of[IO] { case GET -> Root / "1" => Ok() })
      val routes2 = CORS(HttpRoutes.of[IO] { case GET -> Root / "2" => Ok() })
      (routes1 <+> routes2).orNotFound(req) must returnStatus(Ok)
    }

    "Not replace vary header if already set" in {
      val req = buildRequest("/")
      val service = CORS(HttpRoutes.of[IO] {
        case GET -> Root =>
          Response[IO](Ok)
            .putHeaders(Header("Vary", "Origin,Accept"))
            .withEntity("foo")
            .pure[IO]
      })

      service
        .orNotFound(req)
        .map { resp =>
          matchHeader(resp.headers, `Vary`, "Origin,Accept")
        }
        .unsafeRunSync()
    }
  }
}
