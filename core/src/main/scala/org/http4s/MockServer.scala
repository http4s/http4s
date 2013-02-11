package org.http4s

import scala.language.reflectiveCalls

import concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee.{Enumerator, Iteratee}

class MockServer(route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) {
  import MockServer.Response

  def apply(req: RequestHead, enum: Enumerator[Chunk]): Future[Response] = {
    try {
      route.lift(req).fold(Future.successful(onNotFound)) { parser =>
        val it = parser.flatMap { responder =>
          val responseBodyIt: Iteratee[Chunk, Array[Byte]] = Iteratee.consume()
          responder.body.transform(responseBodyIt).map(Response(responder.statusLine, responder.headers, _))
        }
        enum.run(it)
      }
    } catch {
      case t: Throwable => Future.successful(onError(t))
    }
  }

  def onNotFound: MockServer.Response = Response(statusLine = StatusLine.NotFound)

  def onError: PartialFunction[Throwable, Response] = {
    case e: Exception =>
      e.printStackTrace()
      Response(statusLine = StatusLine.InternalServerError)
  }
}

object MockServer {
  case class Response(
    statusLine: StatusLine = StatusLine.Ok,
    headers: Headers = Headers.Empty,
    body: Array[Byte] = Array.empty
  )
}
