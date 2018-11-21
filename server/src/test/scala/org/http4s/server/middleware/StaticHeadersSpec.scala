package org.http4s.server.middleware

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.Uri.uri

class StaticHeadersSpec extends Http4sSpec {

  val testService = HttpRoutes.of[IO] {
    case GET -> Root / "request" =>
      Ok("request response")
    case req @ POST -> Root / "post" =>
      Ok(req.body)
  }

  "NoCache middleware" should {
    "add a no-cache header to a response" in {
      val req = Request[IO](uri = uri("/request"))
      val resp = StaticHeaders.`no-cache`(testService).orNotFound(req)

      val check =
        resp.map(_.headers.toList.map(_.toString).contains("Cache-Control: no-cache")).unsafeRunSync
      check must_=== true
    }
  }

}
