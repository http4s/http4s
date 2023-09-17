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

package org.http4s.server.middleware

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.syntax.all._

class JsonpSuite extends Http4sSuite {
  // The implementing regex isn't supported on scala-native.
  // https://github.com/http4s/http4s/pull/6958
  override def munitIgnore: Boolean = Platform.isNative

  private val json = """{"msg": "hi"}"""

  private val routes = {
    import org.http4s.dsl.io._
    val jResp = Ok(json, "Content-Type" -> "application/json")
    HttpRoutes.of[IO] {
      case GET -> Root / "jsonp" => jResp
      case GET -> Root / "notJson" => Ok("hi")
    }
  }

  test("Wrap response when callback param matches and resp in JSON") {
    val req = Request[IO](GET, uri"/jsonp?callback=foo")
    val expected = s"""foo($json);"""
    Jsonp("callback")(routes.orNotFound)
      .run(req)
      .flatMap(_.as[String])
      .assertEquals(expected)
  }

  test("Do not wrap response when callback param matches but resp is not JSON") {
    val req = Request[IO](GET, uri"/notJson?callback=foo")
    val expected = "hi"
    Jsonp("callback")(routes.orNotFound)
      .run(req)
      .flatMap(_.as[String])
      .assertEquals(expected)
  }

  test("Do not wrap response when callback param does not match") {
    val req = Request[IO](GET, uri"/jsonp?wrong=foo")
    val expected = json
    Jsonp("callback")(routes.orNotFound)
      .run(req)
      .flatMap(_.as[String])
      .assertEquals(expected)
  }

  test("BadRequest for invalid callback param value") {
    // 'void' is a javascript keyword and thus not allowed
    val req = Request[IO](GET, uri"/jsonp?callback=void")
    Jsonp("callback")(routes.orNotFound)
      .run(req)
      .map(_.status)
      .assertEquals(Status.BadRequest)
  }
}
