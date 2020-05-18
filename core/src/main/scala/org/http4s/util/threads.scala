/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.util

import java.util.concurrent._
import scala.concurrent.ExecutionContext

@deprecated("Not related to HTTP. Will be removed from public API.", "0.21.5")
object threads {
  final case class ThreadPriority(toInt: Int)
  case object ThreadPriority {
    val Min = ThreadPriority(Thread.MIN_PRIORITY)
    val Norm = ThreadPriority(Thread.NORM_PRIORITY)
    val Max = ThreadPriority(Thread.MAX_PRIORITY)
  }

  def threadFactory(
      name: Long => String = { l =>
        s"http4s-$l"
      },
      daemon: Boolean = false,
      priority: ThreadPriority = ThreadPriority.Norm,
      uncaughtExceptionHandler: PartialFunction[(Thread, Throwable), Unit] = PartialFunction.empty,
      backingThreadFactory: ThreadFactory = Executors.defaultThreadFactory
  ): ThreadFactory = {
    val p = org.http4s.internal.threads.ThreadPriority(priority.toInt)
    org.http4s.internal.threads.threadFactory(
      name,
      daemon,
      p,
      uncaughtExceptionHandler,
      backingThreadFactory)
  }

  @deprecated("Use newDaemonPool instead", "0.15.7")
  private[http4s] def newDefaultFixedThreadPool(
      n: Int,
      threadFactory: ThreadFactory): ExecutorService =
    new ThreadPoolExecutor(
      n,
      n,
      0L,
      TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue[Runnable],
      threadFactory)

  private[http4s] def newDaemonPool(
      name: String,
      min: Int = 4,
      cpuFactor: Double = 3.0,
      timeout: Boolean = false): ThreadPoolExecutor =
    org.http4s.internal.threads.newDaemonPool(name, min, cpuFactor, timeout)

  private[http4s] def newDaemonPoolExecutionContext(
      name: String,
      min: Int = 4,
      cpuFactor: Double = 3.0,
      timeout: Boolean = false): ExecutionContext =
    org.http4s.internal.threads.newDaemonPoolExecutionContext(name, min, cpuFactor, timeout)

  private[http4s] def newBlockingPool(name: String): ExecutorService =
    org.http4s.internal.threads.newBlockingPool(name)
}
