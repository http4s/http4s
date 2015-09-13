package org.http4s.optics

import monocle.function.all._
import monocle.std.all._
import org.http4s.Uri.{Authority, RegName}
import org.http4s._
import org.http4s.optics.headers._

import scalaz.NonEmptyList

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
      (request.headers composeLens at("age".ci)).get(req) must_== Some(NonEmptyList("15"))
    }

    "set header" in {
      (request.headers composeLens at("age".ci) composeIso optNelToList composePrism stringToInt.below)
        .set(List(10))(req) must equal (req.copy(headers =
          Headers(Header("age", "10"), Header("x-custom1", "hello"), Header("x-custom2", "20"))))
    }

    "multi header update" in {
      (request.headers composeTraversal each composeTraversal each composePrism stringToInt)
        .modify(_ + 1)(req) must equal (Request(
        method = Method.GET,
        uri = Uri(authority = Some(Authority(host = RegName("localhost"), port = Some(8080))), path = "/ping"),
        headers = Headers(Header("age", "16"), Header("x-custom1", "hello"), Header("x-custom2", "21"))
      ))
    }
  }



}
