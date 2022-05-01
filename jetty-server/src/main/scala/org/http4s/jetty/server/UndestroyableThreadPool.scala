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

import org.eclipse.jetty.util.thread.ThreadPool

/** A [[org.eclipse.jetty.util.thread.ThreadPool]] which does not implement
  * [[[[org.eclipse.jetty.util.component.Destroyable]]]] and with a `join`
  * method that does not do anything.
  *
  * We use this class to ensure that code compiled against http4s < 0.21.23
  * will have consistent semantics. Otherwise, attempts to run
  * [[JettyBuilder#resource]] more than once would fail as the
  * [[org.eclipse.jetty.util.thread.ThreadPool]] would have been shut down
  * after the first invocation completed. This is a side effect of cleaning up
  * the resource leak by invoking `.destroy` on the Jetty Server when the
  * resource is completed.
  */
private[jetty] final class UndestroyableThreadPool(value: ThreadPool) extends ThreadPool {
  override final def getIdleThreads: Int = value.getIdleThreads

  override final def getThreads: Int = value.getThreads

  override final def isLowOnThreads: Boolean = value.isLowOnThreads

  override final def execute(runnable: Runnable): Unit =
    value.execute(runnable)

  override final def join: Unit = ()
}
