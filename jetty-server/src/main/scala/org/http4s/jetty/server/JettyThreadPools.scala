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

import cats.effect._
import org.eclipse.jetty.util.component.LifeCycle
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.eclipse.jetty.util.thread.ThreadPool

object JettyThreadPools {

  /** Create a resource for a Jetty
    * [[org.eclipse.jetty.util.thread.ThreadPool]]. It will be "shutdown" when
    * the resource is closed.
    *
    * Jetty [[org.eclipse.jetty.util.thread.ThreadPool]] have some rather
    * unusual properties. [[org.eclipse.jetty.util.thread.ThreadPool]] itself
    * does not implement any of the standard JVM or Jetty lifecycle systems,
    * e.g. [[java.io.Closeable]] or
    * [[org.eclipse.jetty.util.component.LifeCycle]], but ''all'' the concrete
    * implementations of it provided by Jetty ''do'' implement
    * [[org.eclipse.jetty.util.component.LifeCycle]].
    *
    * The [[cats.effect.Resource]] implemented here will use the
    * [[org.eclipse.jetty.util.component.LifeCycle]] shutdown semantics if the
    * underlying [[org.eclipse.jetty.util.thread.ThreadPool]] implements that
    * interface. Otherwise it will invoke
    * [[org.eclipse.jetty.util.thread.ThreadPool#join]] on shutdown, making
    * startup a no-op.
    *
    * @note It is expected and important that the
    *       [[org.eclipse.jetty.util.thread.ThreadPool]] provided to this
    *       function ''has not been started''. If it has and it implements
    *       [[org.eclipse.jetty.util.component.LifeCycle]], then the creation
    *       of the [[cats.effect.Resource]] will fail.
    */
  def resource[F[_]](value: F[ThreadPool])(implicit F: Async[F]): Resource[F, ThreadPool] =
    Resource.eval(value).flatMap {
      // I know, this is gross.
      case value: ThreadPool with LifeCycle =>
        JettyLifeCycle.lifeCycleAsResource[F, ThreadPool with LifeCycle](F.pure(value))
      case value: ThreadPool =>
        Resource.make(F.pure(value))(value => F.blocking(value.join()))
    }

  /** The default [[org.eclipse.jetty.util.thread.ThreadPool]] used by
    * [[JettyBuilder]]. If you require specific constraints on this (a certain
    * number of threads, etc.) please use [[resource]] and take a look at the
    * concrete implementations of [[org.eclipse.jetty.util.thread.ThreadPool]]
    * provided by Jetty.
    */
  def default[F[_]](implicit F: Async[F]): Resource[F, ThreadPool] =
    resource[F](F.delay(new QueuedThreadPool))
}
