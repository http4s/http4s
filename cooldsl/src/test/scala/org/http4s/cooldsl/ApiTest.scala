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

/**
 * Created by Bryce Anderson on 4/26/14.
 */
class ApiTest extends Specification {

  import CoolApi._

  val lenheader = Header.`Content-Length`(4)
  val etag = Header.ETag("foo")

  val a = CoolApi.require(Header.ETag)
  val b = CoolApi.requireThat(Header.`Content-Length`){ h => h.length != 0 }

  "CoolDsl Api" should {
    "Combine validators" in {
      a and b should_== And(a, b)

      true should_== true
    }

    "Fail on a bad request" in {
      val badreq = Request().withHeaders(Headers(lenheader))
      RouteExecutor.ensureValidHeaders(a and b,badreq) should_== -\/(s"Missing header: ${etag.name}")
    }

    "Match captureless route" in {
      val c = a and b

      val req = Request().withHeaders(Headers(etag, lenheader))
      RouteExecutor.ensureValidHeaders(c,req) should_== \/-(HNil)
    }

    "Capture params" in {
      val req = Request().withHeaders(Headers(etag, lenheader))
      Seq({
        val c2 = CoolApi.capture(Header.`Content-Length`) and a
        RouteExecutor.ensureValidHeaders(c2,req) should_== \/-(lenheader::HNil)
      }, {
        val c3 = CoolApi.capture(Header.`Content-Length`) and
          CoolApi.capture(Header.ETag)
        RouteExecutor.ensureValidHeaders(c3,req) should_== \/-(etag::lenheader::HNil)
      }).reduce( _ and _)
    }

    "Map header params" in {
      val req = Request().withHeaders(Headers(etag, lenheader))
      val c = CoolApi.map(Header.`Content-Length`)(_.length)
      RouteExecutor.ensureValidHeaders(c,req) should_== \/-(4::HNil)
    }

    "Combine status line" in {

      true should_== true
    }
  }

  "PathValidator" should {
    import Status._
    import FuncHelpers._

    def check(p: Option[Task[Response]], s: String) = {
      p.get.run.headers.get(Header.ETag).get.value should_== s
    }

    "traverse a captureless path" in {
      val stuff = Method.Get / "hello"
      val req = Request(requestUri = Uri.fromString("/hello").get)

      val f: Request => Option[Task[Response]] = stuff.prepare ~> { () => Ok("Cool.").withHeaders(Header.ETag("foo")) }
      check(f(req), "foo")
    }

    "Not match a path to long" in {
      val stuff = Method.Get / "hello"
      val req = Request(requestUri = Uri.fromString("/hello/world").get)

      val f: Request => Option[Task[Response]] = stuff.prepare ~> { () => Ok("Cool.").withHeaders(Header.ETag("foo")) }
      f(req) should_== None
    }

    "capture a variable" in {
      val stuff = Method.Get / 'hello
      val req = Request(requestUri = Uri.fromString("/hello").get)

      val f: Request => Option[Task[Response]] = stuff.prepare ~> { str: String => Ok("Cool.").withHeaders(Header.ETag(str)) }
      check(f(req), "hello")
    }

    "work directly" in {
      val stuff = Method.Get / "hello"
      val req = Request(requestUri = Uri.fromString("/hello").get)

      val f = stuff.prepare ~> { () => Ok("Cool.").withHeaders(Header.ETag("foo")) }

      check(f(req), "foo")
    }
  }

  "Query validators" should {
    import Status._
    import FuncHelpers._

    def check(p: Option[Task[Response]], s: String) = {
      p.get.run
        .headers.get(Header.ETag)
        .get.value should_== s
    }

    "get a query string" in {
      val path = Method.Post / "hello" *? query[Int]("jimbo")
      val req = Request(requestUri = Uri.fromString("/hello?jimbo=32").get)

      val route = path.prepare ~> { i: Int =>
        println("Running route ------------------------------------")
        Ok("stuff").withHeaders(Header.ETag((i + 1).toString))
      }

      check(route(req), "33")

    }
  }

  "Decoders" should {
    import Status._
    import FuncHelpers._
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
    import FuncHelpers._
    import BodyCodec._
    import scalaz.stream.Process

    val path = Method.Post / "hello" / 'world *? query[Int]("fav")
    val validations = requireThat(Header.`Content-Length`){ h => h.length != 0 } &&
                      capture(Header.ETag)

    val route =
      path.validate(validations).prepare~>{(world: String, fav: Int, tag: Header.ETag) =>

        Ok(s"Hello to you too, $world. Your Fav number is $fav. You sent me body")
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

}
