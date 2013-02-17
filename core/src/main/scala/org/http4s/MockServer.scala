package org.http4s

import scala.language.reflectiveCalls

import concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}

class MockServer(route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) {
  import MockServer.Response

  def apply(req: RequestPrelude, enum: Enumerator[Raw]): Future[Response] = {
    try {
      route.lift(req).fold(Future.successful(onNotFound)) { parser =>
        val it: Iteratee[HttpChunk, Response] = parser.flatMap { responder =>
          val responseBodyIt: Iteratee[Raw,Raw] = Iteratee.consume()
          // I'm not sure why we are compelled to make this complicated looking...
          responder.body ><> Enumeratee.map[HttpChunk](_.bytes) &>> responseBodyIt map{ bytes: Array[Byte] =>
            Response(responder.prelude.status, responder.prelude.headers, body = bytes) }
        }
        (enum &> Enumeratee.map[Raw]((i => HttpEntity(i)): Raw=>HttpChunk)).run(it)
      }
    } catch {
      case t: Throwable => Future.successful(onError(t))
    }
  }

  def onNotFound: MockServer.Response = Response(statusLine = Status.NotFound)

  def onError: PartialFunction[Throwable, Response] = {
    case e: Exception =>
      e.printStackTrace()
      Response(statusLine = Status.InternalServerError)
  }
}

object MockServer {
  case class Response(
    statusLine: Status = Status.Ok,
    headers: Headers = Headers.Empty,
    body: Array[Byte] = Array.empty
  )
}
