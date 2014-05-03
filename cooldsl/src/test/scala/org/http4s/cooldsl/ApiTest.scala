package org.http4s
package cooldsl

import org.specs2.mutable._
import shapeless.HNil
import scalaz.concurrent.Task
import scodec.bits.ByteVector
import Status._

import scalaz.-\/
import org.http4s.Response
import scalaz.\/-

/**
 * Created by Bryce Anderson on 4/26/14.
 */
class ApiTest extends Specification {


  val lenheader = Header.`Content-Length`(4)
  val etag = Header.ETag("foo")

  val RequireETag = require(Header.ETag)
  val RequireNonZeroLen = requireThat(Header.`Content-Length`){ h => h.length != 0 }

  def printBody(resp: Response) {
    val s = new String(resp.body.runLog.run.reduce(_ ++ _).toArray)
    println(s)
  }

  def fetch(p: Option[Task[Response]]) = p.get.run.headers.get(Header.ETag).get.value

  "CoolDsl bits" should {
    "Combine validators" in {
      RequireETag && RequireNonZeroLen should_== And(RequireETag, RequireNonZeroLen)
    }

    "Fail on a bad request" in {
      val badreq = Request().withHeaders(Headers(lenheader))
      RouteExecutor.ensureValidHeaders(RequireETag && RequireNonZeroLen,badreq) should_== -\/(s"Missing header: ${etag.name}")
    }

    "Match captureless route" in {
      val c = RequireETag && RequireNonZeroLen

      val req = Request().withHeaders(Headers(etag, lenheader))
      RouteExecutor.ensureValidHeaders(c,req) should_== \/-(HNil)
    }

    "Capture params" in {
      val req = Request().withHeaders(Headers(etag, lenheader))
      Seq({
        val c2 = capture(Header.`Content-Length`) && RequireETag
        RouteExecutor.ensureValidHeaders(c2,req) should_== \/-(lenheader::HNil)
      }, {
        val c3 = capture(Header.`Content-Length`) && capture(Header.ETag)
        RouteExecutor.ensureValidHeaders(c3,req) should_== \/-(etag::lenheader::HNil)
      }).reduce( _ and _)
    }

    "Map header params" in {
      val req = Request().withHeaders(Headers(etag, lenheader))
      val c = requireMap(Header.`Content-Length`)(_.length)
      RouteExecutor.ensureValidHeaders(c,req) should_== \/-(4::HNil)
    }

    "Run || routes" in {
      val p1 = "one" / 'two
      val p2 = "three" / 'four

      val f = Method.Get / (p1 || p2) |>> { (s: String) => Ok("").withHeaders(Header.ETag(s)) }

      val req1 = Request(requestUri = Uri.fromString("/one/two").get)
      fetch(f(req1)) should_== "two"

      val req2 = Request(requestUri = Uri.fromString("/three/four").get)
      fetch(f(req2)) should_== "four"
    }
  }

  "PathValidator" should {
    import Status._

    def check(p: Option[Task[Response]], s: String) = {
      p.get.run.headers.get(Header.ETag).get.value should_== s
    }

    "traverse a captureless path" in {
      val stuff = Method.Get / "hello"
      val req = Request(requestUri = Uri.fromString("/hello").get)

      val f: Request => Option[Task[Response]] = stuff |>> { () => Ok("Cool.").withHeaders(Header.ETag("foo")) }
      check(f(req), "foo")
    }

    "Not match a path to long" in {
      val stuff = Method.Get / "hello"
      val req = Request(requestUri = Uri.fromString("/hello/world").get)

      val f: Request => Option[Task[Response]] = stuff |>> { () => Ok("Cool.").withHeaders(Header.ETag("foo")) }
      val r = f(req)
      r should_== None
    }

    "capture a variable" in {
      val stuff = Method.Get / 'hello
      val req = Request(requestUri = Uri.fromString("/hello").get)

      val f: Request => Option[Task[Response]] = stuff |>> { str: String => Ok("Cool.").withHeaders(Header.ETag(str)) }
      check(f(req), "hello")
    }

    "work directly" in {
      val stuff = Method.Get / "hello"
      val req = Request(requestUri = Uri.fromString("/hello").get)

      val f = stuff |>> { () => Ok("Cool.").withHeaders(Header.ETag("foo")) }

      check(f(req), "foo")
    }

    "capture end with nothing" in {
      val stuff = Method.Get / "hello" / -*
      val req = Request(requestUri = Uri.fromString("/hello").get)
      val f = stuff |>> { path: List[String] => Ok("Cool.").withHeaders(Header.ETag(if (path.isEmpty) "go" else "nogo")) }
      check(f(req), "go")
    }

    "capture remaining" in {
      val stuff = Method.Get / "hello" / -*
      val req = Request(requestUri = Uri.fromString("/hello/world/foo").get)
      val f = stuff |>> { path: List[String] => Ok("Cool.").withHeaders(Header.ETag(path.mkString)) }
      check(f(req), "worldfoo")
    }
  }

