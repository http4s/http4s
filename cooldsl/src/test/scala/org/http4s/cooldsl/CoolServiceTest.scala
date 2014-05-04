package org.http4s
package cooldsl

import org.specs2.mutable.Specification
import Status._
import scodec.bits.ByteVector

/**
 * Created by Bryce Anderson on 5/3/14.
 */
class CoolServiceTest extends Specification {

  def getBody(b: HttpBody): String = {
    new String(b.runLog.run.foldLeft(ByteVector.empty)(_ ++ _).toArray)
  }

  def checkOk(r: Request): String = {
    service(r).run.headers.get(Header.ETag)
      .map(_.value)
      .getOrElse(null)
  }

  def checkError(r: Request): String = {
    getBody(service(r).run.body)
  }

  def Tag(s: String) = Ok(s).withHeaders(Header.ETag(s))

  def Get(s: String, h: Header*): Request = Request(Method.Get, Uri.fromString(s).get, headers = Headers(h:_*))

  val service = new CoolService {
    val route1 = Method.Get / "hello" |>>> { () => Tag("route1") }
    append(route1)

    val route2 = Method.Get / 'hello |>>> { s: String => Tag("route2") }
    append(route2)

    val route3 = Method.Get / "hello" / "world" |>>> { () => Tag("route3")}
    append(route3)

    val route4 = Method.Get / "hello" / "headers" -? query[Int]("foo") |>>> { i: Int => Tag("route" + i)}
    append(route4)

    // Routes that will have different headers/query string requirements should work together
    val route5 = Method.Get / "hello" / "compete" -? query[Int]("foo") |>>> { i: Int => Tag("route" + i)}
    append(route5)

    val route6 = Method.Get / "hello" / "compete" -? query[String]("foo") |>>> { i: String => Tag("route6_" + i)}
    append(route6)

    val route7 = Method.Get / "hello" / "compete" |>>> { () => Tag("route7")}
    append(route7)

    val route8 = Method.Get / "variadic" / -* |>>> { tail: Seq[String] => Tag("route8_" + tail.mkString("/"))}
    append(route8)
  }

  "CoolService" should {
    "Execute a route with no params" in {
      val req = Get("/hello")
      checkOk(req) should_== "route1"
    }

    "Execute a route with a single param" in {
      val req = Get("/world")
      checkOk(req) should_== "route2"
    }

    "Execute a route with a single param" in {
      val req = Get("/hello/world")
      checkOk(req) should_== "route3"
    }

    "Execute a route with a query" in {
      val req = Get("/hello/headers?foo=4")
      checkOk(req) should_== "route4"
    }

    "Fail a route with a missing query" in {
      val req = Get("/hello/headers")
      checkError(req) should_== "Missing query param: foo"
    }

    "Fail a route with an invalid query" in {
      val req = Get("/hello/headers?foo=bar")
      checkError(req) should_== "Invalid Number Format: bar"
    }

    "Execute a route with a competing query" in {
      val req1 = Get("/hello/compete?foo=5")
      val req2 = Get("/hello/compete?foo=bar")
      val req3 = Get("/hello/compete")
      (checkOk(req1) should_== "route5")      and
      (checkOk(req2) should_== "route6_bar")  and
      (checkOk(req3) should_== "route7")
    }

    "Execute a variadic route" in {
      val req1 = Get("/variadic")
      val req2 = Get("/variadic/one")
      val req3 = Get("/variadic/one/two")

      (checkOk(req1) should_== "route8_")          and
      (checkOk(req2) should_== "route8_one")       and
      (checkOk(req3) should_== "route8_one/two")
    }
  }

}
