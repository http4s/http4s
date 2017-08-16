package org.http4s
package server
package middleware

import org.http4s.dsl._
import scala.io.Source
import fs2.Task
import fs2.io.readInputStream
import java.nio.charset.StandardCharsets

/**
  * Common Tests for Logger, RequestLogger, and ResponseLogger
  */
class LoggerSpec extends Http4sSpec {

  val testService = HttpService {
    case req @ GET -> Root / "request" =>
      Ok("request response")
    case req @ POST -> Root / "post" =>
      Ok(req.body)
  }

  def testResource = getClass.getResourceAsStream("/testresource.txt")

  def body: EntityBody =
    readInputStream[Task](Task.now(testResource), 4096)

  val expectedBody: String =
    Source.fromInputStream(testResource).mkString
  
  "ResponseLogger" should {
    val responseLoggerService = ResponseLogger(true, true)(testService)

    "not effect a Get" in {
      val req = Request(uri = uri("/request"))
      responseLoggerService.orNotFound(req) must returnStatus(Status.Ok)
    }

    "not effect a Post" in {
      val req = Request(uri = uri("/post"), method = POST).withBodyStream(body)
      val res = responseLoggerService.orNotFound(req)
      res must returnStatus(Status.Ok)
      res must returnBody(expectedBody)
    }
  }

  "RequestLogger" should {
    val requestLoggerService = RequestLogger(true, true)(testService)

    "not effect a Get" in {
      val req = Request(uri = uri("/request"))
      requestLoggerService.orNotFound(req) must returnStatus(Status.Ok)
    }

    "not effect a Post" in {
      val req = Request(uri = uri("/post"), method = POST).withBodyStream(body)
      val res = requestLoggerService.orNotFound(req)
      res must returnStatus(Status.Ok)
      res must returnBody(expectedBody)
    }
  }

  "Logger" should {
    val loggerService = Logger(true, true)(testService)

    "not effect a Get" in {
      val req = Request(uri = uri("/request"))
      loggerService.orNotFound(req) must returnStatus(Status.Ok)
    }

    "not effect a Post" in {
      val req = Request(uri = uri("/post"), method = POST).withBodyStream(body)
      val res = loggerService.orNotFound(req)
      res must returnStatus(Status.Ok)
      res must returnBody(expectedBody)
    }
  }
}
