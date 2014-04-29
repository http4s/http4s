package org.http4s
package cooldsl

import org.specs2.mutable._
import shapeless.HNil
import scalaz.{\/-, -\/}
import scalaz.concurrent.Task
import scodec.bits.ByteVector
import scalaz.stream.Process
import java.lang.Process


import org.http4s.cooldsl.BodyCodec.Decoder
import org.http4s.cooldsl.PathBuilder._
import org.http4s.cooldsl.HeaderMatcher._
/**
 * Created by Bryce Anderson on 4/26/14.
 */
class ApiTest extends Specification {

  import HeaderMatcher._

  val lenheader = Header.`Content-Length`(4)
  val etag = Header.ETag("foo")

  val a = HeaderMatcher.require(Header.ETag)
  val b = HeaderMatcher.requireThat(Header.`Content-Length`){ h => h.length != 0 }

  "CoolDsl bits" should {
    "Combine validators" in {
      a && b should_== And(a, b)
    }

    "Fail on a bad request" in {
      val badreq = Request().withHeaders(Headers(lenheader))
      RouteExecutor.ensureValidHeaders(a && b,badreq) should_== -\/(s"Missing header: ${etag.name}")
    }

    "Match captureless route" in {
      val c = a && b

      val req = Request().withHeaders(Headers(etag, lenheader))
      RouteExecutor.ensureValidHeaders(c,req) should_== \/-(HNil)
    }

    "Capture params" in {
      val req = Request().withHeaders(Headers(etag, lenheader))
      Seq({
        val c2 = HeaderMatcher.capture(Header.`Content-Length`) && a
        RouteExecutor.ensureValidHeaders(c2,req) should_== \/-(lenheader::HNil)
      }, {
        val c3 = HeaderMatcher.capture(Header.`Content-Length`) &&
          HeaderMatcher.capture(Header.ETag)
        RouteExecutor.ensureValidHeaders(c3,req) should_== \/-(lenheader::etag::HNil)
      }).reduce( _ and _)
    }

    "Map header params" in {
      val req = Request().withHeaders(Headers(etag, lenheader))
      val c = HeaderMatcher.map(Header.`Content-Length`)(_.length)
      RouteExecutor.ensureValidHeaders(c,req) should_== \/-(4::HNil)
    }

    "Combine status line" in {

      true should_== true
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

      val f: Request => Option[Task[Response]] = stuff ~> { () => Ok("Cool.").withHeaders(Header.ETag("foo")) }
      check(f(req), "foo")
    }

    "Not match a path to long" in {
      val stuff = Method.Get / "hello"
      val req = Request(requestUri = Uri.fromString("/hello/world").get)

      val f: Request => Option[Task[Response]] = stuff ~> { () => Ok("Cool.").withHeaders(Header.ETag("foo")) }
      f(req) should_== None
    }

    "capture a variable" in {
      val stuff = Method.Get / 'hello
      val req = Request(requestUri = Uri.fromString("/hello").get)

      val f: Request => Option[Task[Response]] = stuff ~> { str: String => Ok("Cool.").withHeaders(Header.ETag(str)) }
      check(f(req), "hello")
    }

    "work directly" in {
      val stuff = Method.Get / "hello"
      val req = Request(requestUri = Uri.fromString("/hello").get)

      val f = stuff ~> { () => Ok("Cool.").withHeaders(Header.ETag("foo")) }

      check(f(req), "foo")
    }
  }

  "Query validators" should {
    import Status._

    def check(p: Option[Task[Response]], s: String) = p match {
      case Some(r) => r.run
        .headers.get(Header.ETag)
        .get.value should_== s
      case None => sys.error("Didn't match!")
    }

    "get a query string" in {
      val path = Method.Post / "hello" *? query[Int]("jimbo")
      val req = Request(requestUri = Uri.fromString("/hello?jimbo=32").get)

      val route = path ~> { i: Int =>
        Ok("stuff").withHeaders(Header.ETag((i + 1).toString))
      }

      check(route(req), "33")

    }
  }

  "Decoders" should {
    import Status._
    import BodyCodec._
    import scalaz.stream.Process

    def check(p: Option[Task[Response]], s: String) = {
      p.get.run.headers.get(Header.ETag).get.value should_== s
    }

    "Decode a body" in {
      val path = Method.Post / "hello"
      val reqHeader = requireThat(Header.`Content-Length`){ h => h.length < 10}
      val body = Process.emit(ByteVector.apply("foo".getBytes()))
      val req = Request(requestUri = Uri.fromString("/hello").get, body = body)
                  .withHeaders(Headers(Header.`Content-Length`("foo".length)))

      val route = path.validate(reqHeader).decoding(strDec) ~> { str: String =>
        Ok("stuff").withHeaders(Header.ETag(str))
      }

      check(route(req), "foo")
    }

    "Fail on a header" in {
      val path = Method.Post / "hello"
      val reqHeader = requireThat(Header.`Content-Length`){ h => h.length < 2}
      val body = Process.emit(ByteVector.apply("foo".getBytes()))
      val req = Request(requestUri = Uri.fromString("/hello").get, body = body)
        .withHeaders(Headers(Header.`Content-Length`("foo".length)))

      val route = path.validate(reqHeader).decoding(strDec) ~> { str: String =>
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

    val path = Method.Post / "hello" / 'world *? query[Int]("fav")
    val validations = requireThat(Header.`Content-Length`){ h => h.length != 0 } &&
                      capture(Header.ETag)

    val route =
      path.validate(validations).decoding(strDec)~>{(world: String, fav: Int, tag: Header.ETag, body: String) =>

        Ok(s"Hello to you too, $world. Your Fav number is $fav. You sent me $body")
          .addHeaders(Header.ETag("foo"))
      }

    val body = Process.emit(ByteVector("cool".getBytes))
    val req = Request(requestUri = Uri.fromString("/hello/neptune?fav=23").get, body = body)
                .withHeaders(Headers(Header.`Content-Length`(4), Header.ETag("foo")))

    val resp = route(req).get.run
    printBody(resp)
    resp.headers.get(Header.ETag).get.value should_== "foo"
  }

  def printBody(resp: Response) {
    val s = new String(resp.body.runLog.run.reduce(_ ++ _).toArray)
    println(s)
  }

  "dream api" should {
    import Status.Ok

    "Allow the segregation of building a path parser and validations" in {

      /** The path and validation steps should be composed seperately and then
        * combined into the final object. This final object can then be combined
        * with a suitable 'executor' to generate an 'Action'
        */

      val path = Method.Post / "hello"
      val path2 = path / 'world *? query[Int]("fav")

      val v1 = requireThat(Header.`Content-Length`)(_.length > 0)
      val v2 = v1 && capture(Header.ETag)

      // Now these two can be combined to make the 'Router'

      val r = path2.validate(v2)

      // Now this can be matched with a method to make the 'Action'
      // TODO: kill the prepare method. That can come from the combination of path and validation

      r ~>{(world: String, fav: Int, tag: Header.ETag) =>
        Ok("Success")
          .withHeaders(Header.ETag(fav.toString))
      }

      true should_== true

    }

  }

}
