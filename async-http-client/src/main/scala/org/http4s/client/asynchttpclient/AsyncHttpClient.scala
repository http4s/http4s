package org.http4s
package client
package asynchttpclient

import java.util.concurrent.{Callable, Executor, Executors, ExecutorService}

import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.request.body.generator.{InputStreamBodyGenerator, BodyGenerator}
import org.asynchttpclient.{Request => AsyncRequest, Response => _, _}
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.reactivestreams.Subscription

import org.http4s.util.threads._

import org.reactivestreams.Publisher
import scodec.bits.ByteVector

import scala.collection.JavaConverters._

import scalaz.{\/, -\/, \/-}
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.async.unboundedQueue
import scalaz.stream.io._

import org.log4s.getLogger

object AsyncHttpClient {
  private[this] val log = getLogger

  val defaultConfig = new DefaultAsyncHttpClientConfig.Builder()
    .setMaxConnectionsPerHost(200)
    .setMaxConnections(400)
    .setRequestTimeout(30000)
    .setThreadFactory(threadFactory(name = { i => s"http4s-async-http-client-worker-${i}" }))
    .build()

  /**
    * Create an HTTP client based on the AsyncHttpClient library
    *
    * @param config configuration for the client
    * @param bufferSize body chunks to buffer when reading the body; defaults to 8
    * @param customExecutor custom executor which must be managed externally.
    */
  def apply(config: AsyncHttpClientConfig = defaultConfig,
            bufferSize: Int = 8,
            customExecutor: Option[ExecutorService] = None): Client = {
    val client = new DefaultAsyncHttpClient(config)
    val executorService = customExecutor.getOrElse(newDaemonPool("http4s-async-http-client-response"))
    val close =
      if (customExecutor.isDefined)
        Task.delay { client.close() }
      else
        Task.delay {
          client.close()
          executorService.shutdown()
        }

    Client(Service.lift { req =>
      Task.async[DisposableResponse] { cb =>
        client.executeRequest(toAsyncRequest(req), asyncHandler(cb, bufferSize, executorService))
        ()
      }
    }, close)
  }

  private def asyncHandler(cb: Callback[DisposableResponse], bufferSize: Int, executorService: ExecutorService) =
    new StreamedAsyncHandler[Unit] {
      var state: State = State.CONTINUE
      var dr: DisposableResponse = DisposableResponse(Response(), Task.delay(state = State.ABORT))

      override def onStream(publisher: Publisher[HttpResponseBodyPart]): State = {
        // backpressure is handled by requests to the reactive streams subscription
        val queue = unboundedQueue[HttpResponseBodyPart](Strategy.Sequential)
        val subscriber = new QueueSubscriber[HttpResponseBodyPart](bufferSize, queue) {
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

          override protected def request(n: Int): Unit = {
            state match {
              case State.CONTINUE =>
                super.request(n)
              case _ =>
                // don't request what we're not going to process
            }
          }
        }

        val body = subscriber.process.map(part => ByteVector(part.getBodyPartBytes))
        dr = dr.copy(
          response = dr.response.copy(body = body),
          dispose = Task.apply({
            state = State.ABORT
            subscriber.killQueue()
          })(executorService)
        )
        // Run this before we return the response, lest we violate
        // Rule 3.16 of the reactive streams spec.
        publisher.subscribe(subscriber)
        // We have a fully formed response now.  Complete the
        // callback, rather than waiting for onComplete, or else we'll
        // buffer the entire response before we return it for
        // streaming consumption.
        executorService.execute(new Runnable { def run = cb(\/-(dr)) })
        state
      }

      override def onBodyPartReceived(httpResponseBodyPart: HttpResponseBodyPart): State =
        throw org.http4s.util.bug("Expected it to call onStream instead.")

      override def onStatusReceived(status: HttpResponseStatus): State = {
        dr = dr.copy(response = dr.response.copy(status = getStatus(status)))
        state
      }

      override def onHeadersReceived(headers: HttpResponseHeaders): State = {
        dr = dr.copy(response = dr.response.copy(headers = getHeaders(headers)))
        state
      }

      override def onThrowable(throwable: Throwable): Unit =
        executorService.execute(new Runnable { def run = cb(-\/(throwable)) })

      override def onCompleted(): Unit = {
        // Don't close here.  onStream may still be being called.
      }
    }

  private def toAsyncRequest(request: Request): AsyncRequest =
    new RequestBuilder(request.method.toString)
      .setUrl(request.uri.toString)
      .setHeaders(request.headers
        .groupBy(_.name.toString)
        .mapValues(_.map(_.value.toString).asJavaCollection)
        .asJava
      ).setBody(getBodyGenerator(request))
      .build()

  private def getBodyGenerator(req: Request): BodyGenerator = {
    val len = req.contentLength match {
      case Some(len) => len
      case _ if req.isChunked => -1L
      case _ => 0L
    }
    new InputStreamBodyGenerator(toInputStream(req.body), len)
  }

  private def getStatus(status: HttpResponseStatus): Status =
    Status.fromInt(status.getStatusCode).valueOr(throw _)

  private def getHeaders(headers: HttpResponseHeaders): Headers = {
    Headers(headers.getHeaders.iterator.asScala.map { header =>
      Header(FieldName.unsafeFromString(header.getKey), FieldValue.unsafeFromString(header.getValue))
    }.toList)
  }
}
