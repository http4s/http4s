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
    headers = Headers(Header("age", "15"))
  )

  "optics" should {
    "match method" in {
      (request.method composePrism method.GET).isMatching(req) must_== true
    }

    "get header" in {
      (request.headers composeLens at("age".ci)).get(req) must_== Some(Header("age", "15"))
    }

    "increase header" in {
      (request.headers composeOptional
        index("age".ci) composeLens
        header.value composePrism
        stringToInt
      ).set(10)(req) must equal (req.withHeaders(Header("age", "10")))
    }
  }



}
