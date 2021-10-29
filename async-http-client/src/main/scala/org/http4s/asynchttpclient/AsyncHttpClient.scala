/*
 * Copyright 2016 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package asynchttpclient
package client

import _root_.io.netty.buffer.Unpooled
import _root_.io.netty.handler.codec.http.DefaultHttpHeaders
import _root_.io.netty.handler.codec.http.HttpHeaders
import cats.effect._
import cats.effect.concurrent._
import cats.effect.implicits._
import cats.syntax.all._
import fs2.Stream._
import fs2._
import fs2.interop.reactivestreams.StreamSubscriber
import fs2.interop.reactivestreams.StreamUnicastPublisher
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.asynchttpclient.request.body.generator.BodyGenerator
import org.asynchttpclient.request.body.generator.ReactiveStreamsBodyGenerator
import org.asynchttpclient.{Request => AsyncRequest, Response => _, _}
import org.http4s.client.Client
import org.http4s.client.defaults
import org.http4s.internal.CollectionCompat.CollectionConverters._
import org.http4s.internal.bug
import org.http4s.internal.threads._
import org.reactivestreams.Publisher

object AsyncHttpClient {
  val defaultConfig: DefaultAsyncHttpClientConfig = new DefaultAsyncHttpClientConfig.Builder()
    .setMaxConnectionsPerHost(200)
    .setMaxConnections(400)
    .setRequestTimeout(defaults.RequestTimeout.toMillis.toInt)
    .setThreadFactory(threadFactory(name = { i =>
      s"http4s-async-http-client-worker-$i"
    }))
    .setCookieStore(new NoOpCookieStore)
    .build()

  def apply[F[_]](httpClient: AsyncHttpClient)(implicit F: ConcurrentEffect[F]): Client[F] =
    Client[F] { req =>
      Resource(F.async[(Response[F], F[Unit])] { cb =>
        httpClient.executeRequest(toAsyncRequest(req), asyncHandler(cb))
        ()
      })
    }

  /** Allocates a Client and its shutdown mechanism for freeing resources.
    */
  def allocate[F[_]](config: AsyncHttpClientConfig = defaultConfig)(implicit
      F: ConcurrentEffect[F]): F[(Client[F], F[Unit])] =
    F.delay(new DefaultAsyncHttpClient(config))
      .map(c => (apply(c), F.delay(c.close())))

  /** Create an HTTP client based on the AsyncHttpClient library
    *
    * @param config configuration for the client
    */
  def resource[F[_]](config: AsyncHttpClientConfig = defaultConfig)(implicit
      F: ConcurrentEffect[F]): Resource[F, Client[F]] =
    Resource(allocate(config))

  /** Create a bracketed HTTP client based on the AsyncHttpClient library.
    *
    * @param config configuration for the client
    * @return a singleton stream of the client.  The client will be
    * shutdown when the stream terminates.
    */
  def stream[F[_]](config: AsyncHttpClientConfig = defaultConfig)(implicit
      F: ConcurrentEffect[F]): Stream[F, Client[F]] =
    Stream.resource(resource(config))

  /** Create a custom AsyncHttpClientConfig
    *
    * @param configurationFn function that maps from the builder of the defaultConfig to the custom config's builder
    * @return a custom configuration.
    */
  def configure(
      configurationFn: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder
  ): AsyncHttpClientConfig = {
    val defaultConfigBuilder = new DefaultAsyncHttpClientConfig.Builder(defaultConfig)
    configurationFn(defaultConfigBuilder).build()
  }

  private def asyncHandler[F[_]](cb: Callback[(Response[F], F[Unit])])(implicit
      F: ConcurrentEffect[F]) =
    new StreamedAsyncHandler[Unit] {
      var state: State = State.CONTINUE
      var response: Response[F] = Response()
      val dispose: F[Unit] = F.delay { state = State.ABORT }
      val onStreamCalled: Ref[F, Boolean] = Ref.unsafe[F, Boolean](false)
      val deferredThrowable: Deferred[F, Throwable] = Deferred.unsafe[F, Throwable]

      override def onStream(publisher: Publisher[HttpResponseBodyPart]): State = {
        val eff = for {
          _ <- onStreamCalled.set(true)

          subscriber <- StreamSubscriber[F, HttpResponseBodyPart]

          subscribeF = F.delay(publisher.subscribe(subscriber))

          bodyDisposal <- Ref.of[F, F[Unit]] {
            subscriber.stream(subscribeF).pull.uncons.void.stream.compile.drain
          }

          body =
            subscriber
              .stream(bodyDisposal.set(F.unit) >> subscribeF)
              .flatMap(part => chunk(Chunk.bytes(part.getBodyPartBytes)))
              .mergeHaltBoth(Stream.eval(deferredThrowable.get.flatMap(F.raiseError[Byte])))

          responseWithBody = response.copy(body = body)

          _ <-
            invokeCallbackF[F](cb(Right(responseWithBody -> (dispose >> bodyDisposal.get.flatten))))
        } yield ()

        eff.runAsync(_ => IO.unit).unsafeRunSync()

        state
      }

      override def onBodyPartReceived(httpResponseBodyPart: HttpResponseBodyPart): State =
        throw bug("Expected it to call onStream instead.")

      override def onStatusReceived(status: HttpResponseStatus): State = {
        response = response.copy(status = getStatus(status))
        state
      }

      override def onHeadersReceived(headers: HttpHeaders): State = {
        response = response.copy(headers = getHeaders(headers))
        state
      }

      override def onThrowable(throwable: Throwable): Unit =
        onStreamCalled.get
          .ifM(
            ifTrue = deferredThrowable.complete(throwable),
            ifFalse = invokeCallbackF(cb(Left(throwable))))
          .runAsync(_ => IO.unit)
          .unsafeRunSync()

      override def onCompleted(): Unit =
        onStreamCalled.get
          .ifM(ifTrue = F.unit, ifFalse = invokeCallbackF[F](cb(Right(response -> dispose))))
          .runAsync(_ => IO.unit)
          .unsafeRunSync()
    }

  // use fibers to access the ContextShift and ensure that we get off of the AHC thread pool
  private def invokeCallbackF[F[_]](invoked: => Unit)(implicit F: Concurrent[F]): F[Unit] =
    F.start(F.delay(invoked)).flatMap(_.join)

  private def toAsyncRequest[F[_]: ConcurrentEffect](request: Request[F]): AsyncRequest = {
    val headers = new DefaultHttpHeaders
    for (h <- request.headers.headers)
      headers.add(h.name.toString, h.value)
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
      req.contentLength match {
        case Some(len) => new ReactiveStreamsBodyGenerator(publisher, len)
        case None => EmptyBodyGenerator
      }
  }

  private def getStatus(status: HttpResponseStatus): Status =
    Status.fromInt(status.getStatusCode).valueOr(throw _)

  private def getHeaders(headers: HttpHeaders): Headers =
    Headers(headers.asScala.map { header =>
      header.getKey -> header.getValue
    }.toList)
}
