/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package middleware

import cats.syntax.all._
import cats.effect._
import fs2.io.readInputStream
import org.http4s.dsl.io._
import org.http4s.Uri.uri
import scala.io.Source

/** Common Tests for Logger, RequestLogger, and ResponseLogger
  */
class LoggerSuite extends Http4sSuite {
  val testApp = HttpApp[IO] {
    case GET -> Root / "request" =>
      Ok("request response")
    case req @ POST -> Root / "post" =>
      Ok(req.body)
    case _ =>
      Ok()
  }

  def testResource = getClass.getResourceAsStream("/testresource.txt")

  def body: EntityBody[IO] =
    readInputStream[IO](IO.pure(testResource), 4096, testBlocker)

  val expectedBody: String = Source.fromInputStream(testResource).mkString

  val respApp = ResponseLogger.httpApp(logHeaders = true, logBody = true)(testApp)

  test("response should not affect a Get") {
    val req = Request[IO](uri = uri("/request"))
    respApp(req).map(_.status).assertEquals(Status.Ok)
  }

  test("response should not affect a Post") {
    val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
    respApp(req)
      .flatMap { res =>
        res
          .as[String]
          .map(
            _ === expectedBody && res.status === (Status.Ok)
          )
      }
      .assertEquals(true)
  }

  val reqApp = RequestLogger.httpApp(logHeaders = true, logBody = true)(testApp)

  test("request should not affect a Get") {
    val req = Request[IO](uri = uri("/request"))
    reqApp(req).map(_.status).assertEquals(Status.Ok)
  }

  test("request should not affect a Post") {
    val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
    reqApp(req)
      .flatMap { res =>
        res.as[String].map(_ === expectedBody && res.status === Status.Ok)
      }
      .assertEquals(true)
  }

  val loggerApp = Logger.httpApp(logHeaders = true, logBody = true)(testApp)

  test("logger should not affect a Get") {
    val req = Request[IO](uri = uri("/request"))
    loggerApp(req).map(_.status).assertEquals(Status.Ok)
  }

  test("logger should not affect a Post") {
    val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
    loggerApp(req)
      .flatMap { res =>
        res.as[String].map(_ === expectedBody && res.status === Status.Ok)
      }
      .assertEquals(true)
  }
}
