package org.http4s
package dsl

import MockServer.MockResponse
import Status._

import org.specs2.mutable.Specification

import scalaz.concurrent.Task

object PathInHttpServiceSpec extends Specification {

  private implicit class responseToString(t: Task[MockResponse]) {
    def body = new String(t.run.body)
    def status = t.run.statusLine
  }

  object I extends IntParamMatcher("start")
  object L extends LongParamMatcher("limit")
  object P extends DoubleParamMatcher("percent")

  val service: HttpService = {
    case GET -> Root :? I(start) :? L(limit) =>
      Ok(s"start: $start, limit: $limit")
    case GET -> Root / LongVar(id) =>
      Ok(s"id: $id")
    case GET -> Root :? I(start) =>
      Ok(s"start: $start")
    case GET -> Root =>
      Ok("(empty)")
    case r =>
      NotFound("404 Not Found: " + r.pathInfo)
  }

  def server: MockServer = new MockServer(service)

  "Path DSL within HttpService" should {
    "successfully route GET /" in {
      val response = server(Request(GET, Uri(path = "/")))
      response.body must equalTo("(empty)")
      response.status must equalTo(Ok)
    }
    "successfully route GET /{id}" in {
      val response = server(Request(GET, Uri(path = "/12345")))
      response.body must equalTo("id: 12345")
      response.status must equalTo(Ok)
    }
    "successfully route GET /?{start}" in {
      val response = server(Request(GET, Uri.fromString("/?start=1").get))
      response.body must equalTo("start: 1")
      response.status must equalTo(Ok)
    }
    "successfully route GET /?{start,limit}" in {
      val response = server(Request(GET, Uri.fromString("/?start=1&limit=2").get))
      response.body must equalTo("start: 1, limit: 2")
      response.status must equalTo(Ok)
    }
  }

}