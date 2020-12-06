/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package dsl

import cats.data.Validated._
import cats.effect.IO
import cats.syntax.all._
import org.http4s.Uri.uri
import org.http4s.dsl.io._

final case class Limit(l: Long)

class PathInHttpRoutesSuite extends Http4sSuite {
  object List {
    def unapplySeq(params: Map[String, collection.Seq[String]]) = params.get("list")
    def unapply(params: Map[String, collection.Seq[String]]) = unapplySeq(params)
  }

  object I extends QueryParamDecoderMatcher[Int]("start")
  object P extends QueryParamDecoderMatcher[Double]("decimal")
  object T extends QueryParamDecoderMatcher[String]("term")

  implicit val limitQueryParam = QueryParam.fromKey[Limit]("limit")
  implicit val limitDecoder = QueryParamDecoder[Long].map(Limit.apply)

  object L extends QueryParamMatcher[Limit]

  object OptCounter extends OptionalQueryParamDecoderMatcher[Int]("counter")

  object ValidatingCounter extends ValidatingQueryParamDecoderMatcher[Int]("counter")

  object OptValidatingCounter extends OptionalValidatingQueryParamDecoderMatcher[Int]("counter")

  object MultiOptCounter extends OptionalMultiQueryParamDecoderMatcher[Int]("counter")

  object Flag extends FlagQueryParamMatcher("flag")

  val app: HttpApp[IO] = HttpApp {
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
    case GET -> Root / "app" :? OptCounter(c) =>
      Ok(s"counter: $c")
    case GET -> Root / "valid" :? ValidatingCounter(c) =>
      c.fold(
        errors => BadRequest(errors.map(_.sanitized).mkString_("", ",", "")),
        vc => Ok(s"counter: $vc")
      )
    case GET -> Root / "optvalid" :? OptValidatingCounter(c) =>
      c match {
        case Some(Invalid(errors)) => BadRequest(errors.map(_.sanitized).mkString_("", ",", ""))
        case Some(Valid(cv)) => Ok(s"counter: $cv")
        case None => Ok("no counter")
      }
    case GET -> Root / "multiopt" :? MultiOptCounter(counters) =>
      counters match {
        case Valid(cs @ (_ :: _)) => Ok(s"${cs.length}: ${cs.mkString(",")}")
        case Valid(Nil) => Ok("absent")
        case Invalid(errors) => BadRequest(errors.toList.map(_.details).mkString("\n"))
      }
    case GET -> Root / "flagparam" :? Flag(flag) =>
      if (flag) Ok("flag present")
      else Ok("flag not present")
    case r =>
      NotFound(s"404 Not Found: ${r.pathInfo}")
  }

  def serve(req: Request[IO]): IO[Response[IO]] =
    app(req)

