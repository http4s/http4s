/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
  private val innerRoutes = HttpRoutes.of[IO] { case GET -> Root =>
    Ok("pong")
  }

  private val req = Request[IO](Method.GET, uri"/")

  test("add the Strict-Transport-Security header") {
    List(
      HSTS.unsafeFromDuration(innerRoutes, 365.days).orNotFound,
      HSTS.httpRoutes.unsafeFromDuration(innerRoutes, 365.days).orNotFound,
      HSTS.httpApp.unsafeFromDuration(innerRoutes.orNotFound, 365.days),
    ).parTraverse_ { app =>
      app(req).map(_.status).assertEquals(Status.Ok) *>
        app(req).map(_.headers.contains[`Strict-Transport-Security`]).assert
    }
  }

  test("support custom headers") {
    val hstsHeader = `Strict-Transport-Security`.unsafeFromDuration(365.days, preload = true)

    List(
      HSTS(innerRoutes, hstsHeader).orNotFound,
      HSTS.httpRoutes(innerRoutes).orNotFound,
      HSTS.httpApp(innerRoutes.orNotFound),
    ).parTraverse_ { app =>
      app(req).map(_.status).assertEquals(Status.Ok) *>
        app(req).map(_.headers.contains[`Strict-Transport-Security`]).assert
    }
  }

  test("have a sensible default test") {
    List(
      HSTS(innerRoutes).orNotFound,
      HSTS.httpRoutes(innerRoutes).orNotFound,
      HSTS.httpApp(innerRoutes.orNotFound),
    ).parTraverse_ { app =>
      app(req).map(_.status).assertEquals(Status.Ok) *>
        app(req).map(_.headers.contains[`Strict-Transport-Security`]).assert
    }
  }

}
