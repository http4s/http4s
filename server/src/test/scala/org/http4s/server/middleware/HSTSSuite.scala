/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package middleware

import cats.effect._
import cats.implicits._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.syntax.all._
import scala.concurrent.duration._

class HSTSSuite extends Http4sSuite {
  val innerRoutes = HttpRoutes.of[IO] { case GET -> Root =>
    Ok("pong")
  }

  val req = Request[IO](Method.GET, Uri.uri("/"))

  test("add the Strict-Transport-Security header") {
    List(
      HSTS.unsafeFromDuration(innerRoutes, 365.days).orNotFound,
      HSTS.httpRoutes.unsafeFromDuration(innerRoutes, 365.days).orNotFound,
      HSTS.httpApp.unsafeFromDuration(innerRoutes.orNotFound, 365.days)
    ).traverse { app =>
      app(req).map(_.status).assertEquals(Status.Ok) *>
        app(req).map(_.headers.get(`Strict-Transport-Security`).isDefined).assertEquals(true)
    }
  }

  test("support custom headers") {
    val hstsHeader = `Strict-Transport-Security`.unsafeFromDuration(365.days, preload = true)

    List(
      HSTS(innerRoutes, hstsHeader).orNotFound,
      HSTS.httpRoutes(innerRoutes).orNotFound,
      HSTS.httpApp(innerRoutes.orNotFound)
    ).traverse { app =>
      app(req).map(_.status).assertEquals(Status.Ok) *>
        app(req).map(_.headers.get(`Strict-Transport-Security`).isDefined).assertEquals(true)
    }
  }

  test("have a sensible default test") {
    List(
      HSTS(innerRoutes).orNotFound,
      HSTS.httpRoutes(innerRoutes).orNotFound,
      HSTS.httpApp(innerRoutes.orNotFound)
    ).traverse { app =>
      app(req).map(_.status).assertEquals(Status.Ok) *>
        app(req).map(_.headers.get(`Strict-Transport-Security`).isDefined).assertEquals(true)
    }
  }

}
