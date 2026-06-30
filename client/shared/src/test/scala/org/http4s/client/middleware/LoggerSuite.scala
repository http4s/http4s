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
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.scalacheck.effect.PropF.forAllF

/** Common Tests for Logger, RequestLogger, and ResponseLogger
  */
class LoggerSuite extends Http4sSuite {
  private val testApp = HttpApp[IO] {
    case req @ POST -> Root / "post" =>
      Ok(req.body)
    case GET -> Root / "request" =>
      Ok("request response")
    case _ =>
      NotFound()
  }

  private def body: EntityBody[IO] = fs2.Stream.emits("This is a test resource.".getBytes())

  private val expectedBody: String = "This is a test resource."

  private val responseLoggerClient =
    ResponseLogger(logHeaders = true, logBody = true)(Client.fromHttpApp(testApp))

  test("ResponseLogger should not affect a Get") {
    val req = Request[IO](uri = uri"/request")
    responseLoggerClient.status(req).assertEquals(Status.Ok)
  }

  test("ResponseLogger should not affect a Post") {
    val req = Request[IO](uri = uri"/post", method = POST).withBodyStream(body)
    val res = responseLoggerClient.expect[String](req)
    res.assertEquals(expectedBody)
  }

  private val requestLoggerClient =
    RequestLogger.apply(logHeaders = true, logBody = true)(Client.fromHttpApp(testApp))

  test("RequestLogger should not affect a Get") {
    val req = Request[IO](uri = uri"/request")
    requestLoggerClient.status(req).assertEquals(Status.Ok)
  }

  test("RequestLogger should not affect a Post") {
    val req = Request[IO](uri = uri"/post", method = POST).withBodyStream(body)
    val res = requestLoggerClient.expect[String](req)
    res.assertEquals(expectedBody)
  }

  private def configurableRequestLoggerClient(
      logBody: Boolean,
      logAction: Option[String => IO[Unit]],
  ) = RequestLogger.apply[IO](logHeaders = true, logBody = logBody, logAction = logAction)(
    Client.fromHttpApp(testApp)
  )

  test("RequestLogger should log a Get for all values of logBody") {
    forAllF { (logBody: Boolean) =>
      val req = Request[IO](uri = uri"/request")
      val expectedMessageSubstring = "GET /request"
      def logAction(logger: Deferred[IO, String])(actualMessage: String) =
        logger
          .complete(actualMessage)
          .map(isFirstCompletion => assert(isFirstCompletion, "message was logged more than once"))

      for {
        logger <- IO.deferred[String]
        _ <- configurableRequestLoggerClient(logBody, Some(logAction(logger))).successful(req)
        actualMessage <- logger.tryGet
      } yield actualMessage.fold(fail("Nothing was logged"))(m =>
        assert(
          m.contains(expectedMessageSubstring),
          s"$m did not contain $expectedMessageSubstring",
        )
      )
    }
  }

  private val loggerApp =
    Logger(logHeaders = true, logBody = true)(Client.fromHttpApp(testApp)).toHttpApp

  test("Logger should not affect a Get") {
    val req = Request[IO](uri = uri"/request")
    loggerApp(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Logger should not affect a Post") {
    val req = Request[IO](uri = uri"/post", method = POST).withBodyStream(body)
    val res = loggerApp(req)
    res.map(_.status).assertEquals(Status.Ok)
    res.flatMap(_.as[String]).assertEquals(expectedBody)
  }
}
