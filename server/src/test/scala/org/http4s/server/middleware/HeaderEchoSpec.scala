/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats.Functor
import cats.effect.IO
import cats.syntax.functor._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.Uri.uri
import org.typelevel.ci.CIString

class HeaderEchoSpec extends Http4sSpec {
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

    testee
      .apply(requestMatchingSingleHeaderKey)
      .map(_.headers)
      .map { responseHeaders =>
        responseHeaders.exists(_.value == "someheadervalue") must_== true
        (responseHeaders.toList must have).size(3)
      }
  }

  "HeaderEcho" should {
    "echo a single header in addition to the defaults" in {
      testSingleHeader(HeaderEcho(_ == CIString("someheaderkey"))(testService).orNotFound)
        .unsafeRunSync()
    }

    "echo multiple headers" in {
      val requestMatchingMultipleHeaderKeys =
        Request[IO](
          uri = uri("/request"),
          headers = Headers.of(
            Header("someheaderkey", "someheadervalue"),
            Header("anotherheaderkey", "anotherheadervalue")))
      val headersToEcho =
        List(CIString("someheaderkey"), CIString("anotherheaderkey"))
      val testee = HeaderEcho(headersToEcho.contains(_))(testService)

      val responseHeaders =
        testee.orNotFound(requestMatchingMultipleHeaderKeys).unsafeRunSync().headers

      responseHeaders.exists(_.value == "someheadervalue") must_== true
      responseHeaders.exists(_.value == "anotherheadervalue") must_== true
      (responseHeaders.toList must have).size(4)
    }

    "echo only the default headers where none match the key" in {
      val requestMatchingNotPresentHeaderKey =
        Request[IO](
          uri = uri("/request"),
          headers = Headers.of(Header("someunmatchedheader", "someunmatchedvalue")))

      val testee = HeaderEcho(_ == CIString("someheaderkey"))(testService)
      val responseHeaders =
        testee.orNotFound(requestMatchingNotPresentHeaderKey).unsafeRunSync().headers

      responseHeaders.exists(_.value == "someunmatchedvalue") must_== false
      (responseHeaders.toList must have).size(2)
    }

    "be created via the httpRoutes constructor" in {
      testSingleHeader(
        HeaderEcho.httpRoutes(_ == CIString("someheaderkey"))(testService).orNotFound)
        .unsafeRunSync()
    }

    "be created via the httpApps constructor" in {
      testSingleHeader(HeaderEcho.httpApp(_ == CIString("someheaderkey"))(testService.orNotFound))
        .unsafeRunSync()
    }
  }
}
