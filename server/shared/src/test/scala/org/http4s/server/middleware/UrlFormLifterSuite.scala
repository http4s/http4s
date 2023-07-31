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

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect._
import cats.syntax.all._
import org.http4s.dsl.io._
import org.http4s.syntax.all._

class UrlFormLifterSuite extends Http4sSuite {
  private val urlForm = UrlForm("foo" -> "bar")

  private val app =
    UrlFormLifter(OptionT.liftK[IO])(HttpRoutes.of[IO] { case r @ POST -> Root / "path" =>
      r.uri.multiParams.get("foo") match {
        case Some(ps) =>
          Ok(ps.mkString(","))
        case None =>
          BadRequest("No Foo")
      }
    }).orNotFound

  test("Add application/x-www-form-urlencoded bodies to the query params") {
    val req = Request[IO](method = POST, uri = uri"/path").withEntity(urlForm).pure[IO]
    req.flatMap(app.run).map(_.status).assertEquals(Ok)
  }

  test("Add application/x-www-form-urlencoded bodies after query params") {
    val req =
      Request[IO](method = Method.POST, uri = uri"/path?foo=biz")
        .withEntity(urlForm)
        .pure[IO]
    req.flatMap(app.run).map(_.status).assertEquals(Ok) *>
      req.flatMap(app.run).flatMap(_.as[String]).assertEquals("biz,bar")
  }

  test("Ignore Requests that don't have application/x-www-form-urlencoded bodies") {
    val req = Request[IO](method = Method.POST, uri = uri"/path").withEntity("foo").pure[IO]
    req.flatMap(app.run).map(_.status).assertEquals(BadRequest)
  }

  test("Respect Uri locality context") {
    val req = Request[IO](method = POST, uri = uri"/some/prefix/path").withEntity(urlForm)
    Router[IO]("/some/prefix" -> app.mapK(OptionT.liftK[IO]))
      .run(req)
      .map(_.status)
      .value
      .assertEquals(Some(Ok))
  }

  test("Do not require auth for public routes") {
    object Foo extends QueryParamDecoderMatcher[String]("foo")
    val authMiddleware = AuthMiddleware[IO, String, String](
      authUser = Kleisli(
        _.params.get("user").fold("invalid user".asLeft[String])(_.asRight[String]).pure[IO]
      ),
      onFailure = Kleisli(_ => Response[IO]().withStatus(Forbidden).pure[OptionT[IO, *]]),
    )
    val app =
      Router[IO]("/some/prefix" -> UrlFormLifter(OptionT.liftK[IO]) {
        authMiddleware {
          AuthedRoutes.of[String, IO] {
            case POST -> Root / "path" :? Foo(foo) as auth => Ok(s"foo: $foo, auth: $auth")
            case _ => BadRequest("No foo")
          }
        }
      }) <+> Router[IO]("/health/check" -> HttpRoutes.of[IO] { case GET -> _ => Ok() })
    val req = Request[IO](method = POST, uri = uri"/some/prefix/path?user=bob").withEntity(urlForm)

    for {
      _ <- app.run(req).map(_.status).value.assertEquals(Some(Ok))
      _ <- app.run(Request[IO](uri = uri"/health/check")).map(_.status).value.assertEquals(Some(Ok))
    } yield {}
  }
}
