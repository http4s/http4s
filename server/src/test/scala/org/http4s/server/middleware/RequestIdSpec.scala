/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.Uri.uri
import org.typelevel.ci.CIString
import java.util.UUID

class RequestIdSpec extends Http4sSpec {
  private def testService(headerKey: CIString = CIString("X-Request-ID")) =
    HttpRoutes.of[IO] {
      case req @ GET -> Root / "request" =>
        Ok(show"request-id: ${req.headers.get(headerKey).fold("None")(_.value)}")
      case req @ GET -> Root / "attribute" =>
        Ok(
          show"request-id: ${req.attributes.lookup(RequestId.requestIdAttrKey).getOrElse[String]("None")}")
    }

  private def requestIdFromBody(resp: Response[IO]) =
    resp.as[String].map(_.stripPrefix("request-id: "))

  private def requestIdFromHeaders(
      resp: Response[IO],
      headerKey: CIString = CIString("X-Request-ID")) =
    resp.headers.get(headerKey).fold("None")(_.value)

  "RequestId middleware" should {
    "propagate X-Request-ID header from request to response" in {
      val req =
        Request[IO](uri = uri("/request"), headers = Headers.of(Header("X-Request-ID", "123")))
      val (reqReqId, respReqId) = RequestId
        .httpRoutes(testService())
        .orNotFound(req)
        .flatMap { resp =>
          requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp))
        }
        .unsafeRunSync()

      (reqReqId must_=== "123").and(respReqId must_=== "123")
    }
    "generate X-Request-ID header when unset" in {
      val req = Request[IO](uri = uri("/request"))
      val (reqReqId, respReqId) = RequestId
        .httpRoutes(testService())
        .orNotFound(req)
        .flatMap { resp =>
          requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp))
        }
        .unsafeRunSync()

      (reqReqId must_=== respReqId).and(
        Either.catchNonFatal(UUID.fromString(respReqId)) must (beRight))
    }
    "generate different request ids on subsequent requests" in {
      val req = Request[IO](uri = uri("/request"))
      val resp = RequestId.httpRoutes(testService()).orNotFound(req)
      val requestId1 = resp.map(requestIdFromHeaders(_)).unsafeRunSync()
      val requestId2 = resp.map(requestIdFromHeaders(_)).unsafeRunSync()

      (requestId1 must_!== requestId2)
    }
    "propagate custom request id header from request to response" in {
      val req = Request[IO](
        uri = uri("/request"),
        headers = Headers.of(Header("X-Request-ID", "123"), Header("X-Correlation-ID", "abc")))
      val (reqReqId, respReqId) = RequestId
        .httpRoutes(CIString("X-Correlation-ID"))(testService(CIString("X-Correlation-ID")))
        .orNotFound(req)
        .flatMap { resp =>
          requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp, CIString("X-Correlation-ID")))
        }
        .unsafeRunSync()

      (reqReqId must_=== "abc").and(respReqId must_=== "abc")
    }
    "generate custom request id header when unset" in {
      val req =
        Request[IO](uri = uri("/request"), headers = Headers.of(Header("X-Request-ID", "123")))
      val (reqReqId, respReqId) = RequestId
        .httpRoutes(CIString("X-Correlation-ID"))(testService(CIString("X-Correlation-ID")))
        .orNotFound(req)
        .flatMap { resp =>
          requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp, CIString("X-Correlation-ID")))
        }
        .unsafeRunSync()

      (reqReqId must_=== respReqId).and(
        Either.catchNonFatal(UUID.fromString(respReqId)) must (beRight))
    }
    "generate X-Request-ID header when unset using supplied generator" in {
      val uuid = UUID.fromString("00000000-0000-0000-0000-000000000000")
      val req = Request[IO](uri = uri("/request"))
      val (reqReqId, respReqId) = RequestId
        .httpRoutes(genReqId = IO.pure(uuid))(testService())
        .orNotFound(req)
        .flatMap { resp =>
          requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp))
        }
        .unsafeRunSync()

      (reqReqId must_=== uuid.show).and(respReqId must_=== uuid.show)
    }
    "include requestId attribute with request and response" in {
      val req =
        Request[IO](uri = uri("/attribute"), headers = Headers.of(Header("X-Request-ID", "123")))
      val (reqReqId, respReqId) = RequestId
        .httpRoutes(testService())
        .orNotFound(req)
        .flatMap { resp =>
          requestIdFromBody(resp).map(
            _ -> resp.attributes.lookup(RequestId.requestIdAttrKey).getOrElse("None"))
        }
        .unsafeRunSync()

      (reqReqId must_=== "123").and(respReqId must_=== "123")
    }
  }
}
