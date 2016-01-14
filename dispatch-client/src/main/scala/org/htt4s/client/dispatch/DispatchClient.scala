package org.htt4s.client.dispatch

import dispatch._, Defaults._
import com.ning.http.client.{Response => DResponse}

import org.http4s._
import org.http4s.client._

import scala.collection.JavaConverters._
import scalaz.concurrent.Task

import scalaz._
import Scalaz._

object DispatchClient {
  def apply(): Client = {
    Client(Service.lift { req =>
      Task.async[DResponse] { register =>
        Http(toDRequest(req)).onComplete {
          case scala.util.Success(res) => register(res.right)
          case scala.util.Failure(ex) => register(ex.left)
        }
      }.flatMap(fromDResponse)
    }, Task.now(()))
  }

  private def toDRequest(request: Request): Req = {
    url(request.uri.toString)
      .setMethod(request.method.toString)
      .setHeaders(request.headers
        .groupBy(_.name.toString)
        .mapValues(_.map(_.value).toSeq)
      )
      .setBody(request.bodyAsText.runFoldMap(identity).run)
  }

  private def fromDResponse(response: DResponse): Task[DisposableResponse] = {
    Response()
      .withStatus(Status.fromInt(response.getStatusCode).valueOr(e => throw new ParseException(e)))
      .withTrailerHeaders(Task.now(Headers(getHeaders(response))))
      .withBody(response.getResponseBody)
      .map(response => DisposableResponse(response, Task.now(())))
  }

  private def getHeaders(response: DResponse): List[Header] = {
    response.getHeaders.entrySet().asScala.flatMap(entry => {
      entry.getValue.asScala.map(v => Header(entry.getKey, v)).toList
    }).toList
  }
}
