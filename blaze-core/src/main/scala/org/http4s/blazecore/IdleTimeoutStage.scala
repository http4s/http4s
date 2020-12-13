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

package org.http4s
package blazecore

import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.{AtomicReference}
import org.http4s.blaze.pipeline.MidStage
import org.http4s.blaze.util.{Cancelable, TickWheelExecutor}
import org.log4s.getLogger
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

final private[http4s] class IdleTimeoutStage[A](
    timeout: FiniteDuration,
    exec: TickWheelExecutor,
    ec: ExecutionContext)
    extends MidStage[A, A] { stage =>
  private[this] val logger = getLogger

  @volatile private var cb: Callback[TimeoutException] = null

  private val timeoutState = new AtomicReference[Cancelable](NoOpCancelable)

  override def name: String = "IdleTimeoutStage"

  private val killSwitch = new Runnable {
    override def run(): Unit = {
      val t = new TimeoutException(s"Idle timeout after ${timeout.toMillis} ms.")
      logger.debug(t.getMessage)
      cb(Right(t))
      removeStage()
    }
  }

  override def readRequest(size: Int): Future[A] = {
    resetTimeout()
    channelRead(size)
  }

  override def writeRequest(data: A): Future[Unit] = {
    resetTimeout()
    channelWrite(data)
  }

  override def writeRequest(data: collection.Seq[A]): Future[Unit] = {
    resetTimeout()
    channelWrite(data)
  }

  override protected def stageShutdown(): Unit = {
    cancelTimeout()
    logger.debug(s"Shutting down idle timeout stage")
    super.stageShutdown()
  }

  def init(cb: Callback[TimeoutException]): Unit = {
    logger.debug(s"Starting idle timeout stage with timeout of ${timeout.toMillis} ms")
    stage.cb = cb
    resetTimeout()
  }

  private def setAndCancel(next: Cancelable): Unit = {
    val _ = timeoutState.getAndSet(next).cancel()
  }

  private def resetTimeout(): Unit =
    setAndCancel(exec.schedule(killSwitch, ec, timeout))

  private def cancelTimeout(): Unit =
    setAndCancel(NoOpCancelable)
}
