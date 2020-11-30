/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats._
import cats.implicits._
import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.Uri.uri
import org.http4s.syntax.all._
import org.typelevel.ci.CIString

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
      HeaderEcho(_ === CIString("someheaderkey"))(testService).orNotFound
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
      List(CIString("someheaderkey"), CIString("anotherheaderkey"))
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

    val testee = HeaderEcho(_ == CIString("someheaderkey"))(testService)
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
    testSingleHeader(HeaderEcho.httpRoutes(_ == CIString("someheaderkey"))(testService).orNotFound)
      .assertEquals(true)
  }

  test("be created via the httpApps constructor") {
    testSingleHeader(HeaderEcho.httpApp(_ == CIString("someheaderkey"))(testService.orNotFound))
      .assertEquals(true)
  }
}
