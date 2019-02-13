package org.http4s
package server
package middleware

import cats.effect._
import fs2.io.readInputStream
import org.http4s.dsl.io._
import org.http4s.Uri.uri
import scala.io.Source

/**
  * Common Tests for Logger, RequestLogger, and ResponseLogger
  */
class LoggerSpec extends Http4sSpec {

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
    readInputStream[IO](IO.pure(testResource), 4096, testBlockingExecutionContext)

  val expectedBody: String = Source.fromInputStream(testResource).mkString

  "ResponseLogger" should {
    val app = ResponseLogger.httpApp(logHeaders = true, logBody = true)(testApp)

    "not affect a Get" in {
      val req = Request[IO](uri = uri("/request"))
      app(req) must returnStatus(Status.Ok)
    }

    "not affect a Post" in {
      val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
      val res = app(req)
      res must returnStatus(Status.Ok)
      res must returnBody(expectedBody)
    }
  }

  "RequestLogger" should {
    val app = RequestLogger.httpApp(logHeaders = true, logBody = true)(testApp)

    "not affect a Get" in {
      val req = Request[IO](uri = uri("/request"))
      app(req) must returnStatus(Status.Ok)
    }

    "not affect a Post" in {
      val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
      val res = app(req)
      res must returnStatus(Status.Ok)
      res must returnBody(expectedBody)
    }
  }

  "Logger" should {
    val app = Logger.httpApp(logHeaders = true, logBody = true)(testApp)

    "not affect a Get" in {
      val req = Request[IO](uri = uri("/request"))
      app(req) must returnStatus(Status.Ok)
    }

    "not affect a Post" in {
      val req = Request[IO](uri = uri("/post"), method = POST).withBodyStream(body)
      val res = app(req)
      res must returnStatus(Status.Ok)
      res must returnBody(expectedBody)
    }
  }
}
