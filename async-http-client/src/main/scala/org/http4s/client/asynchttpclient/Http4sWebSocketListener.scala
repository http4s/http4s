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

class Http4sWebSocketListener(cb: Callback[Response])
    extends DefaultWebSocketListener {
  private[this] val log = getLogger

  val src = boundedQueue[WebSocketFrame](10)
  var ahcWs: AhcWebSocket = _

  override def onOpen(ws: AhcWebSocket): Unit = {
    ahcWs = ws
    val sink: Sink[Task, WebSocketFrame] = Process.constant {
      case Text(str, _) =>
        Task.delay {
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
    val local = ahcWs.getLocalAddress.asInstanceOf[InetSocketAddress]
    val remote = ahcWs.getRemoteAddress.asInstanceOf[InetSocketAddress]
    log.info(s"WebSocket connected: $local to $remote")
    val webSocket = WebSocket(
      getHeaders(ahcWs.getUpgradeHeaders),
      exchange,
      local,
      remote)
    val resp = Response(Status.SwitchingProtocols)
      .withAttribute(Client.WebSocketKey, webSocket)
    cb(\/-(resp))
  }

  override def onClose(ws: AhcWebSocket): Unit = {
    log.info(s"WebSocket closed: ${ws.getLocalAddress} to ${ws.getRemoteAddress}")
    src.close.run
  }

  override def onError(t: Throwable): Unit =
    t match {
      case _: Terminated =>
      case _ =>
        // TODO How can we return a full Response here?
        // All we get if the status != 101 is an IllegalStateException
        cb(-\/(t))
    }

  override def onMessage(message: String): Unit =
    src.enqueueOne(Text(message)).run
}
