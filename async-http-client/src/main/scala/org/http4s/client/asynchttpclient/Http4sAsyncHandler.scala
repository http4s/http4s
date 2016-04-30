package org.http4s
package client
package asynchttpclient

import java.util.concurrent.ExecutorService
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.{ HttpResponseBodyPart, HttpResponseHeaders, HttpResponseStatus }
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.reactivestreams.Publisher
import scalaz.{ -\/, \/- }
import scalaz.concurrent.Task
import scodec.bits.ByteVector

class Http4sAsyncHandler(
  callback: Callback[DisposableResponse],
  bufferSize: Int,
  executorService: ExecutorService)
    extends StreamedAsyncHandler[Unit]
{
  var state: State = State.CONTINUE
  var disposableResponse = DisposableResponse(Response(), Task.delay { state = State.ABORT })

  override def onStream(publisher: Publisher[HttpResponseBodyPart]): State = {
    val subscriber = new QueueSubscriber[HttpResponseBodyPart](bufferSize) {
      override def whenNext(element: HttpResponseBodyPart): Boolean = {
        state match {
          case State.CONTINUE =>
            super.whenNext(element)
          case State.ABORT =>
            super.whenNext(element)
            closeQueue()
            false
          case State.UPGRADE =>
            super.whenNext(element)
            state = State.ABORT
            throw new IllegalStateException("UPGRADE not implemented")
        }
      }

      override protected def request(n: Int): Unit =
        state match {
          case State.CONTINUE =>
            super.request(n)
          case _ =>
            // don't request what we're not going to process
        }
    }
    val body = subscriber.process.map(part => ByteVector(part.getBodyPartBytes))
    val response = disposableResponse.response.copy(body = body)
    execute(callback(\/-(DisposableResponse(response, Task.delay {
      state = State.ABORT
      subscriber.killQueue()
    }))))
    publisher.subscribe(subscriber)
    state
  }

  override def onBodyPartReceived(httpResponseBodyPart: HttpResponseBodyPart): State =
    throw org.http4s.util.bug("Expected it to call onStream instead.")

  override def onStatusReceived(status: HttpResponseStatus): State = {
    disposableResponse = disposableResponse.copy(response = disposableResponse.response.copy(status = getStatus(status)))
    state
  }

  override def onHeadersReceived(headers: HttpResponseHeaders): State = {
    disposableResponse = disposableResponse.copy(
      response = disposableResponse.response.copy(
        headers = getHeaders(headers.getHeaders)))
    state
  }

  override def onThrowable(throwable: Throwable): Unit =
    execute(callback(-\/(throwable)))

  override def onCompleted(): Unit = {}

  private def execute(f: => Unit) =
    executorService.execute(new Runnable {
      override def run(): Unit = f
    })
}
