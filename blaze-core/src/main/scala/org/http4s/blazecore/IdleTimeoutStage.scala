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
import java.util.concurrent.atomic.AtomicReference
import org.http4s.blaze.pipeline.MidStage
import org.http4s.blaze.util.{Cancelable, Execution, TickWheelExecutor}
import org.http4s.blazecore.IdleTimeoutStage.{Disabled, Enabled, ShutDown, State}

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

final private[http4s] class IdleTimeoutStage[A](
    timeout: FiniteDuration,
    exec: TickWheelExecutor,
    ec: ExecutionContext)
    extends MidStage[A, A] { stage =>

  private val timeoutState = new AtomicReference[State](Disabled)

  override def name: String = "IdleTimeoutStage"

  override def readRequest(size: Int): Future[A] =
    channelRead(size).andThen { case _ => resetTimeout() }(Execution.directec)

  override def writeRequest(data: A): Future[Unit] = {
    resetTimeout()
    channelWrite(data)
  }

  override def writeRequest(data: collection.Seq[A]): Future[Unit] = {
    resetTimeout()
    channelWrite(data)
  }

  override protected def stageShutdown(): Unit = {
    logger.debug(s"Shutting down idle timeout stage")

    @tailrec def go(): Unit =
      timeoutState.get() match {
        case old @ IdleTimeoutStage.Enabled(_, cancel) =>
          if (timeoutState.compareAndSet(old, ShutDown)) cancel.cancel()
          else go()
        case old =>
          if (!timeoutState.compareAndSet(old, ShutDown)) go()
      }

    go()

    super.stageShutdown()
  }

  def init(cb: Callback[TimeoutException]): Unit = setTimeout(cb)

  def setTimeout(cb: Callback[TimeoutException]): Unit = {
    logger.debug(s"Starting idle timeout with timeout of ${timeout.toMillis} ms")

    val timeoutTask = new Runnable {
      override def run(): Unit = {
        val t = new TimeoutException(s"Idle timeout after ${timeout.toMillis} ms.")
        logger.debug(t.getMessage)
        cb(Right(t))
      }
    }

    @tailrec def go(): Unit =
      timeoutState.get() match {
        case Disabled =>
          val newCancel = exec.schedule(timeoutTask, timeout)
          if (timeoutState.compareAndSet(Disabled, Enabled(timeoutTask, newCancel))) ()
          else {
            newCancel.cancel()
            go()
          }
        case old @ Enabled(_, oldCancel) =>
          val newCancel = exec.schedule(timeoutTask, timeout)
          if (timeoutState.compareAndSet(old, Enabled(timeoutTask, newCancel))) oldCancel.cancel()
          else {
            newCancel.cancel()
            go()
          }
        case _ => ()
      }

    go()
  }

  @tailrec private def resetTimeout(): Unit =
    timeoutState.get() match {
      case old @ Enabled(timeoutTask, oldCancel) =>
        val newCancel = exec.schedule(timeoutTask, timeout)
        if (timeoutState.compareAndSet(old, Enabled(timeoutTask, newCancel))) oldCancel.cancel()
        else {
          newCancel.cancel()
          resetTimeout()
        }
      case _ => ()
    }

  @tailrec def cancelTimeout(): Unit =
    timeoutState.get() match {
      case old @ IdleTimeoutStage.Enabled(_, cancel) =>
        if (timeoutState.compareAndSet(old, Disabled)) cancel.cancel()
        else cancelTimeout()
      case _ => ()
    }

  def tryScheduling(timeoutTask: Runnable): Option[Cancelable] =
    if (exec.isAlive) {
      try Some(exec.schedule(timeoutTask, ec, timeout))
      catch {
        case TickWheelExecutor.AlreadyShutdownException =>
          logger.warn(s"Resetting timeout after tickwheelexecutor is shutdown")
          None
        case NonFatal(e) => throw e
      }
    } else {
      None
    }
}

object IdleTimeoutStage {

  sealed trait State
  case object Disabled extends State
  case class Enabled(timeoutTask: Runnable, cancel: Cancelable) extends State
  case object ShutDown extends State

}
