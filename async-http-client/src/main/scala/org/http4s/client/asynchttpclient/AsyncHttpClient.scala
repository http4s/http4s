package org.http4s
package client
package asynchttpclient

import scala.collection.JavaConverters._

import io.netty.handler.codec.http.HttpHeaders
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import org.asynchttpclient.HttpResponseBodyPart
import org.asynchttpclient.ws.{ DefaultWebSocketListener, WebSocketCloseCodeReasonListener, WebSocketTextFragmentListener }
import org.asynchttpclient.{Request => AsyncRequest, Response => _, _}
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.asynchttpclient.request.body.generator.{BodyGenerator, InputStreamBodyGenerator}
import org.asynchttpclient.ws.{WebSocket => AhcWebSocket, WebSocketByteListener, WebSocketListener, WebSocketPingListener, WebSocketPongListener, WebSocketTextListener, WebSocketUpgradeHandler}
import org.http4s.client.impl.DefaultExecutor
import org.http4s.util.threads._
import org.http4s.client.WebSocket
import org.http4s.websocket.WebsocketBits.{Binary, Close, Ping, Pong, Text, WebSocketFrame}
import org.log4s.getLogger
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
        val handler =
          if (req.isWebSocketRequest)
            wsHandler(cb.compose {
              _.map(resp => DisposableResponse(resp, Task.now(())))
            })
          else
            new Http4sAsyncHandler(cb, bufferSize, executorService)
        client.executeRequest(toAsyncRequest(req), handler)
      }
    }

    Client(open, close)
  }

  private def wsHandler(cb: Callback[Response]): WebSocketUpgradeHandler =
    new WebSocketUpgradeHandler.Builder().addWebSocketListener(
      new Http4sWebSocketListener(cb)).build()

  private def toAsyncRequest(request: Request): AsyncRequest = {
    val builder = new RequestBuilder(request.method.toString)
      .setUrl(request.uri.toString)
      .setHeaders(request.headers
        .groupBy(_.name.toString)
        .mapValues(_.map(_.value).asJavaCollection)
        .asJava)
    // AHC doesn't like a body on WebSocket requests.
    if (!request.isWebSocketRequest)
      builder.setBody(getBodyGenerator(request.body))
    builder.build
  }

  private def getBodyGenerator(body: EntityBody): BodyGenerator =
    new InputStreamBodyGenerator(toInputStream(body))
}
