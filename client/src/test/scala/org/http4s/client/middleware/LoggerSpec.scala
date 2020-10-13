/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package middleware

import cats.effect._
import fs2.io.readInputStream
import org.http4s.Uri.uri
import org.http4s.dsl.io._
import org.http4s.testing.Http4sLegacyMatchersIO
import scala.io.Source

/** Common Tests for Logger, RequestLogger, and ResponseLogger
  */
class LoggerSpec extends Http4sSpec with Http4sLegacyMatchersIO {
  val testApp = HttpApp[IO] {
    case req @ POST -> Root / "post" =>
      Ok(req.body)
    case GET -> Root / "request" =>
      Ok("request response")
    case _ =>
      NotFound()
  }

  def testResource = getClass.getResourceAsStream("/testresource.txt")

  def body: EntityBody[IO] =
    readInputStream[IO](IO.pure(testResource), 4096, testBlocker)

  val expectedBody: String = Source.fromInputStream(testResource).mkString

  "ResponseLogger" should {
    val responseLoggerClient =
      ResponseLogger(true, true)(Client.fromHttpApp(testApp))

    "not affect a Get" in {
      val req = Request[IO](uri = uri("/request"))
      responseLoggerClient.status(req).unsafeRunSync() must_== Status.Ok
    }

    "not affect a Post" in {
      val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
      val res = responseLoggerClient.expect[String](req)
      res.unsafeRunSync() must_== expectedBody
    }
  }

  "RequestLogger" should {
    val requestLoggerClient = RequestLogger.apply(true, true)(Client.fromHttpApp(testApp))

    "not affect a Get" in {
      val req = Request[IO](uri = uri("/request"))
      requestLoggerClient.status(req).unsafeRunSync() must_== Status.Ok
    }

    "not affect a Post" in {
      val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
      val res = requestLoggerClient.expect[String](req)
      res.unsafeRunSync() must_== expectedBody
    }
  }

  "Logger" should {
    val loggerApp =
      Logger(true, true)(Client.fromHttpApp(testApp)).toHttpApp

    "not affect a Get" in {
      val req = Request[IO](uri = uri("/request"))
      loggerApp(req) must returnStatus(Status.Ok)
    }

    "not affect a Post" in {
      val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
      val res = loggerApp(req)
      res must returnStatus(Status.Ok)
      res must returnBody(expectedBody)
    }
  }
}
