package org.http4s.testing

import java.util.concurrent.{ExecutorService, LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scalaz.concurrent.Strategy
import org.http4s.util.threads.threadFactory

object concurrent {
  private[this] val poolCounter = new AtomicInteger(0)

  val TestPool: ExecutorService = {
    val poolNumber = poolCounter.incrementAndGet
    val minThreads = 4
    val maxThreads = math.max(16, Runtime.getRuntime.availableProcessors)
    val exec = new ThreadPoolExecutor(minThreads, maxThreads,
      10, TimeUnit.SECONDS,
      new LinkedBlockingQueue[Runnable],
      threadFactory(i => s"http4s-testing-${poolNumber}-$i", daemon = true))
    exec.allowCoreThreadTimeOut(true) // don't leak threads on multiple test runs
    exec
  }

  implicit val TestStrategy: Strategy =
    Strategy.Executor(TestPool)
}
