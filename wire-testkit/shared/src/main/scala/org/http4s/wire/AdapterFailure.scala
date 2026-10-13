/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

import cats.ApplicativeThrow
import cats.effect.Async
import cats.effect.Ref
import cats.effect.Resource
import cats.syntax.all._
import fs2.Stream

/** A failure of the fixture binding or its cleanup, never evidence of protocol rejection. */
final class AdapterFailure(message: String, cause: Throwable)
    extends RuntimeException(message, cause)

object AdapterFailure {
  private[wire] def find(error: Throwable): Option[AdapterFailure] = {
    @annotation.tailrec
    def loop(pending: List[Throwable], seen: Set[Throwable]): Option[AdapterFailure] =
      pending match {
        case Nil => None
        case (failure: AdapterFailure) :: _ => Some(failure)
        case null :: rest => loop(rest, seen)
        case current :: rest if seen(current) => loop(rest, seen)
        case current :: rest =>
          loop(current.getCause :: current.getSuppressed.toList ::: rest, seen + current)
      }
    loop(List(error), Set.empty)
  }

  def raiseIfPresent[F[_]: ApplicativeThrow](error: Throwable): F[Unit] =
    find(error).fold(ApplicativeThrow[F].unit)(ApplicativeThrow[F].raiseError[Unit])

  /** Own the journal outside the case deadline so canceled resource cleanup is observed too. */
  private[wire] def observe[F[_]: Async, A](target: WireTarget[F])(
      run: WireTarget[F] => F[A]
  ): F[A] =
    Ref.of[F, Option[AdapterFailure]](None).flatMap { state =>
      val monitored = target.withFailureObserver(failure => state.update(_.orElse(Some(failure))))
      Async[F].uncancelable { poll =>
        poll(Async[F].defer(run(monitored))).attempt.flatMap { result =>
          state.get.flatMap {
            case None => result.liftTo[F]
            case Some(failure) =>
              val error = result match {
                case Left(primary) if primary ne failure =>
                  // A fresh wrapper cannot form a cycle with either existing throwable graph.
                  val combined = new AdapterFailure(failure.getMessage, failure)
                  combined.addSuppressed(primary)
                  combined
                case _ => failure
              }
              Async[F].raiseError[A](error)
          }
        }
      }
    }

  /** Resource.use keeps the primary error when release also fails. Attach provenance
    * before returning so it remains observable even on that primary-error path.
    * Cancellation remains cancellation; a failing canceled release is runtime-reported.
    */
  private[wire] def release[F[_]: Async](
      effect: F[Unit],
      exit: Resource.ExitCase,
      operation: String,
      observer: Option[AdapterFailure => F[Unit]] = None,
  ): F[Unit] =
    effect.handleErrorWith { error =>
      val failure = find(error).getOrElse(new AdapterFailure(operation, error))
      observer.traverse_(_(failure)) *> (exit match {
        case Resource.ExitCase.Errored(primary) =>
          Async[F].delay(if (primary ne failure) primary.addSuppressed(failure))
        case _ => Async[F].raiseError(failure)
      })
    }

  private[wire] def make[F[_]: Async, A](
      acquire: F[A],
      observer: Option[AdapterFailure => F[Unit]] = None,
  )(
      finalize: A => F[Unit]
  ): Resource[F, A] =
    Resource.makeCase(acquire)((value, exit) =>
      release(finalize(value), exit, "releasing adapter resource", observer)
    )

  /** Native cleanup during failed partial acquisition keeps its native provenance. */
  private[wire] def guardRelease[F[_]: Async, A](
      resource: Resource[F, A],
      operation: String,
      observer: Option[AdapterFailure => F[Unit]] = None,
  ): Resource[F, A] =
    Resource
      .makeCaseFull[F, (A, Resource.ExitCase => F[Unit])](poll => poll(resource.allocatedCase)) {
        case ((_, finalize), exit) =>
          release(finalize(exit), exit, operation, observer)
      }
      .map(_._1)

  /** Resource-local storage independent of response completion. Only fixture-owned
    * operations are captured; a canceled, unfinished operation records no failure.
    */
  private[wire] final class Recorder[F[_]] private[AdapterFailure] (
      state: Ref[F, Option[AdapterFailure]],
      observer: Option[AdapterFailure => F[Unit]],
  )(implicit F: Async[F]) {
    def retain(error: Throwable): F[Unit] =
      find(error).traverse_(failure =>
        state.update(_.orElse(Some(failure))) *> observer.traverse_(_(failure))
      )

    def capture[A](operation: String)(effect: F[A]): F[A] =
      effect.handleErrorWith { error =>
        val failure = find(error).getOrElse(new AdapterFailure(operation, error))
        F.uncancelable(_ => retain(failure) *> F.raiseError[A](failure))
      }

    val fixtureCleanup: FixtureCleanup[F] =
      new FixtureCleanup[F](effect => capture("releasing fixture source")(effect))

    def source[A](operation: String)(body: Stream[F, A]): Stream[F, A] =
      MonitoredSource(operation, retain)(body)

    private[AdapterFailure] def check: F[Unit] =
      state.get.flatMap(_.traverse_(F.raiseError[Unit]))
  }

  private[wire] def recorder[F[_]: Async](
      observer: Option[AdapterFailure => F[Unit]] = None
  ): Resource[F, Recorder[F]] =
    make(Ref.of[F, Option[AdapterFailure]](None).map(new Recorder[F](_, observer)), observer)(
      _.check
    )
}
