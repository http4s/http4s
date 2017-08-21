package org.http4s
package server
package middleware

import cats.effect._
import fs2.io.readInputStream
import org.http4s.dsl._

import scala.io.Source

/**
  * Common Tests for Logger, RequestLogger, and ResponseLogger
  */
class LoggerSpec extends Http4sSpec {

  val testService = HttpService[IO] {
    case req @ GET -> Root / "request" =>
      Ok("request response")
    case req @ POST -> Root / "post" =>
      Ok(req.body)
  }

  def testResource = getClass.getResourceAsStream("/testresource.txt")

  def body: EntityBody[IO] = readInputStream[IO](IO.pure(testResource), 4096)

  val expectedBody: String = Source.fromInputStream(testResource).mkString

  "ResponseLogger" should {
    val responseLoggerService = ResponseLogger(true, true)(testService)

    "not affect a Get" in {
      val req = Request[IO](uri = uri("/request"))
      responseLoggerService.orNotFound(req) must returnStatus(Status.Ok)
    }

    "not affect a Post" in {
      val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
      val res = responseLoggerService.orNotFound(req)
      res must returnStatus(Status.Ok)
      res must returnBody(expectedBody)
    }
  }

  "RequestLogger" should {
    val requestLoggerService = RequestLogger(true, true)(testService)

    "not affect a Get" in {
      val req = Request[IO](uri = uri("/request"))
      requestLoggerService.orNotFound(req) must returnStatus(Status.Ok)
    }

    "not affect a Post" in {
      val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
      val res = requestLoggerService.orNotFound(req)
      res must returnStatus(Status.Ok)
      res must returnBody(expectedBody)
    }
  }

  "Logger" should {
    val loggerService = Logger(true, true)(testService)

    "not affect a Get" in {
      val req = Request[IO](uri = uri("/request"))
      loggerService.orNotFound(req) must returnStatus(Status.Ok)
    }

    "not affect a Post" in {
      val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
      val res = loggerService.orNotFound(req)
      res must returnStatus(Status.Ok)
      res must returnBody(expectedBody)
    }
  }
}
