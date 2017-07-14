package org.http4s
package server
package middleware

import cats.effect._
import cats.implicits._
import org.http4s.dsl._

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

  val urlForm = UrlForm("foo" -> "bar")

  "ResponseLogger" should {
    val responseLoggerService = ResponseLogger(true, true)(testService)

    "not effect a Get" in {
      val req = Request[IO](uri = uri("/request"))
      responseLoggerService.orNotFound(req) must returnStatus(Status.Ok)
    }

    "not effect a Post" in {
      val req = Request[IO](uri = uri("/post"), method = POST).withBody(urlForm)
      val res = req.flatMap(responseLoggerService.orNotFound)
      res must returnStatus(Status.Ok)
      res must returnBody(urlForm)
    }
  }

  "RequestLogger" should {
    val requestLoggerService = RequestLogger(true, true)(testService)

    "not effect a Get" in {
      val req = Request[IO](uri = uri("/request"))
      requestLoggerService.orNotFound(req) must returnStatus(Status.Ok)
    }

    "not effect a Post" in {
      val req = Request[IO](uri = uri("/post"), method = POST).withBody(urlForm)
      val res = req.flatMap(requestLoggerService.orNotFound)
      res must returnStatus(Status.Ok)
      res must returnBody(urlForm)
    }
  }

  "Logger" should {
    val loggerService = Logger(true, true)(testService)

    "not effect a Get" in {
      val req = Request[IO](uri = uri("/request"))
      loggerService.orNotFound(req) must returnStatus(Status.Ok)
    }

    "not effect a Post" in {
      val req = Request[IO](uri = uri("/post"), method = POST).withBody(urlForm)
      val res = req.flatMap(loggerService.orNotFound)
      res must returnStatus(Status.Ok)
      res must returnBody(urlForm)
    }
  }


}
