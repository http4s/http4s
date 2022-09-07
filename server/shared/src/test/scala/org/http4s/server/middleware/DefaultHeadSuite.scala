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
import cats.effect.kernel.Ref
import cats.implicits._
import fs2.Stream
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.typelevel.ci._

class DefaultHeadSuite extends Http4sSuite {
  private val httpRoutes = HttpRoutes.of[IO] {
    case GET -> Root / "hello" =>
      Ok("hello")

    case GET -> Root / "special" =>
      Ok().map(_.putHeaders("X-Handled-By" -> "GET"))

    case HEAD -> Root / "special" =>
      Ok().map(_.putHeaders("X-Handled-By" -> "HEAD"))
  }

  private val app = DefaultHead(httpRoutes).orNotFound

  test("honor HEAD routes") {
    val req = Request[IO](Method.HEAD, uri = uri"/special")
    app(req)
      .map(_.headers.get(ci"X-Handled-By").map(_.head.value))
      .assertEquals(Some("HEAD"))
  }

  test("return truncated body of corresponding GET on fallthrough") {
    val req = Request[IO](Method.HEAD, uri = uri"/hello")
    app(req).flatMap(_.as[String]).assertEquals("")
  }

  test("retain all headers of corresponding GET on fallthrough") {
    val get = Request[IO](Method.GET, uri = uri"/hello")
    val head = get.withMethod(Method.HEAD)
    val getHeaders = app(get).map(_.headers)
    val headHeaders = app(head).map(_.headers)
    (getHeaders, headHeaders).parMapN(_ === _).assert
  }

  test("allow GET body to clean up on fallthrough") {
    (for {
      open <- Ref[IO].of(false)
      route = HttpRoutes.of[IO] { case GET -> _ =>
        val body: EntityBody[IO] =
          Stream.bracket(open.set(true))(_ => open.set(false)).flatMap(_ => Stream.never[IO])
        Ok(body)
      }
      app = DefaultHead(route).orNotFound
      resp <- app(Request[IO](Method.HEAD))
      _ <- resp.body.compile.drain
      leaked <- open.get
    } yield leaked).assertEquals(false)
  }

  test("be created via the httpRoutes constructor") {
    val req = Request[IO](Method.HEAD, uri = uri"/hello")
    DefaultHead.httpRoutes(httpRoutes).orNotFound(req).flatMap(_.as[String]).assertEquals("")
  }
}
