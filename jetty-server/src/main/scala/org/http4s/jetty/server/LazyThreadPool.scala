/*
 * Copyright 2014 http4s.org
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

package org.http4s.jetty.server

import cats._
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.eclipse.jetty.util.thread.ThreadPool

/** A lazy [[org.eclipse.jetty.util.thread.ThreadPool]]. The pool will not be
  * started until one of the methods on it is invoked.
  *
  * @note This is only provided to as a safety mechanism for the legacy
  *       methods in [[JettyBuilder]] which operated directly on a
  *       [[org.eclipse.jetty.util.thread.ThreadPool]]. If you are not using
  *       the default [[org.eclipse.jetty.util.thread.ThreadPool]] you should
  *       be using [[JettyThreadPools]] to build a [[cats.effect.Resource]]
  *       for the pool.
  */
private[jetty] object LazyThreadPool {

  def newLazyThreadPool: ThreadPool =
    new ThreadPool {

      private val value: Eval[ThreadPool] = Eval.later(new QueuedThreadPool())

      override final def getIdleThreads: Int = value.value.getIdleThreads

      override final def getThreads: Int = value.value.getThreads

      override final def isLowOnThreads: Boolean = value.value.isLowOnThreads

      override final def execute(runnable: Runnable): Unit =
        value.value.execute(runnable)

      override final def join: Unit =
        value.value.join
    }
}
