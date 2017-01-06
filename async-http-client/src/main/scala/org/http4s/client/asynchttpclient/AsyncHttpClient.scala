package org.http4s
package client
package asynchttpclient

import cats._
import fs2._
import fs2.Stream._
import fs2.io.toInputStream
import org.http4s.batteries._

import java.util.concurrent.{Callable, Executors, ExecutorService}

import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.request.body.generator.{InputStreamBodyGenerator, BodyGenerator}
import org.asynchttpclient.{Request => AsyncRequest, Response => _, _}
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.http4s.client.impl.DefaultExecutor

import org.http4s.util.bug
import org.http4s.util.threads._

import org.reactivestreams.Publisher

import scala.collection.JavaConverters._

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
    val executorService = customExecutor.getOrElse(DefaultExecutor.newClientDefaultExecutorService("async-http-client-response"))
    implicit val strategy = Strategy.fromExecutor(executorService)
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

  private def asyncHandler(callback: Callback[DisposableResponse],
                           bufferSize: Int,
                           executorService: ExecutorService): AsyncHandler[Unit] =
    new StreamedAsyncHandler[Unit] {
      var state: State = State.CONTINUE
      var disposableResponse = DisposableResponse(Response(), Task.delay { state = State.ABORT })
      implicit val strategy = Strategy.fromExecutor(executorService)
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
        val body = subscriber.process.flatMap(part => chunk(Chunk.bytes(part.getBodyPartBytes)))
        val response = disposableResponse.response.copy(body = body)
        execute(callback(right(DisposableResponse(response, Task.delay {
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
        disposableResponse = disposableResponse.copy(response = disposableResponse.response.copy(headers = getHeaders(headers)))
        state
      }

      override def onThrowable(throwable: Throwable): Unit =
        execute(callback(left(throwable)))

      override def onCompleted(): Unit = {}

      private def execute(f: => Unit) =
        executorService.execute(new Runnable {
          override def run(): Unit = f
        })
    }

  private def toAsyncRequest(request: Request)(implicit S: Strategy): AsyncRequest =
    new RequestBuilder(request.method.toString)
      .setUrl(request.uri.toString)
      .setHeaders(request.headers
        .groupBy(_.name.toString)
        .mapValues(_.map(_.value).asJavaCollection)
        .asJava
      ).setBody(getBodyGenerator(request.body))
      .build()

  private def getBodyGenerator(body: EntityBody)(implicit S: Strategy): BodyGenerator = {
    val is = body.through(toInputStream).runLast
      .unsafeRun // TODO fs2 port does this have to be synchronous?
      .getOrElse(throw bug("expected an InputStream"))
    new InputStreamBodyGenerator(is)
  }

  private def getStatus(status: HttpResponseStatus): Status =
    Status.fromInt(status.getStatusCode).valueOr(throw _)

  private def getHeaders(headers: HttpResponseHeaders): Headers = {
    Headers(headers.getHeaders.iterator.asScala.map { header =>
      Header(header.getKey, header.getValue)
    }.toList)
  }
}
