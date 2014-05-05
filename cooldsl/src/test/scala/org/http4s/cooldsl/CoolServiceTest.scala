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

  def checkOk(r: Request): String = getBody(service(r).run.body)

  def checkError(r: Request): String = {
    getBody(service(r).run.body)
  }

  def Get(s: String, h: Header*): Request = Request(Method.Get, Uri.fromString(s).get, headers = Headers(h:_*))

  val service = new CoolService {
    Method.Get / "hello" |>>> { () => "route1" }

    Method.Get / 'hello |>>> { hello: String => "route2" }

    Method.Get / "hello" / "world" |>>> { () => "route3" }

    Method.Get / "hello" / "headers" -? query[Int]("foo") |>>> { foo: Int => "route" + foo }

    // Routes that will have different headers/query string requirements should work together
    Method.Get / "hello" / "compete" -? query[Int]("foo") |>>> { foo: Int => "route" + foo }

    Method.Get / "hello" / "compete" -? query[String]("foo") |>>> { foo: String => "route6_" + foo }

    Method.Get / "hello" / "compete" |>>> { () => "route7"}

    Method.Get / "variadic" / -* |>>> { tail: Seq[String] => "route8_" + tail.mkString("/") }

    val or = "or1" || "or2"
    Method.Get / or |>>> { () => "route9" }

    Method.Get / "options" -? query[Option[String]]("foo") |>>> { os: Option[String] => os.getOrElse("None") }

    Method.Get / "seq" -? query[Seq[String]]("foo") |>>> { os: Seq[String] => os.mkString(" ") }

    Method.Get / "seq" -? query[Seq[Int]]("foo") |>>> { os: Seq[Int] => os.mkString(" ") }

    Method.Get / "withreq" -? query[String]("foo") |>>> { (req: Request, foo: String) => s"req $foo" }
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

    "Perform path 'or' logic" in {
      val req1 = Get("/or1")
      val req2 = Get("/or2")
      (checkOk(req1) should_== "route9") and
      (checkOk(req2) should_== "route9")
    }

    "Work with options" in {
      val req1 = Get("/options")
      val req2 = Get("/options?foo=bar")
      (checkOk(req1) should_== "None") and
      (checkOk(req2) should_== "bar")
    }

    "Work with collections" in {
      val req1 = Get("/seq")
      val req2 = Get("/seq?foo=bar")
      val req3 = Get("/seq?foo=1&foo=2")
      (checkOk(req1) should_== "")    and
      (checkOk(req2) should_== "bar") and
      (checkOk(req3) should_== "1 2")
    }

    "Provide the request if desired" in {
      val req = Get("/withreq?foo=bar")
      checkOk(req) should_== "req bar"
    }
  }

}
