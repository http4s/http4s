/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.client.blaze

import org.http4s.blaze.pipeline.MidStage
import org.http4s.blaze.util.Execution
import org.http4s.internal.bug
import scala.concurrent.Future

/** Stage that buffers read requests in order to eagerly detect connection close events.
  *
  * Among other things, this is useful for helping clients to avoid making
  * requests against a stale connection when doing so may result in side
  * effects, and therefore cannot be retried.
  */
private[blaze] final class ReadBufferStage[T] extends MidStage[T, T] {
  override def name: String = "ReadBufferingStage"

  private val lock: Object = this
  private var buffered: Future[T] = _

  override def writeRequest(data: T): Future[Unit] = channelWrite(data)

  override def writeRequest(data: collection.Seq[T]): Future[Unit] = channelWrite(data)

  override def readRequest(size: Int): Future[T] = lock.synchronized {
    if (buffered == null)
      Future.failed(new IllegalStateException("Cannot have multiple pending reads"))
    else if (buffered.isCompleted) {
      // What luck: we can schedule a new read right now, without an intermediate future
      val r = buffered
      buffered = channelRead()
      r
    } else {
      // Need to schedule a new read for after this one resolves
      val r = buffered
      buffered = null

      // We use map as it will introduce some ordering: scheduleRead() will
      // be called before the new Future resolves, triggering the next read.
      r.map { v =>
        scheduleRead(); v
      }(Execution.directec)
    }
  }

  // On startup we begin buffering a read event
  override protected def stageStartup(): Unit = {
    logger.debug("Stage started up. Beginning read buffering")
    lock.synchronized {
      buffered = channelRead()
    }
  }

  private def scheduleRead(): Unit = lock.synchronized {
    if (buffered == null) {
      buffered = channelRead()
    } else {
      val msg = "Tried to schedule a read when one is already pending"
      val ex = bug(msg)
      // This should never happen, but if it does, lets scream about it
      logger.error(ex)(msg)
      throw ex
    }
  }
}
