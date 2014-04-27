package org.http4s
package cooldsl

import org.specs2.mutable._
import shapeless.HNil
import scalaz.{\/-, -\/}
import scalaz.concurrent.Task
import scodec.bits.ByteVector

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

      val f: Request => Option[Task[Response]] = stuff.compile ~> { () => Ok("Cool.").withHeaders(Header.ETag("foo")) }
      check(f(req), "foo")
    }

    "Not match a path to long" in {
      val stuff = Method.Get / "hello"
      val req = Request(requestUri = Uri.fromString("/hello/world").get)

      val f: Request => Option[Task[Response]] = stuff.compile ~> { () => Ok("Cool.").withHeaders(Header.ETag("foo")) }
      f(req) should_== None
    }

    "capture a variable" in {
      val stuff = Method.Get / 'hello
      val req = Request(requestUri = Uri.fromString("/hello").get)

      val f: Request => Option[Task[Response]] = stuff.compile ~> { str: String => Ok("Cool.").withHeaders(Header.ETag(str)) }
      check(f(req), "hello")
    }
  }

  "Decoders" should {
    import Status._
    import FuncHelpers._
    import BodyCodec._
    import scalaz.stream.Process

    val strdec: Decoder[String] = { p: HttpBody =>
      p.runLog.map(v => new String(v.reduce(_ ++ _).toArray))
    }

    def check(p: Option[Task[Response]], s: String) = {
      p.get.run.headers.get(Header.ETag).get.value should_== s
    }

    "Decode a body" in {
      val path = Method.Post / "hello"
      val reqHeader = requireThat(Header.`Content-Length`){ h => h.length < 10}
      val body = Process.emit(ByteVector.apply("foo".getBytes()))
      val req = Request(requestUri = Uri.fromString("/hello").get, body = body)
                  .withHeaders(Headers(Header.`Content-Length`("foo".length)))

      val route = path.validate(reqHeader).decoding(strdec) ~> { str: String =>
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

      val route = path.validate(reqHeader).decoding(strdec) ~> { str: String =>
        Ok("stuff").withHeaders(Header.ETag(str))
      }

      val result = route(req)
      result.get.run.status should_== Status.BadRequest
    }
  }

}
