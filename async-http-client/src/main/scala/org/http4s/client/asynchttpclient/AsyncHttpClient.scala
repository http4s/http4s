package org.http4s
package client
package asynchttpclient

import java.nio.ByteBuffer

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2.Stream._
import fs2._
import fs2.interop.reactivestreams.{StreamSubscriber, StreamUnicastPublisher}
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.asynchttpclient.request.body.generator.{BodyGenerator, ByteArrayBodyGenerator, ReactiveStreamsBodyGenerator}
import org.asynchttpclient.{Request => AsyncRequest, Response => _, _}
import org.http4s.util.threads._
import org.reactivestreams.Publisher

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object AsyncHttpClient {

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
  def apply[F[_]](config: AsyncHttpClientConfig = defaultConfig, bufferSize: Int = 8)
           (implicit F: Effect[F], ec: ExecutionContext): Client[F] = {
    val client = new DefaultAsyncHttpClient(config)
    Client(Service.lift { req: Request[F] =>
      F.async[DisposableResponse[F]] { cb =>
        client.executeRequest(toAsyncRequest(req), asyncHandler(cb, bufferSize))
        ()
      }
    }, F.delay(client.close()))
  }

  private def asyncHandler[F[_]](cb: Callback[DisposableResponse[F]], bufferSize: Int)
                                (implicit F: Effect[F], ec: ExecutionContext) =
    new StreamedAsyncHandler[Unit] {
      var state: State = State.CONTINUE
      var dr: DisposableResponse[F] = DisposableResponse[F](Response(), F.delay(state = State.ABORT))

      override def onStream(publisher: Publisher[HttpResponseBodyPart]): State = {
        // backpressure is handled by requests to the reactive streams subscription
        StreamSubscriber[F, HttpResponseBodyPart]().map { subscriber =>
          val body = subscriber.stream.flatMap(part => chunk(Chunk.bytes(part.getBodyPartBytes)))
          dr = dr.copy(
            response = dr.response.copy(body = body),
            dispose = F.delay(state = State.ABORT)
          )
          // Run this before we return the response, lest we violate
          // Rule 3.16 of the reactive streams spec.
          publisher.subscribe(subscriber)
          // We have a fully formed response now.  Complete the
          // callback, rather than waiting for onComplete, or else we'll
          // buffer the entire response before we return it for
          // streaming consumption.
          ec.execute(new Runnable { def run(): Unit = cb(Right(dr)) })
        }.runAsync(_ => IO.unit).unsafeRunSync
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
        ec.execute(new Runnable { def run(): Unit = cb(Left(throwable)) })

      override def onCompleted(): Unit = {
        // Don't close here.  onStream may still be being called.
      }
    }

  private def toAsyncRequest[F[_]: Effect](request: Request[F])(implicit ec: ExecutionContext): AsyncRequest =
    new RequestBuilder(request.method.toString)
      .setUrl(request.uri.toString)
      .setHeaders(request.headers
        .groupBy(_.name.toString)
        .mapValues(_.map(_.value).asJavaCollection)
        .asJava
      )
      .setBody(getBodyGenerator(request))
      .build()

  private def getBodyGenerator[F[_]: Effect](req: Request[F])(implicit ec: ExecutionContext): BodyGenerator = {
    val publisher = StreamUnicastPublisher(req.body.chunks.map(chunk => ByteBuffer.wrap(chunk.toArray)))
    if (req.isChunked) new ReactiveStreamsBodyGenerator(publisher, -1)
    else req.contentLength match {
      case Some(len) => new ReactiveStreamsBodyGenerator(publisher, len)
      case None => new ByteArrayBodyGenerator(Array.empty)
    }
  }

  private def getStatus(status: HttpResponseStatus): Status =
    Status.fromInt(status.getStatusCode).valueOr(throw _)

  private def getHeaders(headers: HttpResponseHeaders): Headers =
    Headers(headers.getHeaders.iterator.asScala.map { header =>
      Header(header.getKey, header.getValue)
    }.toList)
}
