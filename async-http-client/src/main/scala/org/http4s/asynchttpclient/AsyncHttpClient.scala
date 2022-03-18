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
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
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
import org.http4s.internal.bug
import org.http4s.internal.threads._
import org.reactivestreams.Publisher

import scala.jdk.CollectionConverters._

@deprecated(
  "Upstream is unmaintained. This backend will be removed in the next milestone. If anyone wants to adopt it, please contact the http4s team.",
  "1.0.0-M32",
)
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

  /** Create a HTTP client with an existing AsyncHttpClient client. The supplied client is NOT
    * closed by this Resource!
    */
  def fromClient[F[_]](httpClient: AsyncHttpClient)(implicit F: Async[F]): Resource[F, Client[F]] =
    Dispatcher[F].flatMap { dispatcher =>
      val client = Client[F] { req =>
        Resource(F.async[(Response[F], F[Unit])] { cb =>
          F.delay(
            httpClient
              .executeRequest(toAsyncRequest(req, dispatcher), asyncHandler(cb, dispatcher))
          ).as(None)
        })
      }

      Resource.eval(F.pure(client))
    }

  /** Allocates a Client and its shutdown mechanism for freeing resources.
    */
  def allocate[F[_]](config: AsyncHttpClientConfig = defaultConfig)(implicit
      F: Async[F]
  ): F[(Client[F], F[Unit])] =
    resource(config).allocated

  /** Create an HTTP client based on the AsyncHttpClient library
    *
    * @param config configuration for the client
    */
  def resource[F[_]](
      config: AsyncHttpClientConfig = defaultConfig
  )(implicit F: Async[F]): Resource[F, Client[F]] =
    Resource.make(F.delay(new DefaultAsyncHttpClient(config)))(c => F.delay(c.close())).flatMap {
      httpClient =>
        fromClient(httpClient)
    }

  /** Create a bracketed HTTP client based on the AsyncHttpClient library.
    *
    * @param config configuration for the client
    * @return a singleton stream of the client.  The client will be
    * shutdown when the stream terminates.
    */
  def stream[F[_]](config: AsyncHttpClientConfig = defaultConfig)(implicit
      F: Async[F]
  ): Stream[F, Client[F]] =
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

  private def asyncHandler[F[_]](cb: Callback[(Response[F], F[Unit])], dispatcher: Dispatcher[F])(
      implicit F: Async[F]
  ) =
    new StreamedAsyncHandler[Unit] {
      var state: State = State.CONTINUE
      var response: Response[F] = Response()
      val dispose: F[Unit] = F.delay { state = State.ABORT }
      val onStreamCalled: Ref[F, Boolean] = Ref.unsafe[F, Boolean](false)
      val deferredThrowable: Deferred[F, Throwable] = Deferred.unsafe[F, Throwable]

      override def onStream(publisher: Publisher[HttpResponseBodyPart]): State = {
        val eff = for {
          _ <- onStreamCalled.set(true)

          subscriber <- StreamSubscriber[F, HttpResponseBodyPart](1)

          subscribeF = F.delay(publisher.subscribe(subscriber))

          bodyDisposal <- Ref.of[F, F[Unit]] {
            subscriber.stream(subscribeF).pull.uncons.void.stream.compile.drain
          }

          body =
            subscriber
              .stream(bodyDisposal.set(F.unit) >> subscribeF)
              .flatMap(part => chunk(Chunk.array(part.getBodyPartBytes)))
              .mergeHaltBoth(Stream.eval(deferredThrowable.get.flatMap(F.raiseError[Byte])))

          responseWithBody = response.copy(entity = Entity(body))

          _ <-
            invokeCallbackF[F](cb(Right(responseWithBody -> (dispose >> bodyDisposal.get.flatten))))
        } yield ()

        dispatcher.unsafeRunSync(eff)

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

      override def onThrowable(throwable: Throwable): Unit = {
        val fa = onStreamCalled.get
          .ifM(
            ifTrue = deferredThrowable.complete(throwable).void,
            ifFalse = invokeCallbackF(cb(Left(throwable))),
          )

        dispatcher.unsafeRunSync(fa)
      }

      override def onCompleted(): Unit = {
        val fa = onStreamCalled.get
          .ifM(ifTrue = F.unit, ifFalse = invokeCallbackF[F](cb(Right(response -> dispose))))

        dispatcher.unsafeRunSync(fa)
      }
    }

  // use fibers to ensure that we get off of the AHC thread pool
  private def invokeCallbackF[F[_]](invoked: => Unit)(implicit F: Async[F]): F[Unit] =
    F.start(F.delay(invoked)).flatMap(_.joinWithNever)

  private def toAsyncRequest[F[_]: Async](
      request: Request[F],
      dispatcher: Dispatcher[F],
  ): AsyncRequest = {
    val headers = new DefaultHttpHeaders
    for (h <- request.headers.headers)
      headers.add(h.name.toString, h.value)
    new RequestBuilder(request.method.renderString)
      .setUrl(request.uri.renderString)
      .setHeaders(headers)
      .setBody(getBodyGenerator(request, dispatcher))
      .build()
  }

  private def getBodyGenerator[F[_]: Async](
      req: Request[F],
      dispatcher: Dispatcher[F],
  ): BodyGenerator = {
    val publisher = StreamUnicastPublisher(
      req.body.chunks.map(chunk => Unpooled.wrappedBuffer(chunk.toArray)),
      dispatcher,
    )
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
