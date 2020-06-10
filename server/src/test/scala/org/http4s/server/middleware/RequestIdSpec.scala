/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.Uri.uri
import org.typelevel.ci.CIString
import java.{util => ju}

class RequestIdSpec extends Http4sSpec {
  private def testService(headerKey: CIString = CIString("X-Request-ID")) = HttpRoutes.of[IO] {
    case req @ GET -> Root / "request" =>
      Ok(show"request-id: ${req.headers.get(headerKey).fold("None")(_.value)}")
  }

  private def requestIdFromBody(resp: Response[IO]) =
    resp.as[String].map(_.stripPrefix("request-id: "))

  private def requestIdFromHeaders(resp: Response[IO], headerKey: CIString = CIString("X-Request-ID")) =
    resp.headers.get(headerKey).fold("None")(_.value)

  "RequestId middleware" should {
    "propagate X-Request-ID header from request to response" in {
      val req = Request[IO](uri = uri("/request"), headers = Headers.of(Header("X-Request-ID", "123")))
      val (reqReqId, respReqId) = RequestId()(testService()).orNotFound(req)
        .flatMap { resp =>
          requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp))
        }.unsafeRunSync()

      (reqReqId must_=== "123") and (respReqId must_=== "123")
    }
    "generate X-Request-ID header when unset" in {
      val req = Request[IO](uri = uri("/request"))
      val (reqReqId, respReqId) = RequestId()(testService()).orNotFound(req)
        .flatMap { resp =>
          requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp))
        }.unsafeRunSync()

      (reqReqId must_=== respReqId) and (Either.catchNonFatal(ju.UUID.fromString(respReqId)) must(beRight))
    }
    "propagate custom request id header from request to response" in {
      val req = Request[IO](uri = uri("/request"), headers = Headers.of(Header("X-Request-ID", "123"), Header("X-Correlation-ID", "abc")))
      val (reqReqId, respReqId) = RequestId(CIString("X-Correlation-ID"))(testService(CIString("X-Correlation-ID"))).orNotFound(req)
        .flatMap { resp =>
          requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp, CIString("X-Correlation-ID")))
        }.unsafeRunSync()

      (reqReqId must_=== "abc") and (respReqId must_=== "abc")
    }
    "generate custom request id header when unset" in {
      val req = Request[IO](uri = uri("/request"), headers = Headers.of(Header("X-Request-ID", "123")))
      val (reqReqId, respReqId) = RequestId(CIString("X-Correlation-ID"))(testService(CIString("X-Correlation-ID"))).orNotFound(req)
        .flatMap { resp =>
          requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp, CIString("X-Correlation-ID")))
        }.unsafeRunSync()

      (reqReqId must_=== respReqId) and (Either.catchNonFatal(ju.UUID.fromString(respReqId)) must(beRight))
    }
  }
}
