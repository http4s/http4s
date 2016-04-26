package org.http4s
package client
package asynchttpclient

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import org.asynchttpclient.HttpResponseBodyPart
import org.asynchttpclient.ws.{ DefaultWebSocketListener, WebSocketCloseCodeReasonListener, WebSocketTextFragmentListener }

import scala.collection.JavaConverters._

import org.asynchttpclient.{Request => AsyncRequest, Response => _, _}
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.asynchttpclient.request.body.generator.{BodyGenerator, InputStreamBodyGenerator}
import org.asynchttpclient.ws.{WebSocket => AhcWebSocket, WebSocketByteListener, WebSocketListener, WebSocketPingListener, WebSocketPongListener, WebSocketTextListener, WebSocketUpgradeHandler}
import org.http4s.client.impl.DefaultExecutor
import org.http4s.util.threads._
import org.http4s.websocket.WebSocket
import org.http4s.websocket.WebsocketBits.{Binary, Close, Ping, Pong, Text, WebSocketFrame}
import org.log4s.getLogger
import org.reactivestreams.Publisher
import scalaz.stream.Cause.Terminated
import scalaz.{\/-, -\/}
import scalaz.concurrent.Task
import scalaz.stream.{Exchange, Process, Sink}
import scalaz.stream.async._
import scalaz.stream.io._
import scodec.bits.ByteVector

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
    val close =
      if (customExecutor.isDefined)
        Task.delay { client.close() }
      else
        Task.delay {
          client.close()
          executorService.shutdown()
        }

    val open = Service.lift { req: Request =>
      Task.async[DisposableResponse] { cb =>
        client.executeRequest(toAsyncRequest(req, true), asyncHandler(cb, bufferSize, executorService))
      }
    }

    val ws = Service.lift { req: Request =>
      Task.async[WebSocket] { cb =>
        client.executeRequest(toAsyncRequest(req, false), wsHandler(cb))
      }
    }

    Client(open, ws, close)
  }

  private def asyncHandler(callback: Callback[DisposableResponse],
                           bufferSize: Int,
                           executorService: ExecutorService): AsyncHandler[Unit] =
    new StreamedAsyncHandler[Unit] {
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
        disposableResponse = disposableResponse.copy(response = disposableResponse.response.copy(headers = getHeaders(headers)))
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

  private def wsHandler(cb: Callback[WebSocket]): WebSocketUpgradeHandler = {
    new WebSocketUpgradeHandler.Builder().addWebSocketListener(
      new DefaultWebSocketListener {
        val src = boundedQueue[WebSocketFrame](10)
        var ahcWs: AhcWebSocket = _

        override def onOpen(ws: AhcWebSocket): Unit = {
          ahcWs = ws
          val sink: Sink[Task, WebSocketFrame] = Process.constant {
            case Text(str, _) => 
              Task.delay {
                println("Sending")
                ahcWs.sendMessage(str.getBytes)
              }
            case Binary(data, _) =>
              Task.delay {
                ahcWs.sendMessage(data)
              }
            case Close(_) =>
              Task.delay {
                ahcWs.close()
              }
            case Ping(data) =>
              Task.delay { 
                ahcWs.sendPing(data)
              }
            case Pong(data) =>
              Task.delay {
                ahcWs.sendPong(data)
              }
          }
          val exchange = Exchange(src.dequeue, sink)
          log.info("Connected")
          cb(\/-(WebSocket(exchange,
            ahcWs.getLocalAddress.asInstanceOf[InetSocketAddress],
            ahcWs.getRemoteAddress.asInstanceOf[InetSocketAddress])))
        }

        override def onClose(ws: AhcWebSocket): Unit = {
          log.info("Closed")
          src.close.run
        }

        override def onError(t: Throwable): Unit =
          t match {
            case _: Terminated =>
            case _ => cb(-\/(t))
          }

        override def onMessage(message: String): Unit =
          src.enqueueOne(Text(message)).run
      }
    ).build()
  }

  private def toAsyncRequest(request: Request, setBody: Boolean): AsyncRequest = {
    val builder = new RequestBuilder(request.method.toString)
      .setUrl(request.uri.toString)
      .setHeaders(request.headers
        .groupBy(_.name.toString)
        .mapValues(_.map(_.value).asJavaCollection)
        .asJava)
    // Websockets don't work if we set a body.
    if (setBody)
      builder.setBody(getBodyGenerator(request.body))
    builder.build
  }

  private def getBodyGenerator(body: EntityBody): BodyGenerator =
    new InputStreamBodyGenerator(toInputStream(body))

  private def getStatus(status: HttpResponseStatus): Status =
    Status.fromInt(status.getStatusCode).valueOr(throw _)

  private def getHeaders(headers: HttpResponseHeaders): Headers = {
    Headers(headers.getHeaders.iterator.asScala.map { header =>
      Header(header.getKey, header.getValue)
    }.toList)
  }
}
