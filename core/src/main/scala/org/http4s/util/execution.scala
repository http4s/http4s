package org.http4s.util

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

@deprecated("Not related to HTTP. Will be removed from public API.", "0.21.4")
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
  val trampoline: ExecutionContextExecutor = org.http4s.internal.Trampoline
}
