package org.http4s.blazecore.websocket

import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}
import org.http4s.blaze.pipeline.HeadStage
import org.http4s.websocket.WebsocketBits.WebSocketFrame
import scala.concurrent.Future

/** A simple stage to help
  * test websocket requests
  *
  */
class WSTestHead extends HeadStage[WebSocketFrame] {

  private[this] val inQueue: ArrayBlockingQueue[WebSocketFrame] =
    new ArrayBlockingQueue[WebSocketFrame](10)
  private[this] val outQueue: ArrayBlockingQueue[WebSocketFrame] =
    new ArrayBlockingQueue[WebSocketFrame](10)

  override def readRequest(size: Int): Future[WebSocketFrame] =
    Future.successful(inQueue.take())

  override def writeRequest(data: WebSocketFrame): Future[Unit] = {
    outQueue.put(data)
    Future.unit
  }

  def put(ws: WebSocketFrame): Unit =
    inQueue.put(ws)

  def poll(timeoutSeconds: Long = 2L): Option[WebSocketFrame] =
    Option(outQueue.poll(timeoutSeconds, TimeUnit.SECONDS))

  override def name: String = "WS test stage"
}
