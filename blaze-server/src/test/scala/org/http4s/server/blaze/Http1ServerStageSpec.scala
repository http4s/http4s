package org.http4s.server
package blaze

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant

import org.http4s.headers.{`Transfer-Encoding`, Date, `Content-Length`}
import org.http4s.{headers => H, _}
import org.http4s.blaze._
import org.http4s.blaze.pipeline.{Command => Cmd}
import org.http4s.dsl._
import org.specs2.specification.core.Fragment

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.Process

import scala.concurrent.ExecutionContext.Implicits.global

import scodec.bits.ByteVector

class Http1ServerStageSpec extends Http4sSpec {
  def makeString(b: ByteBuffer): String = {
    val p = b.position()
    val a = new Array[Byte](b.remaining())
    b.get(a).position(p)
    new String(a)
  }

  def parseAndDropDate(buff: ByteBuffer): (Status, Set[Header], String) =
    dropDate(ResponseParser.apply(buff))

  def dropDate(resp: (Status, Set[Header], String)): (Status, Set[Header], String) = {
    val hds = resp._2.filter(_.name != Date.name)
    (resp._1, hds, resp._3)
  }

  def runRequest(req: Seq[String], service: HttpService, maxReqLine: Int = 4*1024, maxHeaders: Int = 16*1024): Future[ByteBuffer] = {
    val head = new SeqTestHead(req.map(s => ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))))
    val httpStage = Http1ServerStage(service, AttributeMap.empty, testPool, true, maxReqLine, maxHeaders)

    pipeline.LeafBuilder(httpStage).base(head)
    head.sendInboundCommand(Cmd.Connected)
    head.result
  }

  "Http1ServerStage: Invalid Lengths" should {
    val req = "GET /foo HTTP/1.1\r\nheader: value\r\n\r\n"

    val service = HttpService {
      case req => Response().withBody("foo!")
    }
    "fail on too long of a request line" in {
      val buff = Await.result(runRequest(Seq(req), service, maxReqLine = 1), 5.seconds)
      val str = StandardCharsets.ISO_8859_1.decode(buff.duplicate()).toString
      // make sure we don't have signs of chunked encoding.
      str.contains("400 Bad Request") must_== true
    }

    "fail on too long of a header" in {
      val buff = Await.result(runRequest(Seq(req), service, maxHeaders = 1), 5.seconds)
      val str = StandardCharsets.ISO_8859_1.decode(buff.duplicate()).toString
      // make sure we don't have signs of chunked encoding.
      str.contains("400 Bad Request") must_== true
    }
  }

  "Http1ServerStage: Common responses" should {
    Fragment.foreach(ServerTestRoutes.testRequestResults.zipWithIndex) { case ((req, (status,headers,resp)), i) =>
      s"Run request $i Run request: --------\n${req.split("\r\n\r\n")(0)}\n" in {
        val result = Await.result(runRequest(Seq(req), ServerTestRoutes()), 5.seconds)
        parseAndDropDate(result) must_== ((status, headers, resp))
      }
    }
  }

  "Http1ServerStage: Errors" should {
    val exceptionService = HttpService {
      case GET -> Root / "sync" => sys.error("Synchronous error!")
      case GET -> Root / "async" => Task.fail(new Exception("Asynchronous error!"))
      case GET -> Root / "sync" / "422" => throw InvalidMessageBodyFailure("lol, I didn't even look")
      case GET -> Root / "async" / "422" => Task.fail(new InvalidMessageBodyFailure("lol, I didn't even look"))
    }

    def runError(path: String) = runRequest(List(path), exceptionService)
        .map(parseAndDropDate)
        .map{ case (s, h, r) =>
        val close = h.exists{ h => h.toRaw.name == "connection".ci && h.toRaw.value == "close"}
        (s, close, r)
      }

    "Deal with synchronous errors" in {
      val path = "GET /sync HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
      val (s,c,_) = Await.result(runError(path), 10.seconds)
      s must_== InternalServerError
      c must_== true
    }

    "Call toHttpResponse on synchronous errors" in {
      val path = "GET /sync/422 HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
      val (s,c,_) = Await.result(runError(path), 10.seconds)
      s must_== UnprocessableEntity
      c must_== false
    }

    "Deal with asynchronous errors" in {
      val path = "GET /async HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
      val (s,c,_) = Await.result(runError(path), 10.seconds)
      s must_== InternalServerError
      c must_== true
    }

    "Call toHttpResponse on asynchronous errors" in {
      val path = "GET /async/422 HTTP/1.1\r\nConnection:keep-alive\r\n\r\n"
      val (s,c,_) = Await.result(runError(path), 10.seconds)
      s must_== UnprocessableEntity
      c must_== false
    }
  }

  "Http1ServerStage: routes" should {
    "Do not send `Transfer-Encoding: identity` response" in {
      val service = HttpService {
        case req =>
          val headers = Headers(H.`Transfer-Encoding`(TransferCoding.identity))
          Response(headers = headers)
            .withBody("hello world")
      }

      // The first request will get split into two chunks, leaving the last byte off
      val req = "GET /foo HTTP/1.1\r\n\r\n"

      val buff = Await.result(runRequest(Seq(req), service), 5.seconds)

      val str = StandardCharsets.ISO_8859_1.decode(buff.duplicate()).toString
      // make sure we don't have signs of chunked encoding.
      str.contains("0\r\n\r\n") must_== false
      str.contains("hello world") must_== true

      val (_, hdrs, _) = ResponseParser.apply(buff)
      hdrs.find(_.name == `Transfer-Encoding`.name) must_== None
    }

    "Do not send an entity or entity-headers for a status that doesn't permit it" in {
      val service: HttpService = HttpService {
        case req =>
          Response(status = Status.NotModified)
            .putHeaders(`Transfer-Encoding`(TransferCoding.chunked))
            .withBody("Foo!")
      }

      val req = "GET /foo HTTP/1.1\r\n\r\n"

      val buf = Await.result(runRequest(Seq(req), service), 5.seconds)
      val (status, hs, body) = ResponseParser.parseBuffer(buf)

      val hss = Headers(hs.toList)
      `Content-Length`.from(hss).isDefined must_== false
      body must_== ""
      status must_== Status.NotModified
    }

    "Add a date header" in {
      val service = HttpService {
        case req => Task.now(Response(body = req.body))
      }

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"

      val buff = Await.result(runRequest(Seq(req1), service), 5.seconds)

      // Both responses must succeed
      val (_, hdrs, _) = ResponseParser.apply(buff)
      hdrs.find(_.name == Date.name) must beSome[Header]
    }

    "Honor an explicitly added date header" in {
      val dateHeader = Date(HttpDate.Epoch)
      val service = HttpService {
        case req => Task.now(Response(body = req.body).replaceAllHeaders(dateHeader))
      }

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"

      val buff = Await.result(runRequest(Seq(req1), service), 5.seconds)

      // Both responses must succeed
      val (_, hdrs, _) = ResponseParser.apply(buff)

      hdrs.find(_.name == Date.name) must_== Some(dateHeader)
    }

    "Handle routes that echos full request body for non-chunked" in {
      val service = HttpService {
        case req => Task.now(Response(body = req.body))
      }

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val (r11,r12) = req1.splitAt(req1.length - 1)

      val buff = Await.result(runRequest(Seq(r11,r12), service), 5.seconds)

      // Both responses must succeed
      parseAndDropDate(buff) must_== ((Ok, Set(H.`Content-Length`(4)), "done"))
    }

    "Handle routes that consumes the full request body for non-chunked" in {
      val service = HttpService {
        case req => req.as[String].flatMap { s => Response().withBody("Result: " + s) }
      }

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val (r11,r12) = req1.splitAt(req1.length - 1)

      val buff = Await.result(runRequest(Seq(r11,r12), service), 5.seconds)

      // Both responses must succeed
      parseAndDropDate(buff) must_== ((Ok, Set(H.`Content-Length`(8 + 4), H.
                                       `Content-Type`(MediaType.`text/plain`, Charset.`UTF-8`)), "Result: done"))
    }

    "Maintain the connection if the body is ignored but was already read to completion by the Http1Stage" in {

      val service = HttpService {
        case _ =>  Response().withBody("foo")
      }

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

      val buff = Await.result(runRequest(Seq(req1,req2), service), 5.seconds)

      val hs = Set(H.`Content-Type`(MediaType.`text/plain`, Charset.`UTF-8`), H.`Content-Length`(3))
      // Both responses must succeed
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, hs, "foo"))
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, hs, "foo"))
    }

    "Drop the connection if the body is ignored and was not read to completion by the Http1Stage" in {

      val service = HttpService {
        case req =>  Response().withBody("foo")
      }

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val (r11,r12) = req1.splitAt(req1.length - 1)

      val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

      val buff = Await.result(runRequest(Seq(r11, r12, req2), service), 5.seconds)

      val hs = Set(H.`Content-Type`(MediaType.`text/plain`, Charset.`UTF-8`), H.`Content-Length`(3))
      // Both responses must succeed
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, hs, "foo"))
      buff.remaining() must_== 0
    }

    "Handle routes that runs the request body for non-chunked" in {

      val service = HttpService {
        case req =>  req.body.run.flatMap { _ => Response().withBody("foo") }
      }

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val (r11,r12) = req1.splitAt(req1.length - 1)
      val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

      val buff = Await.result(runRequest(Seq(r11,r12,req2), service), 5.seconds)

      val hs = Set(H.`Content-Type`(MediaType.`text/plain`, Charset.`UTF-8`), H.`Content-Length`(3))
      // Both responses must succeed
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, hs, "foo"))
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, hs, "foo"))
    }

    // Think of this as drunk HTTP pipelining
    "Not die when two requests come in back to back" in {

      import scalaz.stream.Process.Step
      val service = HttpService {
        case req =>
          req.body.step match {
            case Step(p,_) => Task.now(Response(body = p))
            case _ => sys.error("Failure.")
          }
      }

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

      val buff = Await.result(runRequest(Seq(req1 + req2), service), 5.seconds)

      // Both responses must succeed
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, Set(H.`Content-Length`(4)), "done"))
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, Set(H.`Content-Length`(5)), "total"))
    }

    "Handle using the request body as the response body" in {

      val service = HttpService {
        case req => Task.now(Response(body = req.body))
      }

      // The first request will get split into two chunks, leaving the last byte off
      val req1 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 4\r\n\r\ndone"
      val req2 = "POST /sync HTTP/1.1\r\nConnection:keep-alive\r\nContent-Length: 5\r\n\r\ntotal"

      val buff = Await.result(runRequest(Seq(req1, req2), service), 5.seconds)

      // Both responses must succeed
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, Set(H.`Content-Length`(4)), "done"))
      dropDate(ResponseParser.parseBuffer(buff)) must_== ((Ok, Set(H.`Content-Length`(5)), "total"))
    }

    {
      def req(path: String) = s"GET /$path HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n" +
        "3\r\n" +
        "foo\r\n" +
        "0\r\n" +
        "Foo:Bar\r\n\r\n"

      val service = HttpService {
        case req if req.pathInfo == "/foo" =>
          for {
            _ <- req.body.run
            hs <- req.trailerHeaders
            resp <- Response().withBody(hs.mkString)
          } yield resp

        case req if req.pathInfo == "/bar" =>
          for {
          // Don't run the body
            hs <- req.trailerHeaders
            resp <- Response().withBody(hs.mkString)
          } yield resp

      }

      "Handle trailing headers" in {
        val buff = Await.result(runRequest(Seq(req("foo")), service), 5.seconds)

        val results = dropDate(ResponseParser.parseBuffer(buff))
        results._1 must_== Ok
        results._3 must_== "Foo: Bar"
      }

      "Fail if you use the trailers before they have resolved" in {
        val buff = Await.result(runRequest(Seq(req("bar")), service), 5.seconds)

        val results = dropDate(ResponseParser.parseBuffer(buff))
        results._1 must_== InternalServerError
      }
    }
  }
}
