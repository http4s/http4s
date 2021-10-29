/*
 * Copyright 2014 http4s.org
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

package org.http4s.blazecore.websocket

import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Timer
import cats.effect.concurrent.Semaphore
import cats.syntax.all._
import fs2.Stream
import fs2.concurrent.Queue
import org.http4s.blaze.pipeline.HeadStage
import org.http4s.websocket.WebSocketFrame

import scala.concurrent.Future
import scala.concurrent.duration._

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
  */
sealed abstract class WSTestHead(
    inQueue: Queue[IO, WebSocketFrame],
    outQueue: Queue[IO, WebSocketFrame])(implicit timer: Timer[IO], cs: ContextShift[IO])
    extends HeadStage[WebSocketFrame] {

  private[this] val writeSemaphore = Semaphore[IO](1L).unsafeRunSync()

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
    IO.race(timer.sleep(timeoutSeconds.seconds), outQueue.dequeue1)
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
  def apply()(implicit t: Timer[IO], cs: ContextShift[IO]): IO[WSTestHead] =
    (Queue.unbounded[IO, WebSocketFrame], Queue.unbounded[IO, WebSocketFrame])
      .mapN(new WSTestHead(_, _) {})
}
