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

package io.chrisdavenport.keypool

import cats._
import cats.effect._
import cats.implicits._
import io.chrisdavenport.keypool.internal.{PoolList, PoolMap}

import scala.concurrent.duration._

final class KeyPoolBuilder[F[_]: Temporal: Ref.Make, A, B] private (
    val kpCreate: A => F[B],
    val kpDestroy: B => F[Unit],
    val kpDefaultReuseState: Reusable,
    val idleTimeAllowedInPool: Duration,
    val kpMaxPerKey: A => Int,
    val kpMaxTotal: Int,
    val onReaperException: Throwable => F[Unit]
) {
  private def copy(
      kpCreate: A => F[B] = this.kpCreate,
      kpDestroy: B => F[Unit] = this.kpDestroy,
      kpDefaultReuseState: Reusable = this.kpDefaultReuseState,
      idleTimeAllowedInPool: Duration = this.idleTimeAllowedInPool,
      kpMaxPerKey: A => Int = this.kpMaxPerKey,
      kpMaxTotal: Int = this.kpMaxTotal,
      onReaperException: Throwable => F[Unit] = this.onReaperException
  ): KeyPoolBuilder[F, A, B] = new KeyPoolBuilder[F, A, B](
    kpCreate,
    kpDestroy,
    kpDefaultReuseState,
    idleTimeAllowedInPool,
    kpMaxPerKey,
    kpMaxTotal,
    onReaperException
  )

  def doOnCreate(f: B => F[Unit]): KeyPoolBuilder[F, A, B] =
    copy(kpCreate = { k: A => this.kpCreate(k).flatMap(v => f(v).attempt.void.as(v)) })

  def doOnDestroy(f: B => F[Unit]): KeyPoolBuilder[F, A, B] =
    copy(kpDestroy = { r: B => f(r).attempt.void >> this.kpDestroy(r) })

  def withDefaultReuseState(defaultReuseState: Reusable) =
    copy(kpDefaultReuseState = defaultReuseState)

  def withIdleTimeAllowedInPool(duration: Duration) =
    copy(idleTimeAllowedInPool = duration)

  def withMaxPerKey(f: A => Int): KeyPoolBuilder[F, A, B] =
    copy(kpMaxPerKey = f)

  def withMaxTotal(total: Int): KeyPoolBuilder[F, A, B] =
    copy(kpMaxTotal = total)

  def withOnReaperException(f: Throwable => F[Unit]) =
    copy(onReaperException = f)

  def build: Resource[F, KeyPool[F, A, B]] = {
    def keepRunning[Z](fa: F[Z]): F[Z] =
      fa.onError { case e => onReaperException(e) }.attempt >> keepRunning(fa)
    for {
      kpVar <- Resource.make(
        Ref[F].of[PoolMap[A, B]](PoolMap.open(0, Map.empty[A, PoolList[B]]))
      )(kpVar => KeyPool.destroy(kpDestroy, kpVar))
      _ <- idleTimeAllowedInPool match {
        case fd: FiniteDuration =>
          val n = math.max(0L, fd.toNanos)
          Resource.make(
            Concurrent[F].start(
              keepRunning(KeyPool.reap(kpDestroy, n.nanos, kpVar, onReaperException))))(_.cancel)
        case _ =>
          Applicative[Resource[F, *]].unit
      }
    } yield new KeyPool.KeyPoolConcrete(
      kpCreate,
      kpDestroy,
      kpDefaultReuseState,
      kpMaxPerKey,
      kpMaxTotal,
      kpVar
    )
  }

}

object KeyPoolBuilder {
  def apply[F[_]: Temporal: Ref.Make, A, B](
      create: A => F[B],
      destroy: B => F[Unit]
  ): KeyPoolBuilder[F, A, B] = new KeyPoolBuilder[F, A, B](
    create,
    destroy,
    Defaults.defaultReuseState,
    Defaults.idleTimeAllowedInPool,
    Defaults.maxPerKey,
    Defaults.maxTotal,
    Defaults.onReaperException[F]
  )

  private object Defaults {
    val defaultReuseState = Reusable.Reuse
    val idleTimeAllowedInPool = 30.seconds
    def maxPerKey[K](k: K): Int = Function.const(100)(k)
    val maxTotal = 100
    def onReaperException[F[_]: Applicative] = { t: Throwable =>
      Function.const(Applicative[F].unit)(t)
    }
  }
}
