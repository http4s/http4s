package org.http4s
package server
package middleware

import cats.data.OptionT
import cats.effect._
import fs2.io.readInputStream
import org.http4s.dsl.io._

import scala.io.Source

/**
  * Common Tests for Logger, RequestLogger, and ResponseLogger
  */
class LoggerSpec extends Http4sSpec {

  val testRoutes = HttpRoutes.of[IO] {
    case GET -> Root / "request" =>
      Ok("request response")
    case req @ POST -> Root / "post" =>
      Ok(req.body)
  }

  def testResource = getClass.getResourceAsStream("/testresource.txt")

  def body: EntityBody[IO] = readInputStream[IO](IO.pure(testResource), 4096)

  val expectedBody: String = Source.fromInputStream(testResource).mkString

  "ResponseLogger" should {
    val app = ResponseLogger(OptionT.liftK[IO])(true, true)(testRoutes).orNotFound

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
    val app = RequestLogger(OptionT.liftK[IO])(true, true)(testRoutes).orNotFound

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
    val app = Logger(OptionT.liftK[IO])(true, true)(testRoutes).orNotFound

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
