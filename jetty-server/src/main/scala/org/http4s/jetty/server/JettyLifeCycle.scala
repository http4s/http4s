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
import cats.syntax.all._
import org.eclipse.jetty.util.component.Destroyable
import org.eclipse.jetty.util.component.LifeCycle

private[jetty] object JettyLifeCycle {

  /** Wrap a Jetty [[org.eclipse.jetty.util.component.LifeCycle]] value in a
    * [[cats.effect.Resource]]. This calls
    * [[org.eclipse.jetty.util.component.LifeCycle#start]] on startup and
    * waits for the component to emit an event stating it is started, failing
    * if it is already started (or starting). On release
    * [[org.eclipse.jetty.util.component.LifeCycle#stop]] is called.
    *
    * @note If `A` is _also_ a subtype of
    *       [[org.eclipse.jetty.util.component.Destroyable]] then
    *       [[org.eclipse.jetty.util.component.Destroyable#destroy]] will also
    *       be invoked.
    */
  def lifeCycleAsResource[F[_], A <: LifeCycle](
      fa: F[A]
  )(implicit F: Async[F]): Resource[F, A] =
    Resource.make[F, A](
      fa.flatTap(startLifeCycle[F])
    ) {
      case value: LifeCycle with Destroyable =>
        stopLifeCycle[F](value) *> F.delay(value.destroy())
      case otherwise =>
        stopLifeCycle[F](otherwise)
    }

  /** Attempt to invoke [[org.eclipse.jetty.util.component.LifeCycle#stop]] on a
    * [[org.eclipse.jetty.util.component.LifeCycle]]. If the
    * [[org.eclipse.jetty.util.component.LifeCycle]] is already stopped then
    * this method will return immediately. This can be valid in some cases
    * where a [[org.eclipse.jetty.util.component.LifeCycle]] is stopped
    * internally, e.g. due to some internal error occurring.
    */
  private[this] def stopLifeCycle[F[_]](lifeCycle: LifeCycle)(implicit F: Async[F]): F[Unit] =
    F.asyncF[Unit] { cb =>
      F.defer {
        val listener =
          new LifeCycle.Listener {
            override def lifeCycleStopped(a: LifeCycle): Unit =
              cb(Right(()))

            override def lifeCycleFailure(a: LifeCycle, error: Throwable): Unit =
              cb(Left(error))
          }
        lifeCycle.addEventListener(listener)

        // In the general case, it is not sufficient to merely call stop(). For
        // example, the concrete implementation of stop() for the canonical
        // Jetty Server instance will shortcut to a `return` call taking no
        // action if the server is "stopping". This method _can't_ return until
        // we are _actually stopped_, so we have to check three different states
        // here.

        if (lifeCycle.isStopped) {
          // If the first case, we are already stopped, so our listener won't be
          // called and we just return.
          cb(Right(()))
        } else if (lifeCycle.isStopping()) {
          // If it is stopping, we need to wait for our listener to get invoked.
          ()
        } else {
          // If it is neither stopped nor stopping, we need to request a stop
          // and then wait for the event. It is imperative that we add the
          // listener beforehand here. Otherwise we have some very annoying race
          // conditions.
          lifeCycle.stop()
        }
        F.delay(lifeCycle.removeEventListener(listener)).void
      }
    }

  /** Attempt to [[org.eclipse.jetty.util.component.LifeCycle#start]] on a
    * [[org.eclipse.jetty.util.component.LifeCycle]].
    *
    * If the [[org.eclipse.jetty.util.component.LifeCycle]] is already started
    * (or starting) this will fail.
    */
  private[this] def startLifeCycle[F[_]](lifeCycle: LifeCycle)(implicit F: Async[F]): F[Unit] =
    F.asyncF[Unit] { cb =>
      F.defer {
        val listener =
          new LifeCycle.Listener {
            override def lifeCycleStarted(a: LifeCycle): Unit =
              cb(Right(()))

            override def lifeCycleFailure(a: LifeCycle, error: Throwable): Unit =
              cb(Left(error))
          }
        lifeCycle.addEventListener(listener)

        // Sanity check to ensure the LifeCycle component is not already
        // started. A couple of notes here.
        //
        // - There is _always_ going to be a small chance of a race condition
        //   here in the final branch where we invoke `lifeCycle.start()` _if_
        //   something else has a reference to the `LifeCycle`
        //   value. Thankfully, unlike the stopLifeCycle function, this is
        //   completely in the control of the caller. As long as the caller
        //   doesn't leak the reference (or call .start() themselves) nothing
        //   internally to Jetty should ever invoke .start().
        // - Jetty components allow for reuse in many cases, unless the
        //   .destroy() method is invoked (and the particular type implements
        //   `Destroyable`, it's not part of `LifeCycle`). Jetty uses this for
        //   "soft" resets of the `LifeCycle` component. Thus it is possible
        //   that this `LifeCycle` component has been started before, though I
        //   don't recommend this and we don't (at this time) do that in the
        //   http4s codebase.
        if (lifeCycle.isStarted) {
          cb(
            Left(
              new IllegalStateException(
                "Attempting to start Jetty LifeCycle component, but it is already started."
              )
            )
          )
        } else if (lifeCycle.isStarting) {
          cb(
            Left(
              new IllegalStateException(
                "Attempting to start Jetty LifeCycle component, but it is already starting."
              )
            )
          )
        } else {
          lifeCycle.start()
        }
        F.delay(lifeCycle.removeEventListener(listener)).void
      }
    }
}
