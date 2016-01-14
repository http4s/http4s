package org.http4s.client.asynchttpclient

import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client.generators.InputStreamBodyGenerator
import com.ning.http.client.{Request => AsyncRequest, Response => AsyncResponse, _}

import org.http4s._
import org.http4s.client._
import org.http4s.util.task
import scodec.bits.ByteVector

import scala.collection.JavaConverters._

import scalaz.stream.io._
import scalaz.stream.async
import scalaz.concurrent.Task

import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global

object AsyncHttpClient {
  val defaultConfig = new AsyncHttpClientConfig.Builder()
    .setMaxConnectionsPerHost(200)
    .setMaxConnections(400)
    .setRequestTimeout(30000)
    .build()

  def apply(config: AsyncHttpClientConfig = defaultConfig): Client = {
    val client = new AsyncHttpClient(config)
    Client(Service.lift { req =>
      val p = Promise[DisposableResponse]
      client.executeRequest(toAsyncRequest(req), asyncHandler(p))
      task.futureToTask(p.future)
    }, Task(client.close()))
  }

  private def asyncHandler(promise: Promise[DisposableResponse]): AsyncHandler[Unit] =
    new AsyncHandler[Unit] {
      var state: AsyncHandler.STATE = STATE.CONTINUE
      val queue = async.unboundedQueue[ByteVector]
      val body: EntityBody = queue.dequeue

      var disposableResponse = DisposableResponse(Response(body = body), Task {
        state = STATE.ABORT
        queue.close.run
      })

      override def onBodyPartReceived(httpResponseBodyPart: HttpResponseBodyPart): STATE = {
        queue.enqueueOne(ByteVector(httpResponseBodyPart.getBodyPartBytes)).run
        state
      }

      override def onStatusReceived(status: HttpResponseStatus): STATE = {
        disposableResponse = disposableResponse.copy(response = disposableResponse.response.copy(status = getStatus(status)))
        state
      }

      override def onHeadersReceived(headers: HttpResponseHeaders): STATE = {
        disposableResponse = disposableResponse.copy(response = disposableResponse.response.copy(headers = getHeaders(headers)))
        promise.success(disposableResponse)
        state
      }

      override def onThrowable(throwable: Throwable): Unit = {
        promise.failure(throwable)
        queue.close.run
      }

      override def onCompleted() {
        queue.close.run
      }
    }

  private def toAsyncRequest(request: Request): AsyncRequest =
    new RequestBuilder(request.method.toString)
      .setUrl(request.uri.toString)
      .setHeaders(request.headers
        .groupBy(_.name.toString)
        .mapValues(_.map(_.value).asJavaCollection)
        .asJava
      ).setBody(getBodyGenerator(request.body))
      .build()

  private def getBodyGenerator(body: EntityBody): BodyGenerator =
    new InputStreamBodyGenerator(toInputStream(body))

  private def getStatus(status: HttpResponseStatus): Status =
    Status.fromInt(status.getStatusCode).valueOr(e => throw new ParseException(e))

  private def getHeaders(headers: HttpResponseHeaders): Headers = {
    Headers(headers.getHeaders.entrySet().asScala.flatMap(entry => {
      entry.getValue.asScala.map(v => Header(entry.getKey, v)).toList
    }).toList)
  }
}
