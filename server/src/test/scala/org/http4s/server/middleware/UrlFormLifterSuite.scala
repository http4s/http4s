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

import cats.data.OptionT
import cats.effect._
import cats.syntax.applicative._
import org.http4s.dsl.io._
import org.http4s.syntax.all._

class UrlFormLifterSuite extends Http4sSuite {
  val urlForm = UrlForm("foo" -> "bar")

  val app = UrlFormLifter(OptionT.liftK[IO])(HttpRoutes.of[IO] { case r @ POST -> _ =>
    r.uri.multiParams.get("foo") match {
      case Some(ps) =>
        Ok(ps.mkString(","))
      case None =>
        BadRequest("No Foo")
    }
  }).orNotFound

  test("Add application/x-www-form-urlencoded bodies to the query params") {
    val req = Request[IO](method = POST).withEntity(urlForm).pure[IO]
    req.flatMap(app.run).map(_.status).assertEquals(Ok)
  }

  test("Add application/x-www-form-urlencoded bodies after query params") {
    val req =
      Request[IO](method = Method.POST, uri = Uri.uri("/foo?foo=biz"))
        .withEntity(urlForm)
        .pure[IO]
    req.flatMap(app.run).map(_.status).assertEquals(Ok) *>
      req.flatMap(app.run).flatMap(_.as[String]).assertEquals("biz,bar")
  }

  test("Ignore Requests that don't have application/x-www-form-urlencoded bodies") {
    val req = Request[IO](method = Method.POST).withEntity("foo").pure[IO]
    req.flatMap(app.run).map(_.status).assertEquals(BadRequest)
  }
}
