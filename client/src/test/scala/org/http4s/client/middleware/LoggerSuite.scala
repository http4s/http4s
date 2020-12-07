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
import scala.io.Source

/** Common Tests for Logger, RequestLogger, and ResponseLogger
  */
class LoggerSuite extends Http4sSuite {
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

  val responseLoggerClient =
    ResponseLogger(true, true)(Client.fromHttpApp(testApp))

  test("ResponseLogger should not affect a Get") {
    val req = Request[IO](uri = uri("/request"))
    responseLoggerClient.status(req).assertEquals(Status.Ok)
  }

  test("ResponseLogger should not affect a Post") {
    val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
    val res = responseLoggerClient.expect[String](req)
    res.assertEquals(expectedBody)
  }

  val requestLoggerClient = RequestLogger.apply(true, true)(Client.fromHttpApp(testApp))

  test("RequestLogger should not affect a Get") {
    val req = Request[IO](uri = uri("/request"))
    requestLoggerClient.status(req).assertEquals(Status.Ok)
  }

  test("RequestLogger should not affect a Post") {
    val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
    val res = requestLoggerClient.expect[String](req)
    res.assertEquals(expectedBody)
  }

  val loggerApp =
    Logger(true, true)(Client.fromHttpApp(testApp)).toHttpApp

  test("Logger should not affect a Get") {
    val req = Request[IO](uri = uri("/request"))
    loggerApp(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Logger should not affect a Post") {
    val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
    val res = loggerApp(req)
    res.map(_.status).assertEquals(Status.Ok)
    res.flatMap(_.as[String]).assertEquals(expectedBody)
  }
}
