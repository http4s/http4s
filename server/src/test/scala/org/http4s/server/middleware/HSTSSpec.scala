package org.http4s
package server
package middleware

import cats.effect._
import org.http4s.dsl.io._
import org.http4s.headers._
import scala.concurrent.duration._

class HSTSSpec extends Http4sSpec {

  val innerRoutes = HttpRoutes.of[IO] {
    case GET -> Root =>
      Ok("pong")
  }

  val req = Request[IO](Method.GET, Uri.uri("/"))

  "HSTS" should {

    "add the Strict-Transport-Security header" in {
      val app = HSTS.unsafeFromDuration(innerRoutes, 365.days).orNotFound
      val resp = app(req).unsafeRunSync
      resp.status must_== Status.Ok
      resp.headers.get(`Strict-Transport-Security`) must beSome
    }

    "support custom headers" in {
      val hstsHeader = `Strict-Transport-Security`.unsafeFromDuration(365.days, preload = true)
      val app = HSTS(innerRoutes, hstsHeader).orNotFound
      val resp = app(req).unsafeRunSync
      resp.status must_== Status.Ok
      resp.headers.get(`Strict-Transport-Security`) must beSome
    }

    "have a sensible default" in {
      val app = HSTS(innerRoutes).orNotFound
      val resp = app(req).unsafeRunSync
      resp.status must_== Status.Ok
      resp.headers.get(`Strict-Transport-Security`) must beSome
    }

  }
}
