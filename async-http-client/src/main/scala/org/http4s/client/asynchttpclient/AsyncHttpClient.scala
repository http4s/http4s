package org.http4s
package client
package asynchttpclient

import cats.implicits._
import fs2._
import fs2.Stream._
import fs2.interop.reactivestreams.{StreamSubscriber, StreamUnicastPublisher}

import java.nio.ByteBuffer

import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.request.body.generator.{BodyGenerator, ByteArrayBodyGenerator, ReactiveStreamsBodyGenerator}
import org.asynchttpclient.{Request => AsyncRequest, Response => _, _}
import org.asynchttpclient.handler.StreamedAsyncHandler

import org.http4s.util.threads._

import org.reactivestreams.Publisher

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

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
    * @param ec The ExecutionContext to run responses on
    */
  def apply(config: AsyncHttpClientConfig = defaultConfig, bufferSize: Int = 8)
           (implicit ec: ExecutionContext): Client = {
    val client = new DefaultAsyncHttpClient(config)
    implicit val strategy = Strategy.fromExecutionContext(ec)
    Client(Service.lift { req =>
      Task.async[DisposableResponse] { cb =>
        client.executeRequest(toAsyncRequest(req), asyncHandler(cb, bufferSize))
        ()
      }
    }, Task.delay(client.close()))
  }

  private def asyncHandler(cb: Callback[DisposableResponse], bufferSize: Int)(implicit S: Strategy) =
    new StreamedAsyncHandler[Unit] {
      var state: State = State.CONTINUE
      var dr: DisposableResponse = DisposableResponse(Response(), Task.delay(state = State.ABORT))

      override def onStream(publisher: Publisher[HttpResponseBodyPart]): State = {
        // backpressure is handled by requests to the reactive streams subscription
        StreamSubscriber[Task, HttpResponseBodyPart]().map { subscriber =>
          val body = subscriber.stream.flatMap(part => chunk(Chunk.bytes(part.getBodyPartBytes)))
          dr = dr.copy(
            response = dr.response.copy(body = body),
            dispose = Task.delay(state = State.ABORT)
          )
          // Run this before we return the response, lest we violate
          // Rule 3.16 of the reactive streams spec.
          publisher.subscribe(subscriber)
          // We have a fully formed response now.  Complete the
          // callback, rather than waiting for onComplete, or else we'll
          // buffer the entire response before we return it for
          // streaming consumption.
          S(cb(Right(dr)))
          state
        }.unsafeRun
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
        S(cb(Left(throwable)))

      override def onCompleted(): Unit = {
        // Don't close here.  onStream may still be being called.
      }
    }

  private def toAsyncRequest(request: Request)(implicit S: Strategy): AsyncRequest =
    new RequestBuilder(request.method.toString)
      .setUrl(request.uri.toString)
      .setHeaders(request.headers
        .groupBy(_.name.toString)
        .mapValues(_.map(_.value).asJavaCollection)
        .asJava
      )
      .setBody(getBodyGenerator(request))
      .build()

  private def getBodyGenerator(req: Request)(implicit S: Strategy): BodyGenerator = {
    val publisher = StreamUnicastPublisher(req.body.chunks.map(chunk => ByteBuffer.wrap(chunk.toArray)))
    if (req.isChunked) new ReactiveStreamsBodyGenerator(publisher, -1)
    else req.contentLength match {
      case Some(len) => new ReactiveStreamsBodyGenerator(publisher, len)
      case None => new ByteArrayBodyGenerator(Array.empty)
    }
  }

  private def getStatus(status: HttpResponseStatus): Status =
    Status.fromInt(status.getStatusCode).valueOr(throw _)

  private def getHeaders(headers: HttpResponseHeaders): Headers = {
    Headers(headers.getHeaders.iterator.asScala.map { header =>
      Header(header.getKey, header.getValue)
    }.toList)
  }
}
