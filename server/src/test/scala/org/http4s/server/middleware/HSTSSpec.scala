/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

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
      List(
        HSTS.unsafeFromDuration(innerRoutes, 365.days).orNotFound,
        HSTS.httpRoutes.unsafeFromDuration(innerRoutes, 365.days).orNotFound,
        HSTS.httpApp.unsafeFromDuration(innerRoutes.orNotFound, 365.days)
      ).map { app =>
        val resp = app(req).unsafeRunSync()
        resp.status must_== Status.Ok
        resp.headers.get(`Strict-Transport-Security`) must beSome
      }
    }

    "support custom headers" in {
      val hstsHeader = `Strict-Transport-Security`.unsafeFromDuration(365.days, preload = true)

      List(
        HSTS(innerRoutes, hstsHeader).orNotFound,
        HSTS.httpRoutes(innerRoutes).orNotFound,
        HSTS.httpApp(innerRoutes.orNotFound)
      ).map { app =>
        val resp = app(req).unsafeRunSync()
        resp.status must_== Status.Ok
        resp.headers.get(`Strict-Transport-Security`) must beSome
      }
    }

    "have a sensible default" in {
      List(
        HSTS(innerRoutes).orNotFound,
        HSTS.httpRoutes(innerRoutes).orNotFound,
        HSTS.httpApp(innerRoutes.orNotFound)
      ).map { app =>
        val resp = app(req).unsafeRunSync()
        resp.status must_== Status.Ok
        resp.headers.get(`Strict-Transport-Security`) must beSome
      }
    }
  }
}
