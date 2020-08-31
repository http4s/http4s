/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package middleware

import cats.effect._
import cats.effect.concurrent.Ref
import fs2.Stream
import org.http4s.Uri.uri
import org.http4s.dsl.io._
import org.http4s.testing.Http4sLegacyMatchersIO
import org.typelevel.ci.CIString

class DefaultHeadSpec extends Http4sSpec with Http4sLegacyMatchersIO {
  val httpRoutes = HttpRoutes.of[IO] {
    case GET -> Root / "hello" =>
      Ok("hello")

    case GET -> Root / "special" =>
      Ok(Header("X-Handled-By", "GET"))

    case HEAD -> Root / "special" =>
      Ok(Header("X-Handled-By", "HEAD"))
  }
  val app = DefaultHead(httpRoutes).orNotFound

  "DefaultHead" should {
    "honor HEAD routes" in {
      val req = Request[IO](Method.HEAD, uri = uri("/special"))
      app(req).map(_.headers.get(CIString("X-Handled-By")).map(_.value)) must returnValue(
        Some("HEAD"))
    }

    "return truncated body of corresponding GET on fallthrough" in {
      val req = Request[IO](Method.HEAD, uri = uri("/hello"))
      app(req) must returnBody("")
    }

    "retain all headers of corresponding GET on fallthrough" in {
      val get = Request[IO](Method.GET, uri = uri("/hello"))
      val head = get.withMethod(Method.HEAD)
      val getHeaders = app(get).map(_.headers).unsafeRunSync()
      val headHeaders = app(head).map(_.headers).unsafeRunSync()
      getHeaders must_== headHeaders
    }

    "allow GET body to clean up on fallthrough" in {
      (for {
        open <- Ref[IO].of(false)
        route = HttpRoutes.of[IO] {
          case GET -> _ =>
            val body: EntityBody[IO] =
              Stream.bracket(open.set(true))(_ => open.set(false)).flatMap(_ => Stream.never[IO])
            Ok(body)
        }
        app = DefaultHead(route).orNotFound
        resp <- app(Request[IO](Method.HEAD))
        _ <- resp.body.compile.drain
        leaked <- open.get
      } yield leaked).unsafeRunSync() must beFalse
    }

    "be created via the httpRoutes constructor" in {
      val req = Request[IO](Method.HEAD, uri = uri("/hello"))
      DefaultHead.httpRoutes(httpRoutes).orNotFound(req) must returnBody("")
    }
  }
}