  "Query validators" should {
    import Status._

    "get a query string" in {
      val path = Method.Post / "hello" -? query[Int]("jimbo")
      val req = Request(requestUri = Uri.fromString("/hello?jimbo=32").get)

      val route = path |>> { i: Int =>
        Ok("stuff").withHeaders(Header.ETag((i + 1).toString))
      }

      fetch(route(req)) should_== "33"

    }
  }

  "Decoders" should {
    import Status._
    import BodyCodec._
    import scalaz.stream.Process

    "Decode a body" in {
      val path = Method.Post / "hello"
      val reqHeader = requireThat(Header.`Content-Length`){ h => h.length < 10}
      val body = Process.emit(ByteVector.apply("foo".getBytes()))
      val req = Request(requestUri = Uri.fromString("/hello").get, body = body)
                  .withHeaders(Headers(Header.`Content-Length`("foo".length)))

      val route = path.validate(reqHeader).decoding(strDec) |>> { str: String =>
        Ok("stuff").withHeaders(Header.ETag(str))
      }

      fetch(route(req)) should_== "foo"
    }

    "Fail on a header" in {
      val path = Method.Post / "hello"
      val reqHeader = requireThat(Header.`Content-Length`){ h => h.length < 2}
      val body = Process.emit(ByteVector.apply("foo".getBytes()))
      val req = Request(requestUri = Uri.fromString("/hello").get, body = body)
        .withHeaders(Headers(Header.`Content-Length`("foo".length)))

      val route = path.validate(reqHeader).decoding(strDec) |>> { str: String =>
        Ok("stuff").withHeaders(Header.ETag(str))
      }

      val result = route(req)
      result.get.run.status should_== Status.BadRequest
    }
  }

  "Do a complicated one" in {
    import Status._
    import BodyCodec._
    import scalaz.stream.Process

    val path = Method.Post / "hello" / 'world -? query[Int]("fav")
    val validations = requireThat(Header.`Content-Length`){ h => h.length != 0 } &&
                      capture(Header.ETag)

    val route =
      (path >>> validations).decoding(strDec) |>> {(world: String, fav: Int, tag: Header.ETag, body: String) =>

        Ok(s"Hello to you too, $world. Your Fav number is $fav. You sent me $body")
          .addHeaders(Header.ETag("foo"))
      }

    val body = Process.emit(ByteVector("cool".getBytes))
    val req = Request(requestUri = Uri.fromString("/hello/neptune?fav=23").get, body = body)
                .withHeaders(Headers(Header.`Content-Length`(4), Header.ETag("foo")))

    val resp = route(req).get.run
    resp.headers.get(Header.ETag).get.value should_== "foo"
  }

  "Append headers to a Route" in {
    import Status._
    import BodyCodec._
    import scalaz.stream.Process

    val path = Method.Post / "hello" / 'world -? query[Int]("fav")
    val validations = requireThat(Header.`Content-Length`){ h => h.length != 0 }


    val route = (path >>> validations >>> capture(Header.ETag)).decoding(strDec) |>>
      {(world: String, fav: Int, tag: Header.ETag, body: String) =>

        Ok(s"Hello to you too, $world. Your Fav number is $fav. You sent me $body")
          .addHeaders(Header.ETag("foo"))
      }

    val body = Process.emit(ByteVector("cool".getBytes))
    val req = Request(requestUri = Uri.fromString("/hello/neptune?fav=23").get, body = body)
      .withHeaders(Headers(Header.`Content-Length`(4), Header.ETag("foo")))

    val resp = route(req).get.run
    resp.headers.get(Header.ETag).get.value should_== "foo"

  }
}
