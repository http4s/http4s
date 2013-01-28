package org.htt4ps

import scala.language.reflectiveCalls

import java.util.concurrent.TimeUnit
import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration
import scala.io.Codec

import org.specs2.mutable.Specification
import play.api.libs.iteratee._
import org.http4s.{Request, Response, Method, MockServer}
import java.lang.String

class MockServerSpec extends Specification {
  import scala.concurrent.ExecutionContext.Implicits.global

  val server = new MockServer({
    case req if req.requestMethod == Method.Post && req.pathInfo == "/echo" =>
      // Iteratee brain teaser: how can we return the response header immediately while
      // continuing to consume the request body?
      Iteratee.consume[Array[Byte]]().asInstanceOf[Iteratee[Array[Byte], Array[Byte]]]
        .map { bytes => Response(entityBody = Enumerator(bytes)) }
    case req if req.pathInfo == "/fail" =>
      sys.error("FAIL")
  })

  "A mock server" should {
    "handle matching routes" in {
      val req = Request(requestMethod = Method.Post, pathInfo = "/echo")
      val reqBody = Enumerator("one", "two", "three").through(Enumeratee.map(Codec.toUTF8))
      Await.result(for {
        res <- server(req, reqBody)
        resBytes <- res.entityBody.run(Iteratee.consume[Array[Byte]]()).asInstanceOf[Future[Array[Byte]]]
        resString = new String(resBytes)
      } yield {
        resString should_==("onetwothree")
      }, Duration(5, TimeUnit.SECONDS))
    }

    "fall through to not found" in {
      val req = Request(pathInfo = "/bielefield")
      val reqBody = Enumerator.eof[Array[Byte]]
      Await.result(for {
        res <- server(req, reqBody)
      } yield {
        res.statusLine.code should_==(404)
      }, Duration(5, TimeUnit.SECONDS))

    }

    "handle exceptions" in {
      val req = Request(pathInfo = "/fail")
      val reqBody = Enumerator.eof[Array[Byte]]
      Await.result(for {
        res <- server(req, reqBody)
      } yield {
        res.statusLine.code should_==(500)
      }, Duration(5, TimeUnit.SECONDS))
    }
  }
}
