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

import cats.effect.Concurrent
import cats.effect.std.Semaphore
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.Stream
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef

private[internal] object StreamForking {

  /** forking has similar semantics to parJoin, but there are two key differences.
    * The first is that inner stream outputs are not shuffled to the forked stream.
    * The second is that the outer stream may terminate and finalize before inner
    * streams complete. This is generally unsafe, because inner streams are lexically
    * scoped within the outer stream and accordingly has resources bound to the outer
    * stream available in scope. However, network servers built on top of fs2.io can
    * safely utilize this because inner streams are created fresh from socket Resources
    * that don't close over any resources from the outer stream.
    *
    * One important semantic that the previous prefetch trick didn't have was backpressuring
    * the outer stream from continuing if the max concurrency is reached, which has been
    * recovered here.
    */
  def forking[F[_], O](streams: Stream[F, Stream[F, O]], maxConcurrency: Int = Int.MaxValue)(
      implicit F: Concurrent[F]
  ): Stream[F, Nothing] = {
    val fstream = for {
      done <- SignallingRef[F, Option[Option[Throwable]]](None)
      available <- Semaphore[F](maxConcurrency.toLong)
      running <- SignallingRef[F, Long](1)
    } yield {
      val incrementRunning: F[Unit] = running.update(_ + 1)
      val decrementRunning: F[Unit] = running.update(_ - 1)
      val awaitWhileRunning: F[Unit] = running.discrete.dropWhile(_ > 0).take(1).compile.drain

      val stop: F[Unit] = done.update(_.orElse(Some(None)))
      val stopSignal: Signal[F, Boolean] = done.map(_.nonEmpty)
      def stopFailed(err: Throwable): F[Unit] =
        done.update(_.orElse(Some(Some(err))))

      def runInner(inner: Stream[F, O]): F[Unit] = {
        val fa = inner
          .interruptWhen(stopSignal)
          .compile
          .drain
          .handleErrorWith(stopFailed)
          .guarantee(available.release >> decrementRunning)

        F.uncancelable(_ => available.acquire >> incrementRunning >> F.start(fa).void)
      }

      val runOuter: F[Unit] =
        streams
          .foreach(runInner(_))
          .interruptWhen(stopSignal)
          .compile
          .drain
          .handleErrorWith(stopFailed) >> decrementRunning

      val signalResult: F[Unit] =
        done.get.flatMap {
          case Some(Some(err)) => F.raiseError(err)
          case _ => F.unit
        }

      Stream.bracket(F.start(runOuter))(_ => stop >> awaitWhileRunning >> signalResult) >>
        Stream.eval(awaitWhileRunning).drain
    }

    Stream.eval(fstream).flatten
  }

}
