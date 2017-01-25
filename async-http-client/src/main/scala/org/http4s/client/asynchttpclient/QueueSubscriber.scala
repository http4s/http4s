package org.http4s.client.asynchttpclient

import fs2._
import fs2.Stream._
import fs2.async.boundedQueue
import org.log4s.getLogger

class QueueSubscriber[A](bufferSize: Int = 8)(implicit S: Strategy) extends UnicastSubscriber[A] {
  private[this] val log = getLogger

  private val queue =
    boundedQueue[Task, A](bufferSize)
      .unsafeRun // TODO fs2 port why is queue creation an effect now?

  private val refillProcess =
    repeatEval {
      Task.delay {
        log.trace("Requesting another element")
        request(1)
      }
    }

  final val process: Stream[Task, A] =
    (refillProcess zipWith queue.dequeue)((_, a) => a)

  def whenNext(element: A): Boolean = {
    queue.enqueue1(element).unsafeRun
    true
  }

  def closeQueue(): Unit = {
    log.debug("Closing queue subscriber")
    queue.close.unsafeRun
  }

  def killQueue(): Unit = {
    log.debug("Killing queue subscriber")
    queue.kill.unsafeRun
  }

  override def onComplete(): Unit = {
    log.debug(s"Completed queue subscriber")
    super.onComplete()
    closeQueue()
  }

  override def onError(t: Throwable): Unit = {
    super.onError(t)
    queue.fail(t).unsafeRun
  }
}
