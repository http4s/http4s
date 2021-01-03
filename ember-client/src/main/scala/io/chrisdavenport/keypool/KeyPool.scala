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
import cats.implicits._
import cats.effect._
import scala.concurrent.duration._
import io.chrisdavenport.keypool.internal._

/** This pools internal guarantees are that the max number of values
  * are in the pool at any time, not maximum number of operations.
  * To do the latter application level bounds should be used.
  *
  * A background reaper thread is kept alive for the length of the key pools life.
  *
  * When resources are taken from the pool they are received as a [[Managed]].
  * This [[Managed]] has a Ref to a [[Reusable]] which indicates whether or not the pool
  * can reuse the resource.
  */

trait KeyPool[F[_], A, B] {

  /** Take a [[Managed]] from the Pool. For the lifetime of this
    * resource this is exclusively available to this key.
    *
    * At the end of the resource lifetime the state of the resource
    * controls whether it is submitted back to the pool or removed.
    */
  def take(k: A): Resource[F, Managed[F, B]]

  /** The current state of the pool.
    *
    * The left value is the total number of resources currently in
    * the pool, and the right is a map of how many resources exist
    * for each key.
    */
  def state: F[(Int, Map[A, Int])]
}

object KeyPool {

  private[keypool] final class KeyPoolConcrete[F[_]: Temporal: Ref.Make, A, B] private[keypool] (
      private[keypool] val kpCreate: A => F[B],
      private[keypool] val kpDestroy: B => F[Unit],
      private[keypool] val kpDefaultReuseState: Reusable,
      private[keypool] val kpMaxPerKey: A => Int,
      private[keypool] val kpMaxTotal: Int,
      private[keypool] val kpVar: Ref[F, PoolMap[A, B]]
  ) extends KeyPool[F, A, B] {

    def take(k: A): Resource[F, Managed[F, B]] =
      KeyPool.take(this, k)

    def state: F[(Int, Map[A, Int])] =
      KeyPool.state(kpVar)
  }

  //
  // Instances
  //
  implicit def keypoolFunctor[F[_]: Applicative, Z]: Functor[KeyPool[F, Z, *]] =
    new KPFunctor[F, Z]

  private class KPFunctor[F[_]: Applicative, Z] extends Functor[KeyPool[F, Z, *]] {
    override def map[A, B](fa: KeyPool[F, Z, A])(f: A => B): KeyPool[F, Z, B] =
      new KeyPool[F, Z, B] {
        def take(k: Z): Resource[F, Managed[F, B]] =
          fa.take(k).map(_.map(f))
        def state: F[(Int, Map[Z, Int])] = fa.state
      }
  }

  // Instance Is an AlleyCat Due to Map Functor instance
  // Must Explicitly Import
  def keypoolInvariant[F[_]: Functor, Z]: Invariant[KeyPool[F, *, Z]] =
    new KPInvariant[F, Z]

  private class KPInvariant[F[_]: Functor, Z] extends Invariant[KeyPool[F, *, Z]] {
    override def imap[A, B](fa: KeyPool[F, A, Z])(f: A => B)(g: B => A): KeyPool[F, B, Z] =
      new KeyPool[F, B, Z] {
        def take(k: B): Resource[F, Managed[F, Z]] =
          fa.take(g(k))
        def state: F[(Int, Map[B, Int])] = fa.state.map { case (total, m) =>
          (total, m.map { case (a, i) => (f(a), i) })
        }
      }
  }

  // Internal Helpers

  /** Make a 'KeyPool' inactive and destroy all idle resources.
    */
  private[keypool] def destroy[F[_]: MonadError[*[_], Throwable], A, B](
      kpDestroy: B => F[Unit],
      kpVar: Ref[F, PoolMap[A, B]]
  ): F[Unit] = for {
    m <- kpVar.getAndSet(PoolMap.closed[A, B])
    _ <- m match {
      case PoolClosed() => Applicative[F].unit
      case PoolOpen(_, m2) =>
        m2.toList.traverse_ { case (_, pl) =>
          pl.toList
            .traverse_ { case (_, r) =>
              kpDestroy(r).attempt.void
            }
        }
    }
  } yield ()

