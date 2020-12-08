/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.blazecore.websocket

import cats.effect.IO
import cats.effect.std.Semaphore
import cats.implicits._
import fs2.Stream
import fs2.concurrent.Queue
import org.http4s.blaze.pipeline.HeadStage
import org.http4s.websocket.WebSocketFrame

import scala.concurrent.Future
import scala.concurrent.duration._
import cats.effect.unsafe.implicits.global

/** A simple stage t
  * o help test websocket requests
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
  */
sealed abstract class WSTestHead(
    inQueue: Queue[IO, WebSocketFrame],
    outQueue: Queue[IO, WebSocketFrame],
    writeSemaphore: Semaphore[IO])
    extends HeadStage[WebSocketFrame] {

  /** Block while we put elements into our queue
    *
    * @return
    */
  override def readRequest(size: Int): Future[WebSocketFrame] =
    inQueue.dequeue1.unsafeToFuture()

  /** Sent downstream from the websocket stage,
    * put the result in our outqueue, so we may
    * pull from it later to inspect it
    */
  override def writeRequest(data: WebSocketFrame): Future[Unit] =
    writeSemaphore.tryAcquire
      .flatMap {
        case true =>
          outQueue.enqueue1(data) *> writeSemaphore.release
        case false =>
          IO.raiseError(new IllegalStateException("pending write"))
      }
      .unsafeToFuture()

  /** Insert data into the read queue,
    * so it's read by the websocket stage
    * @param ws
    */
  def put(ws: WebSocketFrame): IO[Unit] =
    inQueue.enqueue1(ws)

  val outStream: Stream[IO, WebSocketFrame] =
    outQueue.dequeue

  /** poll our queue for a value,
    * timing out after `timeoutSeconds` seconds
    * runWorker(this);
    */
  def poll(timeoutSeconds: Long): IO[Option[WebSocketFrame]] =
    IO.race(IO.sleep(timeoutSeconds.seconds), outQueue.dequeue1)
      .map {
        case Left(_) => None
        case Right(wsFrame) =>
          Some(wsFrame)
      }

  def pollBatch(batchSize: Int, timeoutSeconds: Long): IO[List[WebSocketFrame]] =
    outQueue
      .dequeueChunk1(batchSize)
      .map(_.toList)
      .timeoutTo(timeoutSeconds.seconds, IO.pure(Nil))

  override def name: String = "WS test stage"

  override protected def doClosePipeline(cause: Option[Throwable]): Unit = {}
}

object WSTestHead {
  def apply(): IO[WSTestHead] =
    (Queue.unbounded[IO, WebSocketFrame], Queue.unbounded[IO, WebSocketFrame], Semaphore[IO](1L))
      .mapN(new WSTestHead(_, _, _) {})
}
