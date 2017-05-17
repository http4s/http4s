package org.http4s
package server
package middleware

import org.http4s.dsl._

/**
  * Common Tests for Logger, RequestLogger, and ResponseLogger
  */
class LoggerSpec extends Http4sSpec {

  val testService = HttpService {
    case req @ GET -> Root / "request" =>
      Ok("request response")
    case req @ POST -> Root / "post" =>
      Ok("post response")
  }

  val urlForm = UrlForm("foo" -> "bar")

  "ResponseLogger" should {
    val responseLoggerService = ResponseLogger(testService)

    "not effect a Get" in {
      val req = Request(uri = uri("/request"))
      responseLoggerService.orNotFound(req) must returnStatus(Status.Ok)
    }

    "not effect a Post" in {
      val req = Request(uri = uri("/post"), method = POST).withBody(urlForm)
      req.flatMap(responseLoggerService.orNotFound) must returnStatus(Status.Ok)
    }
  }

  "RequestLogger" should {
    val requestLoggerService = RequestLogger(testService)

    "not effect a Get" in {
      val req = Request(uri = uri("/request"))
      requestLoggerService.orNotFound(req) must returnStatus(Status.Ok)
    }

    "not effect a Post" in {
      val req = Request(uri = uri("/post"), method = POST).withBody(urlForm)
      req.flatMap(requestLoggerService.orNotFound) must returnStatus(Status.Ok)
    }
  }

  "Logger" should {
    val loggerService = Logger(testService)

    "not effect a Get" in {
      val req = Request(uri = uri("/request"))
      loggerService.orNotFound(req) must returnStatus(Status.Ok)
    }

    "not effect a Post" in {
      val req = Request(uri = uri("/post"), method = POST).withBody(urlForm)
      req.flatMap(loggerService.orNotFound) must returnStatus(Status.Ok)
    }
  }


}