  /** Run a reaper thread, which will destroy old resources. It will
    * stop running once our pool switches to PoolClosed.
    */
  private[keypool] def reap[F[_]: Temporal, A, B](
      destroy: B => F[Unit],
      idleTimeAllowedInPoolNanos: FiniteDuration,
      kpVar: Ref[F, PoolMap[A, B]],
      onReaperException: Throwable => F[Unit]
  ): F[Unit] = {
    // We are going to do non-referentially tranpsarent things as we may be waiting for our modification to go through
    // which may change the state depending on when the modification block is running atomically at the moment
    def findStale(
        now: FiniteDuration,
        idleCount: Int,
        m: Map[A, PoolList[B]]): (PoolMap[A, B], List[(A, B)]) = {
      val isNotStale: FiniteDuration => Boolean =
        time =>
          time + idleTimeAllowedInPoolNanos >= now // Time value is alright inside the KeyPool in nanos.

      // Probably a more idiomatic way to do this in scala
      //([(key, PoolList resource)] -> [(key, PoolList resource)]) ->
      // ([resource] -> [resource]) ->
      // [(key, PoolList resource)] ->
      // (Map key (PoolList resource), [resource])
      def findStale_(
          toKeep: List[(A, PoolList[B])] => List[(A, PoolList[B])],
          toDestroy: List[(A, B)] => List[(A, B)],
          l: List[(A, PoolList[B])]
      ): (Map[A, PoolList[B]], List[(A, B)]) =
        l match {
          case Nil => (toKeep(List.empty).toMap, toDestroy(List.empty))
          case (key, pList) :: rest =>
            // Can use span since we know everything will be ordered as the time is
            // when it is placed back into the pool.
            val (notStale, stale) = pList.toList.span(r => isNotStale(r._1))
            val toDestroy_ : List[(A, B)] => List[(A, B)] = l =>
              toDestroy((stale.map(t => (key -> t._2)) ++ l))
            val toKeep_ : List[(A, PoolList[B])] => List[(A, PoolList[B])] = l =>
              PoolList.fromList(notStale) match {
                case None => toKeep(l)
                case Some(x) => toKeep((key, x) :: l)
              }
            findStale_(toKeep_, toDestroy_, rest)
        }
      // May be able to use Span eventually
      val (toKeep, toDestroy) = findStale_(identity, identity, m.toList)
      val idleCount_ = idleCount - toDestroy.length
      (PoolMap.open(idleCount_, toKeep), toDestroy)
    }

    val sleep = Temporal[F].sleep(5.seconds).void

    // Wait 5 Seconds
    def loop: F[Unit] = for {
      now <- Temporal[F].monotonic
      _ <-
        kpVar
          .tryModify {
            case p @ PoolClosed() => (p, Applicative[F].unit)
            case p @ PoolOpen(idleCount, m) =>
              if (m.isEmpty)
                (
                  p,
                  Applicative[F].unit
                ) // Not worth it to introduce deadlock concerns when hot loop is 5 seconds
              else {
                val (m_, toDestroy) = findStale(now, idleCount, m)
                (
                  m_,
                  toDestroy.traverse_(r => destroy(r._2)).attempt.flatMap {
                    case Left(t) =>
                      onReaperException(
                        t
                      ) // CHEATING .handleErrorWith(t => F.delay(t.printStackTrace()))
                    case Right(()) => Applicative[F].unit
                  })
              }
          }
          .flatMap {
            case Some(act) => act >> sleep >> loop
            case None => loop
          }
    } yield ()

    loop
  }

  private[keypool] def state[F[_]: Functor, A, B](
      kpVar: Ref[F, PoolMap[A, B]]): F[(Int, Map[A, Int])] =
    kpVar.get.map(pm =>
      pm match {
        case PoolClosed() => (0, Map.empty)
        case PoolOpen(idleCount, m) =>
          val modified = m.map { case (k, pl) =>
            pl match {
              case One(_, _) => (k, 1)
              case Cons(_, length, _, _) => (k, length)
            }
          }.toMap
          (idleCount, modified)
      })

  private[keypool] def put[F[_]: Temporal, A, B](
      kp: KeyPoolConcrete[F, A, B],
      k: A,
      r: B): F[Unit] = {
    def addToList[Z](
        now: FiniteDuration,
        maxCount: Int,
        x: Z,
        l: PoolList[Z]): (PoolList[Z], Option[Z]) =
      if (maxCount <= 1) (l, Some(x))
      else {
        l match {
          case l @ One(_, _) => (Cons(x, 2, now, l), None)
          case l @ Cons(_, currCount, _, _) =>
            if (maxCount > currCount) (Cons(x, currCount + 1, now, l), None)
            else (l, Some(x))
        }
      }
    def go(now: FiniteDuration, pc: PoolMap[A, B]): (PoolMap[A, B], F[Unit]) = pc match {
      case p @ PoolClosed() => (p, kp.kpDestroy(r))
      case p @ PoolOpen(idleCount, m) =>
        if (idleCount > kp.kpMaxTotal) (p, kp.kpDestroy(r))
        else
          m.get(k) match {
            case None =>
              val cnt_ = idleCount + 1
              val m_ = PoolMap.open(cnt_, m + (k -> One(r, now)))
              (m_, Applicative[F].pure(()))
            case Some(l) =>
              val (l_, mx) = addToList(now, kp.kpMaxPerKey(k), r, l)
              val cnt_ = idleCount + mx.fold(1)(_ => 0)
              val m_ = PoolMap.open(cnt_, m + (k -> l_))
              (m_, mx.fold(Applicative[F].unit)(r => kp.kpDestroy(r)))
          }
    }

    Clock[F].monotonic.flatMap { now =>
      kp.kpVar.modify(pm => go(now, pm)).flatten
    }
  }

  private[keypool] def take[F[_]: Temporal: Ref.Make, A, B](
      kp: KeyPoolConcrete[F, A, B],
      k: A): Resource[F, Managed[F, B]] = {
    def go(pm: PoolMap[A, B]): (PoolMap[A, B], Option[B]) = pm match {
      case p @ PoolClosed() => (p, None)
      case pOrig @ PoolOpen(idleCount, m) =>
        m.get(k) match {
          case None => (pOrig, None)
          case Some(One(a, _)) =>
            (PoolMap.open(idleCount - 1, m - k), Some(a))
          case Some(Cons(a, _, _, rest)) =>
            (PoolMap.open(idleCount - 1, m + (k -> rest)), Some(a))
        }
    }

    for {
      optR <- Resource.liftF(kp.kpVar.modify(go))
      releasedState <- Resource.liftF(Ref[F].of[Reusable](kp.kpDefaultReuseState))
      resource <- Resource.make(optR.fold(kp.kpCreate(k))(r => Applicative[F].pure(r))) {
        resource =>
          for {
            reusable <- releasedState.get
            out <- reusable match {
              case Reusable.Reuse => put(kp, k, resource).attempt.void
              case Reusable.DontReuse => kp.kpDestroy(resource).attempt.void
            }
          } yield out
      }
    } yield new Managed(resource, optR.isDefined, releasedState)
  }
}
