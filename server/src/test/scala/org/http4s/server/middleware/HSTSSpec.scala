package org.http4s
package server
package middleware

import cats.effect._
import org.http4s.dsl._
import org.http4s.headers._

import scala.concurrent.duration._

class HSTSSpec extends Http4sSpec {
  val innerService = HttpService[IO] {
      case GET -> Root =>
        Ok("pong")
    }

  val req = Request[IO](Method.GET, Uri.uri("/"))

  "HSTS" should {
    "add the Strict-Transport-Security header" in {
      val service = HSTS.unsafeFromDuration(innerService, 365.days)
      val resp = service.orNotFound(req).unsafeRunSync
      resp.status must_== Status.Ok
      resp.headers.get(`Strict-Transport-Security`) must beSome
    }
    "support custom headers" in {
      val hstsHeader = `Strict-Transport-Security`.unsafeFromDuration(365.days, preload = true)
      val service = HSTS(innerService, hstsHeader)
      val resp = service.orNotFound(req).unsafeRunSync
      resp.status must_== Status.Ok
      resp.headers.get(`Strict-Transport-Security`) must beSome
    }
    "have a sensible default" in {
      val service = HSTS(innerService)
      val resp = service.orNotFound(req).unsafeRunSync
      resp.status must_== Status.Ok
      resp.headers.get(`Strict-Transport-Security`) must beSome
    }
  }
}
