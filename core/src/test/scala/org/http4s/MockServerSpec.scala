package org.http4s

import scala.language.reflectiveCalls

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

import org.specs2.mutable.Specification
import play.api.libs.iteratee._
import org.specs2.time.NoTimeConversions

class MockServerSpec extends Specification with NoTimeConversions {
  import scala.concurrent.ExecutionContext.Implicits.global

  val timeout = 5 seconds

  val server = new MockServer({
    case req if req.requestMethod == Method.Post && req.pathInfo == "/echo" =>
      // Iteratee brain teaser: how can we return the response header immediately while
      // continuing to consume the request body?
      Enumeratee.collect[Chunk] { case chunk: BodyChunk => chunk.bytes }
        .transform(Iteratee.consume[Array[Byte]](): Iteratee[Array[Byte], Array[Byte]])
        .map { bytes => Response(entityBody = new MessageBody(body = Enumerator(bytes).through(Enumeratee.map(BodyChunk(_))))) }
    case req if req.requestMethod == Method.Post && req.pathInfo == "/sum" =>
      Enumeratee.collect[Chunk] { case chunk: BodyChunk => new String(chunk.bytes).toInt }
        .transform(Iteratee.fold(0)((sum, i) => sum + i))
        .map { sum => Response(entityBody = MessageBody(sum)) }
    case req if req.pathInfo == "/fail" =>
      sys.error("FAIL")
  })

  "A mock server" should {
    "handle matching routes" in {
      val req = Request(requestMethod = Method.Post, pathInfo = "/echo")
      val reqBody = MessageBody("one", "two", "three")
      Await.result(for {
        res <- server(req, reqBody)
        resString <- res.entityBody.asString
      } yield {
        resString should_==("onetwothree")
      }, timeout)
    }

    "runs a sum" in {
      val req = Request(requestMethod = Method.Post, pathInfo = "/sum")
      val reqBody = MessageBody(1, 2, 3)
      Await.result(for {
        res <- server(req, reqBody)
        resString <- res.entityBody.asString
      } yield {
        resString should_==("6")
      }, timeout)
    }

    "fall through to not found" in {
      val req = Request(pathInfo = "/bielefield")
      Await.result(for {
        res <- server(req)
      } yield {
        res.statusLine.code should_==(404)
      }, timeout)
    }

    "handle exceptions" in {
      val req = Request(pathInfo = "/fail")
      Await.result(for {
        res <- server(req)
      } yield {
        res.statusLine.code should_==(500)
      }, timeout)
    }
  }
}
