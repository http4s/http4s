package org.http4s

import scala.language.implicitConversions
import concurrent.Future
import scala.language.reflectiveCalls

import scala.concurrent.Await
import scala.concurrent.duration._

import org.specs2.mutable.Specification
import play.api.libs.iteratee._
import org.specs2.time.NoTimeConversions
import scala.io.Codec

import Writable._
import Bodies._
import java.nio.charset.Charset

class MockServerSpec extends Specification with NoTimeConversions {
  import scala.concurrent.ExecutionContext.Implicits.global

  def stringHandler(charset: Charset, maxSize: Int = Integer.MAX_VALUE)(f: String => Responder): Iteratee[Chunk, Responder] = {
    Traversable.takeUpTo[Chunk](maxSize)
      .transform(Iteratee.consume[Chunk]().asInstanceOf[Iteratee[Chunk, Chunk]].map {
      bs => new String(bs, charset)
    })
      .flatMap(Iteratee.eofOrElse(Responder(statusLine = StatusLine.RequestEntityTooLarge)))
      .map(_.right.map(f).merge)
  }

  val server = new MockServer({
    case req if req.requestMethod == Method.Post && req.pathInfo == "/echo" =>
      Future(Responder(body = req.body))
    case req if req.requestMethod == Method.Post && req.pathInfo == "/sum" =>
      req.body.run(stringHandler(req.charset, 16)(s => Responder(body = s.split('\n').map(_.toInt).sum)))

    case req if req.pathInfo == "/fail" =>
      Future(Responder( statusLine = StatusLine.InternalServerError))
  })

  def response(req: Request): MockServer.Response = {
    Await.result(server(req), 5 seconds)
  }

  "A mock server" should {
    "handle matching routes" in {
      val req = Request(requestMethod = Method.Post, pathInfo = "/echo",
        body = Seq("one", "two", "three"))
      new String(response(req).body) should_==("onetwothree")
    }

    "runs a sum" in {
      val req = Request(requestMethod = Method.Post, pathInfo = "/sum",
        body = Seq("1\n", "2\n3", "\n4"))
      new String(response(req).body) should_==("10")
    }

    "runs too large of a sum" in {
      val req = Request(requestMethod = Method.Post, pathInfo = "/sum",
        body = "12345678\n901234567")
      response(req).statusLine should_==(StatusLine.RequestEntityTooLarge)
    }

    "fall through to not found" in {
      val req = Request(pathInfo = "/bielefield")
      response(req).statusLine should_== StatusLine.NotFound
    }

    "handle exceptions" in {
      val req = Request(pathInfo = "/fail")
      response(req).statusLine should_== StatusLine.InternalServerError
    }
  }
}
