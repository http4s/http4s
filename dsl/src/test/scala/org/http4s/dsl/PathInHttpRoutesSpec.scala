package org.http4s
package dsl

import cats.data.Validated._
import cats.effect.IO
import cats.instances.string._
import cats.syntax.foldable._
import org.http4s.dsl.io._

object PathInHttpRoutesSpec extends Http4sSpec {

  object List {
    def unapplySeq(params: Map[String, Seq[String]]) = params.get("list")
    def unapply(params: Map[String, Seq[String]]) = unapplySeq(params)
  }

  object I extends QueryParamDecoderMatcher[Int]("start")
  object P extends QueryParamDecoderMatcher[Double]("decimal")
  object T extends QueryParamDecoderMatcher[String]("term")

  final case class Limit(l: Long)
  implicit val limitQueryParam = QueryParam.fromKey[Limit]("limit")
  implicit val limitDecoder = QueryParamDecoder[Long].map(Limit.apply)

  object L extends QueryParamMatcher[Limit]

  object OptCounter extends OptionalQueryParamDecoderMatcher[Int]("counter")

  object ValidatingCounter extends ValidatingQueryParamDecoderMatcher[Int]("counter")

  object OptValidatingCounter extends OptionalValidatingQueryParamDecoderMatcher[Int]("counter")

  object MultiOptCounter extends OptionalMultiQueryParamDecoderMatcher[Int]("counter")

  val app: HttpApp[IO] = HttpApp {
    case GET -> Root :? I(start) +& L(limit) =>
      Ok[IO](s"start: $start, limit: ${limit.l}")
    case GET -> Root / LongVar(id) =>
      Ok[IO](s"id: $id")
    case GET -> Root :? I(start) =>
      Ok[IO](s"start: $start")
    case GET -> Root =>
      Ok[IO]("(empty)")
    case GET -> Root / "calc" :? P(d) =>
      Ok[IO](s"result: ${d / 2}")
    case GET -> Root / "items" :? List(list) =>
      Ok[IO](s"items: ${list.mkString(",")}")
    case GET -> Root / "search" :? T(search) =>
      Ok[IO](s"term: $search")
    case GET -> Root / "mix" :? T(t) +& List(l) +& P(d) +& I(s) +& L(m) =>
      Ok[IO](s"list: ${l.mkString(",")}, start: $s, limit: ${m.l}, term: $t, decimal=$d")
    case GET -> Root / "app" :? OptCounter(c) =>
      Ok[IO](s"counter: $c")
    case GET -> Root / "valid" :? ValidatingCounter(c) =>
      c.fold(
        errors => BadRequest[IO](errors.map(_.sanitized).mkString_("", ",", "")),
        vc => Ok[IO](s"counter: $vc")
      )
    case GET -> Root / "optvalid" :? OptValidatingCounter(c) =>
      c match {
        case Some(Invalid(errors)) => BadRequest[IO](errors.map(_.sanitized).mkString_("", ",", ""))
        case Some(Valid(cv)) => Ok[IO](s"counter: $cv")
        case None => Ok[IO]("no counter")
      }
    case GET -> Root / "multiopt" :? MultiOptCounter(counters) =>
      counters match {
        case Valid(cs @ (_ :: _)) => Ok[IO](s"${cs.length}: ${cs.mkString(",")}")
        case Valid(Nil) => Ok[IO]("absent")
        case Invalid(_) => BadRequest[IO]()
      }
    case r =>
      NotFound[IO](s"404 Not Found: ${r.pathInfo}")
  }

  def serve(req: Request[IO]): Response[IO] =
    app(req).unsafeRunSync

