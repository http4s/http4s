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
import cats.effect.Concurrent
import cats.effect.Fiber
import cats.effect.kernel.Poll
import cats.effect.kernel.Unique
import cats.effect.Deferred
import cats.effect.Outcome
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.std.Queue
import cats.syntax.all._
import fs2.Chunk
import fs2.Compiler
import fs2.CompositeFailure
import fs2.Pull
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

    /** Keep source scopes in an adapter-owned compiler so cleanup errors are retained
      * before a native uploader can discard them. Demand gates every source pull.
      * Consumer completion closes scopes at that gate; errors and cancellation can
      * interrupt an active pull with the original exit. EOF follows source finalization.
      */
    def source[A](operation: String)(body: Stream[F, A]): Stream[F, A] =
      Stream.resource {
        for {
          stop <- Resource.eval(Deferred[F, Resource.ExitCase])
          requests <- Resource.eval(Queue.synchronous[F, Deferred[F, Option[Chunk[A]]]])
          done <- Resource.eval(Deferred[F, Outcome[F, Throwable, Unit]])
          run = {
            def next(stream: Stream[F, A]): Pull[F, Nothing, Unit] =
              Pull.eval(F.race(stop.get, requests.take)).flatMap {
                case Left(Resource.ExitCase.Errored(error)) => Pull.raiseError(error)
                case Left(Resource.ExitCase.Succeeded) => Pull.done
                case Left(Resource.ExitCase.Canceled) => Pull.eval(F.never[Unit])
                case Right(reply) =>
                  stream.pull.uncons.flatMap {
                    case None => Pull.done
                    case Some((chunk, tail)) =>
                      Pull.eval(reply.complete(Some(chunk)).void) >> next(tail)
                  }
              }

            def fixtureErrors(error: Throwable, primary: Throwable): List[Throwable] = {
              def leaves(error: Throwable): List[Throwable] = error match {
                case composite: CompositeFailure =>
                  composite.all.toList.flatMap(leaves)
                case error => List(error)
              }
              // FS2 flattens composite primaries when combining failures. Remove only
              // their injected occurrences: cleanup may independently raise them again.
              leaves(primary).foldLeft(leaves(error)) { (remaining, injected) =>
                val (before, rest) = remaining.span(_ ne injected)
                before ::: rest.drop(1)
              }
            }

            val interrupted = stop.get.flatMap {
              case Resource.ExitCase.Canceled => F.pure(Right(()): Either[Throwable, Unit])
              case Resource.ExitCase.Errored(error) => F.pure(Left(error): Either[Throwable, Unit])
              case Resource.ExitCase.Succeeded => F.never[Either[Throwable, Unit]]
            }
            def sourceEffect[B](effect: F[B], wrap: Boolean): F[B] =
              effect.handleErrorWith { error =>
                stop.tryGet.flatMap { exit =>
                  val fixture = exit match {
                    case Some(Resource.ExitCase.Errored(primary)) =>
                      // Injected downstream errors belong to the native consumer.
                      CompositeFailure.fromList(fixtureErrors(error, primary))
                    case _ => Some(error)
                  }
                  fixture match {
                    case None => F.raiseError[B](error)
                    case Some(cleanup) =>
                      val failure = find(cleanup).getOrElse(new AdapterFailure(operation, cleanup))
                      F.uncancelable(_ =>
                        retain(failure) *> F.raiseError[B](if (wrap) failure else error)
                      )
                  }
                }
              }

            // Observe the ordinary compiler's cancellation release before the
            // runtime reports its error. Source effects and internal recovery use
            // their original F; all other compiler operations delegate unchanged.
            val compiling = new Concurrent[F] {
              def pure[B](value: B): F[B] = F.pure(value)
              def flatMap[B, C](effect: F[B])(next: B => F[C]): F[C] = F.flatMap(effect)(next)
              def tailRecM[B, C](value: B)(next: B => F[Either[B, C]]): F[C] =
                F.tailRecM(value)(next)
              def raiseError[B](error: Throwable): F[B] = F.raiseError(error)
              def handleErrorWith[B](effect: F[B])(recover: Throwable => F[B]): F[B] =
                F.handleErrorWith(effect)(recover)
              def forceR[B, C](effect: F[B])(next: F[C]): F[C] = F.forceR(effect)(next)
              def uncancelable[B](body: Poll[F] => F[B]): F[B] = F.uncancelable(body)
              def canceled: F[Unit] = F.canceled
              def onCancel[B](effect: F[B], finalize: F[Unit]): F[B] =
                F.onCancel(effect, sourceEffect(finalize, wrap = false))
              def unique: F[Unique.Token] = F.unique
              def start[B](effect: F[B]): F[Fiber[F, Throwable, B]] = F.start(effect)
              def never[B]: F[B] = F.never
              def cede: F[Unit] = F.cede
              override def racePair[B, C](left: F[B], right: F[C]): F[Either[
                (Outcome[F, Throwable, B], Fiber[F, Throwable, C]),
                (Fiber[F, Throwable, B], Outcome[F, Throwable, C]),
              ]] = F.racePair(left, right)
              def ref[B](value: B): F[Ref[F, B]] = F.ref(value)
              def deferred[B]: F[Deferred[F, B]] = F.deferred
            }
            val compile = sourceEffect(
              next(body).stream
                .interruptWhen(interrupted)
                .compile(Compiler.target(Compiler.Target.forConcurrent(compiling)))
                .drain,
              wrap = true,
            )
            F.guaranteeCase(compile)(result => done.complete(result).void).attempt.void
          }
          _ <- Resource.makeCase(F.start(run)) { (fiber, exit) =>
            stop.complete(exit).void *> fiber.join *> done.get.flatMap {
              case Outcome.Errored(error) =>
                exit match {
                  case Resource.ExitCase.Errored(primary) if error eq primary => F.unit
                  case _ => F.raiseError[Unit](error)
                }
              case _ => F.unit
            }
          }
        } yield Stream
          .repeatEval {
            Deferred[F, Option[Chunk[A]]].flatMap { reply =>
              F.race(done.get, requests.offer(reply) *> reply.get).flatMap {
                case Left(Outcome.Succeeded(result)) => result.as(Option.empty[Chunk[A]])
                case Left(Outcome.Errored(error)) => F.raiseError[Option[Chunk[A]]](error)
                case Left(Outcome.Canceled()) => F.canceled *> F.never[Option[Chunk[A]]]
                case Right(chunk) => F.pure(chunk)
              }
            }
          }
          .unNoneTerminate
          .flatMap(Stream.chunk)
      }.flatten

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
