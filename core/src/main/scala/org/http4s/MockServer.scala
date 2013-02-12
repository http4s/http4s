package org.http4s

import scala.language.reflectiveCalls

import concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee.{Done, Cont, Iteratee}
import play.api.libs.iteratee.Input

class MockServer(route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) {
  import MockServer.Response

  def apply(req: Request[Raw]): Future[Response] = {
    try {
      route.lift(req).fold(Future.successful(onNotFound)) {
        responder => responder.flatMap(render).recover(onError)
      }

    } catch {
      case t: Throwable => Future.successful(onError(t))
    }
  }

  def render(responder: Responder[HttpChunk]): Future[Response] = {

    val it: Iteratee[HttpChunk,(Raw, Headers)] = {
      def step(result: List[Array[Byte]])(in: Input[HttpChunk]): Iteratee[HttpChunk,(Raw, Headers)] = {
        in match {
          case Input.El(HttpEntity(data)) => Cont( i => step(data::result)(i))
          case Input.El(HttpTrailer(trailer)) => Done((result.reverse.toArray.flatten, trailer))
          case Input.EOF => Done((result.reverse.toArray.flatten , Headers.Empty))
          case Input.El(_) => sys.error("Multipart or file not implemented yet") // TODO: make this real
        }
      }
      Cont{ i => step(Nil)(i)}
    }


    responder.body.run(it).map { case (body,trailer) =>
      Response(statusLine = responder.statusLine, headers = responder.headers, body = body)
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
