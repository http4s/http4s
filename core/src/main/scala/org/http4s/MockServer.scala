package org.http4s

import scala.language.reflectiveCalls

import concurrent.{Await, ExecutionContext, Future}
import concurrent.duration._
import play.api.libs.iteratee.{Enumerator, Iteratee}

class MockServer(route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) {
  import MockServer.MockResponse

  def apply(req: RequestPrelude, enum: Enumerator[Chunk]): Future[MockResponse] = {
    try {
      route.lift(req).fold(Future.successful(onNotFound)) { parser =>
        val it: Iteratee[Chunk, MockResponse] = parser.flatMap { response =>
          val responseBodyIt: Iteratee[BodyChunk, BodyChunk] = Iteratee.consume()
          response.body ><> BodyParser.whileBodyChunk &>> responseBodyIt map { bytes: BodyChunk =>
            MockResponse(response.prelude.status, response.prelude.headers, body = bytes.toArray, response.attributes)
          }
        }
        enum.run(it)
      }
    } catch {
      case t: Throwable => Future.successful(onError(t))
    }
  }

  def response(req: RequestPrelude,
               body: Enumerator[Chunk] = Enumerator.eof,
               wait: Duration = 5.seconds): MockResponse = {
    Await.result(apply(req, body), 5.seconds)
  }

  def onNotFound: MockResponse = MockResponse(statusLine = Status.NotFound)

  def onError: PartialFunction[Throwable, MockResponse] = {
    case e: Exception =>
      e.printStackTrace()
      MockResponse(statusLine = Status.InternalServerError)
  }
}

object MockServer {
  private[MockServer] val emptyBody = Array.empty[Byte]   // Makes direct Response comparison possible

  case class MockResponse(
    statusLine: Status = Status.Ok,
    headers: HeaderCollection = HeaderCollection.empty,
    body: Array[Byte] = emptyBody,
    attributes: AttributeMap = AttributeMap.empty
  )
}
