package org.http4s.client.asynchttpclient

import fs2._
import fs2.Stream._
import fs2.async.boundedQueue
import org.log4s.getLogger

class QueueSubscriber[A] private[http4s] (bufferSize: Int, val queue: Queue[A]) extends UnicastSubscriber[A](bufferSize) {
  private[this] val log = getLogger

  @deprecated("Triggers the scalaz.stream.DefaultExecutor. This class will be made private in 0.16.", "0.15.7")
  def this(bufferSize: Int = 8) =
    this(bufferSize, unboundedQueue[A])

  final val process: Process[Task, A] =
    queue.dequeue.observe(Process.constant { _: Any => Task.delay(request(1)) })

  def whenNext(element: A): Boolean = {
    log.debug(s"Enqueuing element: $this")
    queue.enqueueOne(element).unsafePerformSync
    true
  }

  def closeQueue(): Unit =
    queue.close.unsafePerformAsync {
      case \/-(_) => log.debug(s"Closed queue subscriber $this")
      case -\/(t) => log.error(t)(s"Error closing queue subscriber $this")
    }

  def killQueue(): Unit =
    queue.close.unsafePerformAsync {
      case \/-(_) => log.debug(s"Killed queue subscriber $this")
      case -\/(t) => log.error(t)(s"Error killing queue subscriber $this")
    }

  override def onComplete(): Unit = {
    log.debug(s"Completed queue subscriber $this")
    super.onComplete()
    closeQueue()
  }

  override def onError(t: Throwable): Unit = {
    super.onError(t)
    log.debug(s"Failing queue subscriber $this")
    queue.fail(t).unsafeRun
  }
}
