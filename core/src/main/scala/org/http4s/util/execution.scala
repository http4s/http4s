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
