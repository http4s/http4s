package org.http4s
package dsl

import org.http4s.server.MockServer
import server.MockServer.MockResponse

import scalaz.{ Failure, Success }
import scalaz.concurrent.Task

object PathInHttpServiceSpec extends Http4sSpec {

  private implicit class responseToString(t: Task[MockResponse]) {
    def body = new String(t.run.body)
    def status = t.run.statusLine
  }

  object List {
    def unapplySeq(params: Map[String, Seq[String]]) = params.get("list")
    def unapply(params: Map[String, Seq[String]]) = unapplySeq(params)
  }

  object I extends QueryParamDecoderMatcher[Int]("start")
  object P extends QueryParamDecoderMatcher[Double]("decimal")
  object T extends QueryParamDecoderMatcher[String]("term")

  case class Limit(l: Long)
  implicit val limitQueryParam = QueryParam.fromKey[Limit]("limit")
  implicit val limitDecoder    = QueryParamDecoder.decodeBy[Limit, Long](Limit.apply)

  object L extends QueryParamMatcher[Limit]

  object OptCounter extends OptionalQueryParamDecoderMatcher[Int]("counter")

  object ValidatingCounter extends ValidatingQueryParamDecoderMatcher[Int]("counter")

  object OptValidatingCounter extends OptionalValidatingQueryParamDecoderMatcher[Int]("counter")

  val service = HttpService {
    case GET -> Root :? I(start) +& L(limit) =>
      Ok(s"start: $start, limit: ${limit.l}")
    case GET -> Root / LongVar(id) =>
      Ok(s"id: $id")
    case GET -> Root :? I(start) =>
      Ok(s"start: $start")
    case GET -> Root =>
      Ok("(empty)")
    case GET -> Root / "calc" :? P(d) =>
      Ok(s"result: ${d / 2}")
    case GET -> Root / "items" :? List(list) =>
      Ok(s"items: ${list.mkString(",")}")
    case GET -> Root / "search" :? T(search) =>
      Ok(s"term: $search")
    case GET -> Root / "mix" :? T(t) +& List(l) +& P(d) +& I(s) +& L(m) =>
      Ok(s"list: ${l.mkString(",")}, start: $s, limit: ${m.l}, term: $t, decimal=$d")
    case GET -> Root / "app":? OptCounter(c) =>
      Ok(s"counter: $c")
    case GET -> Root / "valid" :? ValidatingCounter(c) =>
      c.fold(
        errors => BadRequest(errors.list.map(_.sanitized).mkString(",")),
        vc => Ok(s"counter: $vc")
      )
    case GET -> Root / "optvalid" :? OptValidatingCounter(c) =>
      c match {
        case Some(Failure(errors)) => BadRequest(errors.list.map(_.sanitized).mkString(","))
        case Some(Success(cv)) => Ok(s"counter: $cv")
        case None => Ok("no counter")
      }
    case r =>
      NotFound("404 Not Found: " + r.pathInfo)
  }

  def server: MockServer = new MockServer(service)

  "Path DSL within HttpService" should {
    "GET /" in {
      val response = server(Request(GET, Uri(path = "/")))
      response.status must_== (Ok)
      response.body must equalTo("(empty)")
    }
    "GET /{id}" in {
      val response = server(Request(GET, Uri(path = "/12345")))
      response.status must_== (Ok)
      response.body must equalTo("id: 12345")
    }
    "GET /?{start}" in {
      val response = server(Request(GET, uri("/?start=1")))
      response.status must_== (Ok)
      response.body must equalTo("start: 1")
    }
    "GET /?{start,limit}" in {
      val response = server(Request(GET, uri("/?start=1&limit=2")))
      response.status must_== (Ok)
      response.body must equalTo("start: 1, limit: 2")
    }
    "GET /calc" in {
      val response = server(Request(GET, Uri(path = "/calc")))
      response.status must_== (NotFound)
      response.body must equalTo("404 Not Found: /calc")
    }
    "GET /calc?decimal=1.3" in {
      val response = server(Request(GET, Uri(path = "/calc", query = Query.fromString("decimal=1.3"))))
      response.status must_== (Ok)
      response.body must equalTo(s"result: 0.65")
    }
    "GET /items?list=1&list=2&list=3&list=4&list=5" in {
      val response = server(Request(GET, Uri(path = "/items", query = Query.fromString("list=1&list=2&list=3&list=4&list=5"))))
      response.status must_== (Ok)
      response.body must equalTo(s"items: 1,2,3,4,5")
    }
    "GET /search" in {
      val response = server(Request(GET, Uri(path = "/search")))
      response.status must_== (NotFound)
      response.body must equalTo("404 Not Found: /search")
    }
    "GET /search?term" in {
      val response = server(Request(GET, Uri(path = "/search", query = Query.fromString("term"))))
      response.status must_== (NotFound)
      response.body must equalTo("404 Not Found: /search")
    }
    "GET /search?term=" in {
      val response = server(Request(GET, Uri(path = "/search", query = Query.fromString("term="))))
      response.status must_== (Ok)
      response.body must equalTo("term: ")
    }
    "GET /search?term= http4s  " in {
      val response = server(Request(GET, Uri(path = "/search", query = Query.fromString("term=%20http4s%20%20"))))
      response.status must_== (Ok)
      response.body must equalTo("term:  http4s  ")
    }
    "GET /search?term=http4s" in {
      val response = server(Request(GET, Uri(path = "/search", query = Query.fromString("term=http4s"))))
      response.status must_== (Ok)
      response.body must equalTo("term: http4s")
    }
    "optional parameter present" in {
      val response = server(Request(GET, Uri(path = "/app", query = Query.fromString("counter=3"))))
      response.status must_== (Ok)
      response.body must equalTo("counter: Some(3)")
    }
    "optional parameter absent" in {
      val response = server(Request(GET, Uri(path = "/app", query = Query.fromString("other=john"))))
      response.status must_== (Ok)
      response.body must equalTo("counter: None")
    }
    "optional parameter present with incorrect format" in {
      val response = server(Request(GET, Uri(path = "/app", query = Query.fromString("counter=john"))))
      response.status must_== (NotFound)
    }
    "validating parameter present" in {
      val response = server(Request(GET, Uri(path = "/valid", query = Query.fromString("counter=3"))))
      response.status must_== (Ok)
      response.body must equalTo("counter: 3")
    }
    "validating parameter absent" in {
      val response = server(Request(GET, Uri(path = "/valid", query = Query.fromString("notthis=3"))))
      response.status must_== (NotFound)
    }
    "validating parameter present with incorrect format" in {
      val response = server(Request(GET, Uri(path = "/valid", query = Query.fromString("counter=foo"))))
      response.status must_== (BadRequest)
      response.body must equalTo("Query decoding Int failed")
    }
    "optional validating parameter present" in {
      val response = server(Request(GET, Uri(path = "/optvalid", query = Query.fromString("counter=3"))))
      response.status must_== (Ok)
      response.body must equalTo("counter: 3")
    }
    "optional validating parameter absent" in {
      val response = server(Request(GET, Uri(path = "/optvalid", query = Query.fromString("notthis=3"))))
      response.status must_== (Ok)
      response.body must equalTo("no counter")
    }
    "optional validating parameter present with incorrect format" in {
      val response = server(Request(GET, Uri(path = "/optvalid", query = Query.fromString("counter=foo"))))
      response.status must_== (BadRequest)
      response.body must equalTo("Query decoding Int failed")
    }

  }

}
