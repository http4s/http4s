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
package server
package middleware

import cats.effect._
import cats.syntax.all._
import fs2.io.readInputStream
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.http4s.testing.AutoCloseableResource

import scala.io.Source

/** Common Tests for Logger, RequestLogger, and ResponseLogger
  */
class LoggerSuite extends Http4sSuite {
  private val testApp = HttpApp[IO] {
    case GET -> Root / "request" =>
      Ok("request response")
    case req @ POST -> Root / "post" =>
      Ok(req.body)
    case _ =>
      Ok()
  }

  private def testResource = getClass.getResourceAsStream("/testresource.txt")

  private def body: EntityBody[IO] =
    readInputStream[IO](IO.pure(testResource), 4096)

  private val expectedBody: String =
    AutoCloseableResource.resource(Source.fromInputStream(testResource))(_.mkString)

  private val respApp = ResponseLogger.httpApp(logHeaders = true, logBody = true)(testApp)

  test("response should not affect a Get") {
    val req = Request[IO](uri = uri"/request")
    respApp(req).map(_.status).assertEquals(Status.Ok)
  }

  test("response should not affect a Post") {
    val req = Request[IO](uri = uri"/post", method = POST).withBodyStream(body)
    respApp(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ === expectedBody && res.status === (Status.Ok)
        )
    }.assert
  }

  private val reqApp = RequestLogger.httpApp(logHeaders = true, logBody = true)(testApp)

  test("request should not affect a Get") {
    val req = Request[IO](uri = uri"/request")
    reqApp(req).map(_.status).assertEquals(Status.Ok)
  }

  test("request should not affect a Post") {
    val req = Request[IO](uri = uri"/post", method = POST).withBodyStream(body)
    reqApp(req).flatMap { res =>
      res.as[String].map(_ === expectedBody && res.status === Status.Ok)
    }.assert
  }

  private val loggerApp = Logger.httpApp(logHeaders = true, logBody = true)(testApp)

  test("logger should not affect a Get") {
    val req = Request[IO](uri = uri"/request")
    loggerApp(req).map(_.status).assertEquals(Status.Ok)
  }

  test("logger should not affect a Post") {
    val req = Request[IO](uri = uri"/post", method = POST).withBodyStream(body)
    loggerApp(req).flatMap { res =>
      res.as[String].map(_ === expectedBody && res.status === Status.Ok)
    }.assert
  }
}
