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

import java.util.ArrayDeque
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor

@deprecated("Unused.  Will be removed in 1.0", "0.23.11")
private[http4s] object Trampoline extends ExecutionContextExecutor {
  private val local = new ThreadLocal[ThreadLocalTrampoline]

  def execute(runnable: Runnable): Unit = {
    var queue = local.get()
    if (queue == null) {
      queue = new ThreadLocalTrampoline
      local.set(queue)
    }

    queue.execute(runnable)
  }

  def reportFailure(t: Throwable): Unit = ExecutionContext.defaultReporter(t)

  // Only safe to use from a single thread
  private final class ThreadLocalTrampoline extends ExecutionContext {
    private var running = false
    private var r0, r1, r2: Runnable = null
    private var rest: ArrayDeque[Runnable] = null

    override def execute(runnable: Runnable): Unit = {
      if (r0 == null) r0 = runnable
      else if (r1 == null) r1 = runnable
      else if (r2 == null) r2 = runnable
      else {
        if (rest == null) rest = new ArrayDeque[Runnable]()
        rest.add(runnable)
      }

      if (!running) {
        running = true
        run()
      }
    }

    override def reportFailure(cause: Throwable): Unit = ExecutionContext.defaultReporter(cause)

    @tailrec
    private def run(): Unit = {
      val r = next()
      if (r == null) {
        rest = null // don't want a memory leak from potentially large array buffers
        running = false
      } else {
        try r.run()
        catch { case e: Throwable => reportFailure(e) }
        run()
      }
    }

    private def next(): Runnable = {
      val r = r0
      r0 = r1
      r1 = r2
      r2 = if (rest != null) rest.pollFirst() else null
      r
    }
  }
}
