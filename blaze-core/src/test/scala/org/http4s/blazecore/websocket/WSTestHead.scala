package org.http4s.blazecore.websocket

import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import org.http4s.blaze.pipeline.HeadStage
import org.http4s.websocket.WebsocketBits.WebSocketFrame
import scala.concurrent.Future

/** A simple stage to help test websocket requests
  *
  * This is really disgusting code but bear with me here:
  * `java.util.LinkedBlockingDeque` does NOT have nodes with
  * atomic references. We need to check finalizers, and those are run concurrently
  * and nondeterministically, so we're in a sort of hairy situation
  * for checking finalizers doing anything other than waiting on an update
  *
  * That is, on updates, we may easily run into a lost update problem if
  * nodes are checked by a different thread since node values have no
  * atomicity guarantee by the jvm. I simply want to provide a (blocking)
  * way of reading a websocket frame, to emulate reading from a socket.
  *
  */
sealed abstract class WSTestHead(
    inQueue: ConcurrentLinkedQueue[WebSocketFrame],
    outQueue: ConcurrentLinkedQueue[WebSocketFrame])
    extends HeadStage[WebSocketFrame] {

  /** Block while we put elements into our queue
    *
    * @return
    */
  override def readRequest(size: Int): Future[WebSocketFrame] =
    Future.successful {
      var r: WebSocketFrame = null
      while (r eq null) {
        r = inQueue.poll()
      }
      r
    }

  /** Sent downstream from the websocket stage,
    * put the result in our outqueue, so we may
    * pull from it later to inspect it
    */
  override def writeRequest(data: WebSocketFrame): Future[Unit] = {
    val _ = outQueue.add(data)
    Future.unit
  }

  /** Insert data into the read queue,
    * so it's read by the websocket stage
    * @param ws
    */
  def put(ws: WebSocketFrame): Unit = {
    inQueue.add(ws); ()
  }

  /** poll our queue for a value,
    * timing out after `timeoutSeconds` seconds
    *
    */
  def poll(timeoutSeconds: Long): Option[WebSocketFrame] =
    Option {
      var r: WebSocketFrame = null
      val expires = Instant.now.plusSeconds(timeoutSeconds)
      while ((r eq null) && expires.isAfter(Instant.now())) {
        r = outQueue.poll()
      }
      r
    }

  override def name: String = "WS test stage"
}

object WSTestHead {
  def apply(): WSTestHead = {
    val inQueue: ConcurrentLinkedQueue[WebSocketFrame] =
      new ConcurrentLinkedQueue[WebSocketFrame]()

    val outQueue: ConcurrentLinkedQueue[WebSocketFrame] =
      new ConcurrentLinkedQueue[WebSocketFrame]()
    new WSTestHead(inQueue, outQueue) {}
  }
}
