/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.util

import java.util.ArrayDeque
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object execution {

  /** Execute `Runnable`s directly on the current thread, using a stack frame.
    *
    * This is not safe to use for recursive function calls as you will ultimately
    * encounter a stack overflow. For those situations, use `trampoline`.
    */
  val direct: ExecutionContextExecutor = new ExecutionContextExecutor {
    def execute(runnable: Runnable): Unit =
      try runnable.run()
      catch { case t: Throwable => reportFailure(t) }

    def reportFailure(t: Throwable): Unit = ExecutionContext.defaultReporter(t)
  }

  /** A trampolining `ExecutionContext`
    *
    * This `ExecutionContext` is run thread locally to avoid context switches.
    * Because this is a thread-local executor, if there is a dependency between
    * the submitted `Runnable`s and the thread becomes blocked, there will be
    * a deadlock.
    */
  val trampoline: ExecutionContextExecutor = new ExecutionContextExecutor {
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
  }

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
