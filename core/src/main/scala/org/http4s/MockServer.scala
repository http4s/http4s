package org.http4s

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee.Enumerator

class MockServer(route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) {
  def apply(req: Request, body: MessageBody = MessageBody.Empty): Future[Response] = {
    try {
      route.lift(req).fold(Future.successful(onNotFound)) {
        handler => body.enumerate.run(handler)
      }
    } catch {
      case t: Throwable => Future.successful(onError(t))
    }
  }

  def onNotFound: Response = Response(statusLine = StatusLine.NotFound)

  def onError: PartialFunction[Throwable, Response] = {
    case e: Exception => Response(statusLine = StatusLine.InternalServerError)
  }
}