  "Path DSL within HttpService" should {
    "GET /" in {
      val response = serve(Request(GET, Uri(path = "/")))
      response.status must_== (Ok)
      response.as[String] must returnValue("(empty)")
    }
    "GET /{id}" in {
      val response = serve(Request(GET, Uri(path = "/12345")))
      response.status must_== (Ok)
      response.as[String] must returnValue("id: 12345")
    }
    "GET /?{start}" in {
      val response = serve(Request(GET, uri("/?start=1")))
      response.status must_== (Ok)
      response.as[String] must returnValue("start: 1")
    }
    "GET /?{start,limit}" in {
      val response = serve(Request(GET, uri("/?start=1&limit=2")))
      response.status must_== (Ok)
      response.as[String] must returnValue("start: 1, limit: 2")
    }
    "GET /calc" in {
      val response = serve(Request(GET, Uri(path = "/calc")))
      response.status must_== (NotFound)
      response.as[String] must returnValue("404 Not Found: /calc")
    }
    "GET /calc?decimal=1.3" in {
      val response =
        serve(Request(GET, Uri(path = "/calc", query = Query.fromString("decimal=1.3"))))
      response.status must_== (Ok)
      response.as[String] must returnValue(s"result: 0.65")
    }
    "GET /items?list=1&list=2&list=3&list=4&list=5" in {
      val response = serve(
        Request(
          GET,
          Uri(path = "/items", query = Query.fromString("list=1&list=2&list=3&list=4&list=5"))))
      response.status must_== (Ok)
      response.as[String] must returnValue(s"items: 1,2,3,4,5")
    }
    "GET /search" in {
      val response = serve(Request(GET, Uri(path = "/search")))
      response.status must_== (NotFound)
      response.as[String] must returnValue("404 Not Found: /search")
    }
    "GET /search?term" in {
      val response = serve(Request(GET, Uri(path = "/search", query = Query.fromString("term"))))
      response.status must_== (NotFound)
      response.as[String] must returnValue("404 Not Found: /search")
    }
    "GET /search?term=" in {
      val response = serve(Request(GET, Uri(path = "/search", query = Query.fromString("term="))))
      response.status must_== (Ok)
      response.as[String] must returnValue("term: ")
    }
    "GET /search?term= http4s  " in {
      val response =
        serve(Request(GET, Uri(path = "/search", query = Query.fromString("term=%20http4s%20%20"))))
      response.status must_== (Ok)
      response.as[String] must returnValue("term:  http4s  ")
    }
    "GET /search?term=http4s" in {
      val response =
        serve(Request(GET, Uri(path = "/search", query = Query.fromString("term=http4s"))))
      response.status must_== (Ok)
      response.as[String] must returnValue("term: http4s")
    }
    "optional parameter present" in {
      val response = serve(Request(GET, Uri(path = "/app", query = Query.fromString("counter=3"))))
      response.status must_== (Ok)
      response.as[String] must returnValue("counter: Some(3)")
    }
    "optional parameter absent" in {
      val response = serve(Request(GET, Uri(path = "/app", query = Query.fromString("other=john"))))
      response.status must_== (Ok)
      response.as[String] must returnValue("counter: None")
    }
    "optional parameter present with incorrect format" in {
      val response =
        serve(Request(GET, Uri(path = "/app", query = Query.fromString("counter=john"))))
      response.status must_== (NotFound)
    }
    "validating parameter present" in {
      val response =
        serve(Request(GET, Uri(path = "/valid", query = Query.fromString("counter=3"))))
      response.status must_== (Ok)
      response.as[String] must returnValue("counter: 3")
    }
    "validating parameter absent" in {
      val response =
        serve(Request(GET, Uri(path = "/valid", query = Query.fromString("notthis=3"))))
      response.status must_== (NotFound)
    }
    "validating parameter present with incorrect format" in {
      val response =
        serve(Request(GET, Uri(path = "/valid", query = Query.fromString("counter=foo"))))
      response.status must_== (BadRequest)
      response.as[String] must returnValue("Query decoding Int failed")
    }
    "optional validating parameter present" in {
      val response =
        serve(Request(GET, Uri(path = "/optvalid", query = Query.fromString("counter=3"))))
      response.status must_== (Ok)
      response.as[String] must returnValue("counter: 3")
    }
    "optional validating parameter absent" in {
      val response =
        serve(Request(GET, Uri(path = "/optvalid", query = Query.fromString("notthis=3"))))
      response.status must_== (Ok)
      response.as[String] must returnValue("no counter")
    }
    "optional validating parameter present with incorrect format" in {
      val response =
        serve(Request(GET, Uri(path = "/optvalid", query = Query.fromString("counter=foo"))))
      response.status must_== (BadRequest)
      response.as[String] must returnValue("Query decoding Int failed")
    }
    "optional multi parameter with no parameters" in {
      val response = serve(Request(GET, Uri(path = "/multiopt")))
      response.status must_== (Ok)
      response.as[String] must returnValue("absent")
    }
    "optional multi parameter with multiple parameters" in {
      val response = serve(
        Request(
          GET,
          Uri(path = "/multiopt", query = Query.fromString("counter=1&counter=2&counter=3"))))
      response.status must_== (Ok)
      response.as[String] must returnValue("3: 1,2,3")
    }
    "optional multi parameter with one parameter" in {
      val response =
        serve(Request(GET, Uri(path = "/multiopt", query = Query.fromString("counter=3"))))
      response.status must_== (Ok)
      response.as[String] must returnValue("1: 3")
    }
    "optional multi parameter with incorrect format" in {
      val response =
        serve(Request(GET, Uri(path = "/multiopt", query = Query.fromString("counter=foo"))))
      response.status must_== (BadRequest)
    }
    "optional multi parameter with one incorrect parameter" in {
      val response = serve(
        Request(GET, Uri(path = "/multiopt", query = Query.fromString("counter=foo&counter=1"))))
      response.status must_== (BadRequest)

      val response2 = serve(
        Request(GET, Uri(path = "/multiopt", query = Query.fromString("counter=1&counter=foo"))))
      response2.status must_== (BadRequest)
    }

  }

}
