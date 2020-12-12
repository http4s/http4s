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

import cats._
import cats.implicits._
import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.Uri.uri
import org.http4s.syntax.all._
import org.http4s.util.CaseInsensitiveString

class HeaderEchoSuite extends Http4sSuite {
  object someHeaderKey extends HeaderKey.Default
  object anotherHeaderKey extends HeaderKey.Default

  val testService = HttpRoutes.of[IO] { case GET -> Root / "request" =>
    Ok("request response")
  }

  def testSingleHeader[F[_]: Functor, G[_]](testee: Http[F, G]) = {
    val requestMatchingSingleHeaderKey =
      Request[G](
        uri = uri("/request"),
        headers = Headers.of(Header("someheaderkey", "someheadervalue"))
      )

    (testee
      .apply(requestMatchingSingleHeaderKey))
      .map(_.headers)
      .map { responseHeaders =>
        responseHeaders.exists(_.value === "someheadervalue") &&
        responseHeaders.toList.length === 3
      }
  }

  test("echo a single header in addition to the defaults") {
    testSingleHeader(
      HeaderEcho(_ === CaseInsensitiveString("someheaderkey"))(testService).orNotFound
    ).assertEquals(true)
  }

  test("echo multiple headers") {
    val requestMatchingMultipleHeaderKeys =
      Request[IO](
        uri = uri("/request"),
        headers = Headers.of(
          Header("someheaderkey", "someheadervalue"),
          Header("anotherheaderkey", "anotherheadervalue")))
    val headersToEcho =
      List(CaseInsensitiveString("someheaderkey"), CaseInsensitiveString("anotherheaderkey"))
    val testee = HeaderEcho(headersToEcho.contains(_))(testService)

    testee
      .orNotFound(requestMatchingMultipleHeaderKeys)
      .map { r =>
        val responseHeaders = r.headers

        responseHeaders.exists(_.value === "someheadervalue") &&
        responseHeaders.exists(_.value === "anotherheadervalue") &&
        responseHeaders.toList.length === 4
      }
      .assertEquals(true)
  }

  test("echo only the default headers where none match the key") {
    val requestMatchingNotPresentHeaderKey =
      Request[IO](
        uri = uri("/request"),
        headers = Headers.of(Header("someunmatchedheader", "someunmatchedvalue")))

    val testee = HeaderEcho(_ == CaseInsensitiveString("someheaderkey"))(testService)
    testee
      .orNotFound(requestMatchingNotPresentHeaderKey)
      .map { r =>
        val responseHeaders = r.headers

        !responseHeaders.exists(_.value === "someunmatchedvalue") &&
        responseHeaders.toList.length === 2
      }
      .assertEquals(true)
  }

  test("be created via the httpRoutes constructor") {
    testSingleHeader(
      HeaderEcho.httpRoutes(_ == CaseInsensitiveString("someheaderkey"))(testService).orNotFound)
      .assertEquals(true)
  }

  test("be created via the httpApps constructor") {
    testSingleHeader(
      HeaderEcho.httpApp(_ == CaseInsensitiveString("someheaderkey"))(testService.orNotFound))
      .assertEquals(true)
  }
}
