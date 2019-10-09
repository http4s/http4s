package org.http4s
package client
package asynchttpclient

import cats.effect._
import cats.implicits._
import cats.effect.implicits._
import fs2.Stream._
import fs2._
import fs2.interop.reactivestreams.{StreamSubscriber, StreamUnicastPublisher}
import _root_.io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders}
import _root_.io.netty.buffer.Unpooled
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.asynchttpclient.request.body.generator.{BodyGenerator, ReactiveStreamsBodyGenerator}
import org.asynchttpclient.{Request => AsyncRequest, Response => _, _}
import org.http4s.internal.CollectionCompat.CollectionConverters._
import org.http4s.internal.invokeCallback
import org.http4s.util.threads._
import org.log4s.getLogger
import org.reactivestreams.Publisher
import _root_.io.netty.handler.codec.http.cookie.Cookie
import org.asynchttpclient.uri.Uri
import org.asynchttpclient.cookie.CookieStore

object AsyncHttpClient {
  private[this] val logger = getLogger

  val defaultConfig = new DefaultAsyncHttpClientConfig.Builder()
    .setMaxConnectionsPerHost(200)
    .setMaxConnections(400)
    .setRequestTimeout(defaults.RequestTimeout.toMillis.toInt)
    .setThreadFactory(threadFactory(name = { i =>
      s"http4s-async-http-client-worker-${i}"
    }))
    .setCookieStore(new NoOpCookieStore)
    .build()

  /**
    * Allocates a Client and its shutdown mechanism for freeing resources.
    */
  def allocate[F[_]](config: AsyncHttpClientConfig = defaultConfig)(
      implicit F: ConcurrentEffect[F]): F[(Client[F], F[Unit])] =
    F.delay(new DefaultAsyncHttpClient(config))
      .map(c =>
        (Client[F] { req =>
          Resource(F.async[(Response[F], F[Unit])] { cb =>
            c.executeRequest(toAsyncRequest(req), asyncHandler(cb))
            ()
          })
        }, F.delay(c.close)))

  /**
    * Create an HTTP client based on the AsyncHttpClient library
    *
    * @param config configuration for the client
    * @param ec The ExecutionContext to run responses on
    */
  def resource[F[_]](config: AsyncHttpClientConfig = defaultConfig)(
      implicit F: ConcurrentEffect[F]): Resource[F, Client[F]] =
    Resource(allocate(config))

  /**
    * Create a bracketed HTTP client based on the AsyncHttpClient library.
    *
    * @param config configuration for the client
    * @param ec The ExecutionContext to run responses on
    * @return a singleton stream of the client.  The client will be
    * shutdown when the stream terminates.
    */
  def stream[F[_]](config: AsyncHttpClientConfig = defaultConfig)(
      implicit F: ConcurrentEffect[F]): Stream[F, Client[F]] =
    Stream.resource(resource(config))

  private def asyncHandler[F[_]](cb: Callback[(Response[F], F[Unit])])(
      implicit F: ConcurrentEffect[F]) =
    new StreamedAsyncHandler[Unit] {
      var state: State = State.CONTINUE
      var response: Response[F] = Response()
      val dispose = F.delay { state = State.ABORT }

      override def onStream(publisher: Publisher[HttpResponseBodyPart]): State = {
        // backpressure is handled by requests to the reactive streams subscription
        StreamSubscriber[F, HttpResponseBodyPart]
          .map { subscriber =>
            val body = subscriber.stream.flatMap(part => chunk(Chunk.bytes(part.getBodyPartBytes)))
            response = response.copy(body = body)
            // Run this before we return the response, lest we violate
            // Rule 3.16 of the reactive streams spec.
            publisher.subscribe(subscriber)
            // We have a fully formed response now.  Complete the
            // callback, rather than waiting for onComplete, or else we'll
            // buffer the entire response before we return it for
            // streaming consumption.
            invokeCallback(logger)(cb(Right(response -> dispose)))
          }
          .runAsync(_ => IO.unit)
          .unsafeRunSync()
        state
      }

      override def onBodyPartReceived(httpResponseBodyPart: HttpResponseBodyPart): State =
        throw org.http4s.util.bug("Expected it to call onStream instead.")

      override def onStatusReceived(status: HttpResponseStatus): State = {
        response = response.copy(status = getStatus(status))
        state
      }

      override def onHeadersReceived(headers: HttpHeaders): State = {
        response = response.copy(headers = getHeaders(headers))
        state
      }

      override def onThrowable(throwable: Throwable): Unit =
        invokeCallback(logger)(cb(Left(throwable)))

      override def onCompleted(): Unit = {
        // Don't close here.  onStream may still be being called.
      }
    }

  private def toAsyncRequest[F[_]: ConcurrentEffect](request: Request[F]): AsyncRequest = {
    val headers = new DefaultHttpHeaders
    for (h <- request.headers.toList)
      headers.add(h.name.value, h.value)
    new RequestBuilder(request.method.renderString)
      .setUrl(request.uri.renderString)
      .setHeaders(headers)
      .setBody(getBodyGenerator(request))
      .build()
  }

  private def getBodyGenerator[F[_]: ConcurrentEffect](req: Request[F]): BodyGenerator = {
    val publisher = StreamUnicastPublisher(
      req.body.chunks.map(chunk => Unpooled.wrappedBuffer(chunk.toArray)))
    if (req.isChunked) new ReactiveStreamsBodyGenerator(publisher, -1)
    else
      // As far as I can tell there are three valid options when
      // `req.contentLength.isEmpty`, none are particularly pleasant.
      //
      // 1. We can pass in -1 to the ReactiveStreamsBodyGenerator, this will
      //    cause a 'Transfer-Encoding: chunked' header to be added to the
      //    request headers. This is disadvantageous as this header will not
      //    be reflected in the `req.headers` value, and thus may be
      //    surprising to some users.
      //
      // 2. We can strictly resolve the body and compute the content length
      //    here. The assumption here is that the request was intended to be
      //    non-streaming because otherwise the user would have created the
      //    request in such a way that `isChunked` would have been set to
      //    `true`.
      //
      // 3. Make it impossible for
      //    `_.isChunked == false && // _.contentLength.isEmpty`
      //
      // The final option is the most appealing to me, as this seems most
      // inline with Request messages via the RFC
      // [[https://tools.ietf.org/html/rfc7230#section-3.3.2]], but it may
      // have more far reaching consequences to the existing code.
      //
      // For that reason, for now, we will force chunked encoding as it seems
      // to be the most safe. Servers should already be able to handle chunked
      // encoding and it doesn't risk exploding memory usage.
      new ReactiveStreamsBodyGenerator(publisher, req.contentLength.getOrElse(-1))
  }

  private def getStatus(status: HttpResponseStatus): Status =
    Status.fromInt(status.getStatusCode).valueOr(throw _)

  private def getHeaders(headers: HttpHeaders): Headers =
    Headers(headers.asScala.map { header =>
      Header(header.getKey, header.getValue)
    }.toList)
  private class NoOpCookieStore extends CookieStore {
    val empty: java.util.List[Cookie] = new java.util.ArrayList()
    override def add(uri: Uri, cookie: Cookie): Unit = ()
    override def get(uri: Uri): java.util.List[Cookie] = empty
    override def getAll(): java.util.List[Cookie] = empty
    override def remove(pred: java.util.function.Predicate[Cookie]): Boolean = false
    override def clear(): Boolean = false
  }
}
