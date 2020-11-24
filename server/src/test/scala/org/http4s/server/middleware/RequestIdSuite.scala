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
import org.http4s.syntax.all._
import org.http4s.Uri.uri
import org.http4s.util.{CaseInsensitiveString => CIString}
import java.util.UUID

class RequestIdSuite extends Http4sSuite {
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

  test("propagate X-Request-ID header from request to response") {
    val req =
      Request[IO](uri = uri("/request"), headers = Headers.of(Header("X-Request-ID", "123")))
    RequestId
      .httpRoutes(testService())
      .orNotFound(req)
      .flatMap { resp =>
        requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp))
      }
      .map { case (req, resp) => req === "123" && resp === "123" }
      .assertEquals(true)
  }

  test("generate X-Request-ID header when unset") {
    val req = Request[IO](uri = uri("/request"))
    RequestId
      .httpRoutes(testService())
      .orNotFound(req)
      .flatMap { resp =>
        requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp))
      }
      .map { case (reqReqId, respReqId) =>
        reqReqId === respReqId && Either.catchNonFatal(UUID.fromString(respReqId)).isRight
      }
      .assertEquals(true)
  }

  test("generate different request ids on subsequent requests") {
    val req = Request[IO](uri = uri("/request"))
    val resp = RequestId.httpRoutes(testService()).orNotFound(req)
    (resp.map(requestIdFromHeaders(_)), resp.map(requestIdFromHeaders(_)))
      .parMapN(_ =!= _)
      .assertEquals(true)
  }

  test("propagate custom request id header from request to response") {
    val req = Request[IO](
      uri = uri("/request"),
      headers = Headers.of(Header("X-Request-ID", "123"), Header("X-Correlation-ID", "abc")))
    RequestId
      .httpRoutes(CIString("X-Correlation-ID"))(testService(CIString("X-Correlation-ID")))
      .orNotFound(req)
      .flatMap { resp =>
        requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp, CIString("X-Correlation-ID")))
      }
      .map { case (reqReqId, respReqId) =>
        reqReqId === "abc" && respReqId === "abc"
      }
      .assertEquals(true)
  }

  test("generate custom request id header when unset") {
    val req =
      Request[IO](uri = uri("/request"), headers = Headers.of(Header("X-Request-ID", "123")))
    RequestId
      .httpRoutes(CIString("X-Correlation-ID"))(testService(CIString("X-Correlation-ID")))
      .orNotFound(req)
      .flatMap { resp =>
        requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp, CIString("X-Correlation-ID")))
      }
      .map { case (reqReqId, respReqId) =>
        reqReqId === respReqId && Either.catchNonFatal(UUID.fromString(respReqId)).isRight
      }
      .assertEquals(true)
  }

  test("generate X-Request-ID header when unset using supplied generator") {
    val uuid = UUID.fromString("00000000-0000-0000-0000-000000000000")
    val req = Request[IO](uri = uri("/request"))
    RequestId
      .httpRoutes(genReqId = IO.pure(uuid))(testService())
      .orNotFound(req)
      .flatMap { resp =>
        requestIdFromBody(resp).map(_ -> requestIdFromHeaders(resp))
      }
      .map { case (reqReqId, respReqId) =>
        reqReqId === uuid.show && respReqId === uuid.show
      }
      .assertEquals(true)
  }

  test("include requestId attribute with request and response") {
    val req =
      Request[IO](uri = uri("/attribute"), headers = Headers.of(Header("X-Request-ID", "123")))
    RequestId
      .httpRoutes(testService())
      .orNotFound(req)
      .flatMap { resp =>
        requestIdFromBody(resp).map(
          _ -> resp.attributes.lookup(RequestId.requestIdAttrKey).getOrElse("None"))
      }
      .map { case (reqReqId, respReqId) =>
        reqReqId === "123" && respReqId === "123"
      }
      .assertEquals(true)
  }
}
