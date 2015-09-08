package org.http4s.optics

import monocle.function.all._
import monocle.std.string._
import org.http4s.Uri.{Authority, RegName}
import org.http4s._
import org.http4s.optics.headers._

class OpticsSpec extends Http4sSpec {

  val req = Request(
    method = Method.GET,
    uri = Uri(authority = Some(Authority(host = RegName("localhost"), port = Some(8080))), path = "/ping"),
    headers = Headers(Header("age", "15"), Header("x-custom1", "hello"), Header("x-custom2", "20"))
  )

  "optics" should {
    "match method" in {
      (request.method composePrism method.GET).isMatching(req) must_== true
    }

    "get header" in {
      (request.headers composeLens at("age".ci)).get(req) must_== Some("15")
    }

    "set header" in {
      (request.headers composeOptional index("age".ci) composePrism stringToInt)
        .set(10)(req) must equal (req.copy(headers = req.headers.put(Header("age", "10"))))
    }

    "multi header update" in {
      (request.headers composeTraversal each composePrism stringToInt)
        .modify(_ + 1)(req) must equal (req.copy(headers =
          req.headers.put(Header("age", "16"), Header("x-custom2", "21"))
        ))
    }
  }



}