  test("Path DSL within HttpService should GET /") {
    val response = serve(Request(GET, Uri(path = "/")))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("(empty)")
  }
  test("Path DSL within HttpService should GET /{id}") {
    val response = serve(Request(GET, Uri(path = "/12345")))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("id: 12345")
  }
  test("Path DSL within HttpService should GET /?{start}") {
    val response = serve(Request(GET, uri("/?start=1")))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("start: 1")
  }
  test("Path DSL within HttpService should GET /?{start,limit}") {
    val response = serve(Request(GET, uri("/?start=1&limit=2")))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("start: 1, limit: 2")
  }
  test("Path DSL within HttpService should GET /calc") {
    val response = serve(Request(GET, Uri(path = "/calc")))
    response.map(_.status).assertEquals(NotFound) *>
      response.flatMap(_.as[String]).assertEquals("404 Not Found: /calc")
  }
  test("Path DSL within HttpService should GET /calc?decimal=1.3") {
    val response =
      serve(Request(GET, Uri(path = "/calc", query = Query.fromString("decimal=1.3"))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals(s"result: 0.65")
  }
  test("Path DSL within HttpService should GET /items?list=1&list=2&list=3&list=4&list=5") {
    val response = serve(
      Request(
        GET,
        Uri(path = "/items", query = Query.fromString("list=1&list=2&list=3&list=4&list=5"))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals(s"items: 1,2,3,4,5")
  }
  test("Path DSL within HttpService should GET /search") {
    val response = serve(Request(GET, Uri(path = "/search")))
    response.map(_.status).assertEquals(NotFound) *>
      response.flatMap(_.as[String]).assertEquals("404 Not Found: /search")
  }
  test("Path DSL within HttpService should GET /search?term") {
    val response = serve(Request(GET, Uri(path = "/search", query = Query.fromString("term"))))
    response.map(_.status).assertEquals(NotFound) *>
      response.flatMap(_.as[String]).assertEquals("404 Not Found: /search")
  }
  test("Path DSL within HttpService should GET /search?term=") {
    val response = serve(Request(GET, Uri(path = "/search", query = Query.fromString("term="))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("term: ")
  }
  test("Path DSL within HttpService should GET /search?term= http4s  ") {
    val response =
      serve(Request(GET, Uri(path = "/search", query = Query.fromString("term=%20http4s%20%20"))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("term:  http4s  ")
  }
  test("Path DSL within HttpService should GET /search?term=http4s") {
    val response =
      serve(Request(GET, Uri(path = "/search", query = Query.fromString("term=http4s"))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("term: http4s")
  }
  test("Path DSL within HttpService should optional parameter present") {
    val response = serve(Request(GET, Uri(path = "/app", query = Query.fromString("counter=3"))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("counter: Some(3)")
  }
  test("Path DSL within HttpService should optional parameter absent") {
    val response = serve(Request(GET, Uri(path = "/app", query = Query.fromString("other=john"))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("counter: None")
  }
  test("Path DSL within HttpService should optional parameter present with incorrect format") {
    val response =
      serve(Request(GET, Uri(path = "/app", query = Query.fromString("counter=john"))))
    response.map(_.status).assertEquals(NotFound)
  }
  test("Path DSL within HttpService should validating parameter present") {
    val response =
      serve(Request(GET, Uri(path = "/valid", query = Query.fromString("counter=3"))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("counter: 3")
  }
  test("Path DSL within HttpService should validating parameter absent") {
    val response =
      serve(Request(GET, Uri(path = "/valid", query = Query.fromString("notthis=3"))))
    response.map(_.status).assertEquals(NotFound)
  }
  test("Path DSL within HttpService should validating parameter present with incorrect format") {
    val response =
      serve(Request(GET, Uri(path = "/valid", query = Query.fromString("counter=foo"))))
    response.map(_.status).assertEquals(BadRequest) *>
      response.flatMap(_.as[String]).assertEquals("Query decoding Int failed")
  }
  test("Path DSL within HttpService should optional validating parameter present") {
    val response =
      serve(Request(GET, Uri(path = "/optvalid", query = Query.fromString("counter=3"))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("counter: 3")
  }
  test("Path DSL within HttpService should optional validating parameter absent") {
    val response =
      serve(Request(GET, Uri(path = "/optvalid", query = Query.fromString("notthis=3"))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("no counter")
  }
  test(
    "Path DSL within HttpService should optional validating parameter present with incorrect format") {
    val response =
      serve(Request(GET, Uri(path = "/optvalid", query = Query.fromString("counter=foo"))))
    response.map(_.status).assertEquals(BadRequest) *>
      response.flatMap(_.as[String]).assertEquals("Query decoding Int failed")
  }
  test("Path DSL within HttpService should optional multi parameter with no parameters") {
    val response = serve(Request(GET, Uri(path = "/multiopt")))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("absent")
  }
  test("Path DSL within HttpService should optional multi parameter with multiple parameters") {
    val response = serve(
      Request(
        GET,
        Uri(path = "/multiopt", query = Query.fromString("counter=1&counter=2&counter=3"))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("3: 1,2,3")
  }
  test("Path DSL within HttpService should optional multi parameter with one parameter") {
    val response =
      serve(Request(GET, Uri(path = "/multiopt", query = Query.fromString("counter=3"))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("1: 3")
  }
  test("Path DSL within HttpService should optional multi parameter with incorrect format") {
    val response =
      serve(Request(GET, Uri(path = "/multiopt", query = Query.fromString("counter=foo"))))
    response.map(_.status).assertEquals(BadRequest)
  }
  test("Path DSL within HttpService should optional multi parameter with one incorrect parameter") {
    val response = serve(
      Request(GET, Uri(path = "/multiopt", query = Query.fromString("counter=foo&counter=1"))))

    val response2 = serve(
      Request(GET, Uri(path = "/multiopt", query = Query.fromString("counter=1&counter=foo"))))
    response.map(_.status).assertEquals(BadRequest) *>
      response2.map(_.status).assertEquals(BadRequest)
  }
  test(
    "Path DSL within HttpService should optional multi parameter with two incorrect parameters must return both") {
    val response = serve(
      Request(GET, Uri(path = "/multiopt", query = Query.fromString("counter=foo&counter=bar"))))
    response.map(_.status).assertEquals(BadRequest) *>
      response
        .flatMap(_.as[String])
        .map(_.split("\n").toList)
        .assertEquals(
          scala.List(
            """For input string: "foo"""",
            """For input string: "bar""""
          ))
  }
  test("Path DSL within HttpService should optional flag parameter when present") {
    val response =
      serve(Request(GET, Uri(path = "/flagparam", query = Query.fromString("flag"))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("flag present")
  }
  test("Path DSL within HttpService should optional flag parameter when present with a value") {
    val response =
      serve(Request(GET, Uri(path = "/flagparam", query = Query.fromString("flag=1"))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("flag present")
  }
  test("Path DSL within HttpService should optional flag parameter when not present") {
    val response =
      serve(Request(GET, Uri(path = "/flagparam", query = Query.fromString(""))))
    response.map(_.status).assertEquals(Ok) *>
      response.flatMap(_.as[String]).assertEquals("flag not present")
  }
}
