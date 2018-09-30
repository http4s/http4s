package org.http4s
package client
package asynchttpclient

import cats.effect._
import cats.implicits.{catsSyntaxEither => _, _}
import cats.effect.implicits._
import fs2._
import fs2.Stream._
import fs2.concurrent.Queue
import fs2.interop.reactivestreams.{StreamSubscriber, StreamUnicastPublisher}
import _root_.io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders}
import _root_.io.netty.buffer.Unpooled
import _root_.io.netty.util.concurrent.{Future => NFuture, GenericFutureListener}
import java.util.concurrent.{ExecutionException}
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.asynchttpclient.request.body.generator.{BodyGenerator, ReactiveStreamsBodyGenerator}
import org.asynchttpclient.{Request => AsyncRequest, Response => _, _}
import org.asynchttpclient.ws.{WebSocket => AhcWebSocket, WebSocketListener, WebSocketUpgradeHandler}
import org.http4s.internal.{invoke, invokeCallback}
import org.http4s.util.threads._
import org.http4s.websocket.WebsocketBits._
import org.log4s.getLogger
import org.reactivestreams.Publisher
import scala.collection.JavaConverters._

object AsyncHttpClient {
  private[this] val logger = getLogger

  val defaultConfig = new DefaultAsyncHttpClientConfig.Builder()
    .setMaxConnectionsPerHost(200)
    .setMaxConnections(400)
    .setRequestTimeout(30000)
    .setThreadFactory(threadFactory(name = { i =>
      s"http4s-async-http-client-worker-${i}"
    }))
    .build()

  /**
    * Create an HTTP client based on the AsyncHttpClient library
    *
    * @param config configuration for the client
    * @param ec The ExecutionContext to run responses on
    */
  def resource[F[_]](config: AsyncHttpClientConfig = defaultConfig)(
      implicit F: ConcurrentEffect[F]): Resource[F, Client[F]] =
    Resource
      .make(F.delay(new DefaultAsyncHttpClient(config)))(c => F.delay(c.close()))
      .map(client =>
        Client[F] { req =>
          Resource(F.async[(Response[F], F[Unit])] { cb =>
            client.executeRequest(toAsyncRequest(req), asyncHandler(cb))
            ()
          })
      })

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
    for (h <- request.headers)
      headers.add(h.name.toString, h.value)
    new RequestBuilder(request.method.toString)
      .setUrl(request.uri.toString)
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
      Header(header.getKey, header.getValue)
    }.toList)

  def webSocketResource[F[_]](config: AsyncHttpClientConfig = defaultConfig)(
      implicit F: ConcurrentEffect[F]): Resource[F, WebSocketClient[F]] =
    Resource
      .make(F.delay(new DefaultAsyncHttpClient(config)))(c => F.delay(c.close()))
      .map(client =>
        WebSocketClient[F] { req =>
          for {
            receiveQueue <- Queue.unbounded[F, WebSocketFrame]
            socket <- F.async[WebSocketClient.Socket[F]] { cb =>
              client.prepareGet(req.uri.renderString).execute(
                new WebSocketUpgradeHandler(List(wsListener(cb, receiveQueue)).asJava))
              ()
            }
          } yield socket
        }
      )

  private def wsListener[F[_]](cb: Callback[WebSocketClient.Socket[F]], receiveQueue: Queue[F, WebSocketFrame])(      implicit F: ConcurrentEffect[F]): WebSocketListener =
    new WebSocketListener {
      def send(socket: AhcWebSocket): Sink[F, WebSocketFrame] = Sink {
        case Text(s, last) => fromNettyFuture(socket.sendTextFrame(s, last, 0)).void
        case Binary(data, last) => fromNettyFuture(socket.sendBinaryFrame(data, last, 0)).void
        case Continuation(data, last) => fromNettyFuture(socket.sendContinuationFrame(data, last, 0)).void
        case Ping(data) => fromNettyFuture(socket.sendPingFrame(data)).void
        case Pong(data) => fromNettyFuture(socket.sendPongFrame(data)).void
        case close: Close =>
          // TODO extract reason from close frame
          fromNettyFuture(socket.sendCloseFrame(close.closeCode, "")).void
      }

      def onOpen(ahcSocket: AhcWebSocket): Unit = {
        invokeCallback(logger)(cb(Right(WebSocketClient.Socket(send(ahcSocket), receiveQueue.dequeue))))
      }

      def enqueue(frame: WebSocketFrame): Unit =
        invoke(logger)(receiveQueue.enqueue1(frame))

      def onClose(ahcSocket: AhcWebSocket, code: Int, reason: String) =
        Close(code, reason).foreach(enqueue)

      def onError(t: Throwable) =
        cb(Left(t))

      override def onBinaryFrame(data: Array[Byte], last: Boolean, rsv: Int) =
        enqueue(Binary(data, last))

      override def onTextFrame(s: String, last: Boolean, rsv: Int) =
        enqueue(Text(s, last))

      override def onPingFrame(data: Array[Byte]) =
        enqueue(Ping(data))

      override def onPongFrame(data: Array[Byte]) =
        enqueue(Pong(data))
    }

  private def fromNettyFuture[F[_], A](nf: NFuture[A])(implicit F: Async[F]): F[A] =
    F.async[A] { cb =>
      nf.addListener(new GenericFutureListener[NFuture[A]] {
        def operationComplete(f: NFuture[A]) =
          try cb(Right(f.getNow))
          catch {
            case ee: ExecutionException => cb(Left(ee.getCause))
          }
      })
      ()
    }
}
