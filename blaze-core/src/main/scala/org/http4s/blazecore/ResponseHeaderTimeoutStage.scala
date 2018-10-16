package org.http4s
package blazecore

import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.{AtomicReference}
import org.http4s.blaze.pipeline.MidStage
import org.http4s.blaze.util.{Cancelable, TickWheelExecutor}
import org.log4s.getLogger
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration

final private[http4s] class ResponseHeaderTimeoutStage[A](
    timeout: Duration,
    exec: TickWheelExecutor,
    ec: ExecutionContext)
    extends MidStage[A, A] { stage =>
  private[this] val logger = getLogger
  @volatile private[this] var cb: Callback[TimeoutException] = null

  private val timeoutState = new AtomicReference[Cancelable](NoOpCancelable)

  override def name: String = "ResponseHeaderTimeoutStage"

  private val killSwitch = new Runnable {
    override def run(): Unit = {
      val t = new TimeoutException(s"Response header timeout after ${timeout.toMillis} ms.")
      logger.debug(t.getMessage)
      cb(Left(t))
      removeStage()
    }
  }

  override def readRequest(size: Int): Future[A] =
    channelRead(size)

  override def writeRequest(data: A): Future[Unit] = {
    setTimeout()
    channelWrite(data)
  }

  override def writeRequest(data: Seq[A]): Future[Unit] = {
    setTimeout()
    channelWrite(data)
  }

  override protected def stageShutdown(): Unit = {
    cancelTimeout()
    logger.debug(s"Shutting down response header timeout stage")
    super.stageShutdown()
  }

  override def stageStartup(): Unit = {
    super.stageStartup()
    logger.debug(s"Starting response header timeout stage with timeout of ${timeout.toMillis} ms")
  }

  def init(cb: Callback[TimeoutException]): Unit = {
    this.cb = cb
    stageStartup()
  }

  private def setTimeout(): Unit = {
    @tailrec
    def go(): Unit = {
      val prev = timeoutState.get()
      if (prev == NoOpCancelable) {
        val next = exec.schedule(killSwitch, ec, timeout)
        if (!timeoutState.compareAndSet(prev, next)) {
          next.cancel()
          go()
        } else {
          prev.cancel()
        }
      }
    }
    go()
  }

  private def cancelTimeout(): Unit =
    timeoutState.getAndSet(NoOpCancelable).cancel()
}
