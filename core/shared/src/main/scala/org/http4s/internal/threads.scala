/*
 * Copyright 2013 http4s.org
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

package org.http4s.internal

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

private[http4s] object threads {
  final case class ThreadPriority(toInt: Int)
  case object ThreadPriority {
    val Min: ThreadPriority = ThreadPriority(Thread.MIN_PRIORITY)
    val Norm: ThreadPriority = ThreadPriority(Thread.NORM_PRIORITY)
    val Max: ThreadPriority = ThreadPriority(Thread.MAX_PRIORITY)
  }

  def threadFactory(
      name: Long => String = { l =>
        s"http4s-$l"
      },
      daemon: Boolean = false,
      priority: ThreadPriority = ThreadPriority.Norm,
      uncaughtExceptionHandler: PartialFunction[(Thread, Throwable), Unit] = PartialFunction.empty,
      backingThreadFactory: ThreadFactory = Executors.defaultThreadFactory,
  ): ThreadFactory =
    new ThreadFactory {
      val count = new AtomicLong(0)
      override def newThread(r: Runnable): Thread = {
        val thread = backingThreadFactory.newThread(r)
        thread.setName(name(count.getAndIncrement))
        thread.setDaemon(daemon)
        thread.setPriority(priority.toInt)
        thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
          override def uncaughtException(t: Thread, e: Throwable): Unit =
            uncaughtExceptionHandler.orElse(fallthrough)((t, e))
          val fallthrough: PartialFunction[(Thread, Throwable), Unit] = {
            case (t: Thread, e: Throwable) =>
              Option(t.getThreadGroup)
                .getOrElse(Thread.getDefaultUncaughtExceptionHandler)
                .uncaughtException(t, e)
          }
        })
        thread
      }
    }

  def newDaemonPool(
      name: String,
      min: Int = 4,
      cpuFactor: Double = 3.0,
      timeout: Boolean = false,
  ): ThreadPoolExecutor = {
    val cpus = Runtime.getRuntime.availableProcessors
    val exec = new ThreadPoolExecutor(
      math.max(min, cpus),
      math.max(min, (cpus * cpuFactor).ceil.toInt),
      10L,
      TimeUnit.SECONDS,
      new LinkedBlockingQueue[Runnable],
      threadFactory(i => s"$name-$i", daemon = true),
    )
    exec.allowCoreThreadTimeOut(timeout)
    exec
  }

  def newDaemonPoolExecutionContext(
      name: String,
      min: Int = 4,
      cpuFactor: Double = 3.0,
      timeout: Boolean = false,
  ): ExecutionContext =
    ExecutionContext.fromExecutorService(newDaemonPool(name, min, cpuFactor, timeout))

  def newBlockingPool(name: String): ExecutorService = {
    val CorePoolSize = 0
    val MaxPoolSize = Int.MaxValue
    val KeepAliveTime = 1.minute

    new ThreadPoolExecutor(
      CorePoolSize,
      MaxPoolSize,
      KeepAliveTime.toSeconds,
      TimeUnit.SECONDS,
      new SynchronousQueue[Runnable](false),
      threadFactory(i => s"$name-$i", daemon = true),
    )
  }
}
