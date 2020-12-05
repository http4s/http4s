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
