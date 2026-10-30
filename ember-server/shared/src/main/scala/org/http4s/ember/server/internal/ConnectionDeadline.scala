/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.server.internal

import cats.effect.kernel.Ref
import cats.effect.kernel.Temporal
import cats.syntax.all._

import scala.concurrent.duration._

/** A deadline for the phases of a connection whose timeout only closes the connection:
  * waiting for the next request on a kept-alive connection, and receiving the request header.
  *
  * Racing a timer against each such operation starts two fibers per operation, for every request.
  * Instead, an operation records its deadline, and a single long-lived fiber per connection
  * ([[expired]]) sleeps until the deadline has passed, at which point the connection is closed.
  */
private[internal] final class ConnectionDeadline[F[_]] private (
    deadline: Ref[F, Long],
    period: FiniteDuration,
)(implicit F: Temporal[F]) {
  import ConnectionDeadline.Disarmed

  private[this] val disarm: F[Unit] = deadline.set(Disarmed)

  /** Runs `fa`, and lets [[expired]] complete if `fa` is not done within `timeout`. */
  def within[A](fa: F[A], timeout: Duration): F[A] =
    timeout match {
      case fd: FiniteDuration =>
        F.monotonic.flatMap { now =>
          val at = now.toNanos + fd.toNanos
          // saturate on overflow, for very large timeouts
          deadline.set(if (at < 0L) Disarmed - 1L else at)
        } >> (fa <* disarm).onError { case _ => disarm }
      case _ => fa
    }

  /** Completes when an operation run [[within]] a timeout has not finished in time. */
  val expired: F[Unit] = {
    def loop: F[Unit] = (deadline.get, F.monotonic).flatMapN { (at, now) =>
      val nanos = now.toNanos
      if (nanos >= at) F.unit
      else if (at == Disarmed) F.sleep(period) >> loop
      else F.sleep((at - nanos).nanos) >> loop
    }
    loop
  }
}

private[internal] object ConnectionDeadline {
  private val Disarmed = Long.MaxValue

  /** A [[ConnectionDeadline]] for the given timeouts, or `None` if they are all infinite.
    *
    * While no deadline is set, [[ConnectionDeadline.expired]] wakes up every shortest timeout,
    * so that a deadline set in the meantime is never missed.
    */
  def apply[F[_]](timeouts: Duration*)(implicit
      F: Temporal[F]
  ): F[Option[ConnectionDeadline[F]]] =
    timeouts.collect { case fd: FiniteDuration => fd }.reduceOption(_ min _) match {
      case Some(shortest) =>
        F.ref(Disarmed).map { deadline =>
          Some(new ConnectionDeadline[F](deadline, shortest.max(1.millisecond)))
        }
      case None => F.pure(None)
    }
}
