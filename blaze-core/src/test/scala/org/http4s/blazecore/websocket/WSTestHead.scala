package org.http4s.blazecore.websocket

import cats.effect.{ContextShift, IO, Timer}
import cats.effect.concurrent.Deferred
import cats.implicits._
import fs2.concurrent.{NoneTerminatedQueue, Queue}
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
  *
  */
sealed abstract class WSTestHead(
    inQueue: Queue[IO, WebSocketFrame],
    outQueue: NoneTerminatedQueue[IO, WebSocketFrame],
    val isStageShutdown: Deferred[IO, Unit]
)(implicit timer: Timer[IO], cs: ContextShift[IO])
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
    outQueue.enqueue1(Some(data)).unsafeToFuture()

  /** Insert data into the read queue,
    * so it's read by the websocket stage
    * @param ws
    */
  def put(ws: WebSocketFrame): IO[Unit] =
    inQueue.enqueue1(ws)

  /** poll our queue for a value,
    * timing out after `timeoutSeconds` seconds
    * runWorker(this);
    */
  def poll(timeoutSeconds: Long): IO[Option[WebSocketFrame]] =
    IO.race(timer.sleep(timeoutSeconds.seconds), outQueue.dequeue1)
      .map {
        case Left(_) => None
        case Right(opt) => opt
      }

  def pollBatch(batchSize: Int, timeoutSeconds: Long): IO[List[WebSocketFrame]] =
    outQueue
      .dequeueChunk1(batchSize)
      .map(_.fold(List.empty[WebSocketFrame])(_.toList))
      .timeoutTo(timeoutSeconds.seconds, IO.pure(Nil))

  override def name: String = "WS test stage"

  override protected def doClosePipeline(cause: Option[Throwable]): Unit = {}

  override protected def stageShutdown() = {
    super.stageShutdown()
    (outQueue.enqueue1(None) >> isStageShutdown.complete(()))
      .unsafeRunAsync(_ => ())
  }
}

object WSTestHead {
  def apply()(implicit t: Timer[IO], cs: ContextShift[IO]): WSTestHead =
    (
      Queue.unbounded[IO, WebSocketFrame],
      Queue.synchronousNoneTerminated[IO, WebSocketFrame],
      Deferred[IO, Unit]
    ).mapN(new WSTestHead(_, _, _) {}).unsafeRunSync()
}
