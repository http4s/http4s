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

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Outcome
import cats.effect.Ref
import cats.effect.Resource
import cats.syntax.all._
import fs2.CompositeFailure
import fs2.Stream
import munit.CatsEffectSuite

import scala.concurrent.duration._

class MonitoredSourceSuite extends CatsEffectSuite {
  override val munitIOTimeout: Duration = 20.seconds

  test("source ownership preserves demand and successful truncation exit cases") {
    for {
      reads <- Ref.of[IO, Int](0)
      exit <- Deferred[IO, Resource.ExitCase]
      body = Stream
        .repeatEval(reads.update(_ + 1).as(42.toByte))
        .onFinalizeCase(value => exit.complete(value).void)
      _ <- AdapterFailure.recorder[IO]().use { failures =>
        val source = failures.source("source")(body)
        reads.get.assertEquals(0) *> source.take(1).compile.toVector.assertEquals(Vector(42.toByte))
      }
      _ <- reads.get.assertEquals(1)
      _ <- exit.get.assertEquals(Resource.ExitCase.Succeeded)
    } yield ()
  }

  Vector(
    "success",
    "truncation",
    "downstream error",
    "downstream interruption",
    "external cancellation",
    "source cancellation",
  )
    .foreach { scenario =>
      test(s"source ownership matches direct stream lifecycle: $scenario") {
        val native = new java.io.IOException("downstream native failure")
        def run(wrapped: Boolean): IO[(Resource.ExitCase, Resource.ExitCase)] =
          for {
            started <- Deferred[IO, Unit]
            exit <- Deferred[IO, Resource.ExitCase]
            body = (Stream.eval(started.complete(()).as(42)) ++ (scenario match {
              case "success" => Stream.empty
              case "source cancellation" => Stream.exec(IO.canceled)
              case _ => Stream.never[IO]
            })).onFinalizeCase(value => exit.complete(value).void)
            outcome <- Resource
              .make(
                AdapterFailure
                  .recorder[IO]()
                  .use { failures =>
                    val source = if (wrapped) failures.source("source")(body) else body
                    val consumed = scenario match {
                      case "truncation" => source.take(1)
                      case "downstream error" => source.evalMap(_ => IO.raiseError[Int](native))
                      case "downstream interruption" =>
                        source.interruptWhen(started.get.as(Left(native): Either[Throwable, Unit]))
                      case _ => source
                    }
                    consumed.compile.drain
                  }
                  .start
              )(_.cancel)
              .use { fiber =>
                (if (scenario == "external cancellation") started.get *> fiber.cancel
                 else IO.unit) *>
                  fiber.join.flatMap {
                    case Outcome.Succeeded(result) => result.as(Resource.ExitCase.Succeeded)
                    case Outcome.Errored(error) =>
                      IO(assert(AdapterFailure.find(error).isEmpty))
                        .as(Resource.ExitCase.Errored(error))
                    case Outcome.Canceled() => IO.pure(Resource.ExitCase.Canceled)
                  }
              }
            finalized <- exit.get
          } yield (outcome, finalized)
        (run(false), run(true)).tupled
          .flatMap { case (direct, wrapped) =>
            IO(assertEquals(wrapped, direct))
          }
          .timeout(2.seconds)
      }
    }

  Vector(false, true).foreach { compositePrimary =>
    Vector("successful", "distinct", "same instance", "nested composite").foreach { cleanup =>
      test(s"source cleanup retains independent failures: $cleanup, composite=$compositePrimary") {
        val first = new java.io.IOException("downstream native failure")
        val second = new java.io.IOException("another downstream native failure")
        val native = if (compositePrimary) CompositeFailure(first, second, List(first)) else first
        val nativeErrors = if (compositePrimary) List(first, second, first) else List(first)
        val separate = new java.io.IOException("source cleanup")
        val (cleanupError, expected) = cleanup match {
          case "successful" => (None, Nil)
          case "distinct" => (Some(separate), List(separate))
          case "same instance" => (Some(native), nativeErrors)
          case _ =>
            // copy preserves actual nesting, unlike CompositeFailure.apply's flattening.
            val nested = CompositeFailure(separate, separate)
              .copy(head = CompositeFailure(native, native))
            (Some(nested), nativeErrors ++ nativeErrors :+ separate)
        }
        def leaves(error: Throwable): List[Throwable] = error match {
          case composite: CompositeFailure => composite.all.toList.flatMap(leaves)
          case error => List(error)
        }
        for {
          observed <- Ref.of[IO, Vector[AdapterFailure]](Vector.empty)
          exit <- Deferred[IO, Resource.ExitCase]
          body = (Stream.emit(42).covary[IO] ++ Stream.never[IO]).onFinalizeCase { value =>
            exit.complete(value).void *> cleanupError.traverse_(IO.raiseError[Unit])
          }
          result <- AdapterFailure
            .recorder[IO](Some(failure => observed.update(_ :+ failure)))
            .use { failures =>
              failures
                .source("source")(body)
                .evalMap(_ => IO.raiseError[Unit](native))
                .compile
                .drain
            }
            .attempt
          _ <- exit.get.assertEquals(Resource.ExitCase.Errored(native))
          failures <- observed.get
          _ <- IO {
            assert(result.isLeft)
            if (expected.isEmpty) {
              assert(result.left.exists(_ eq native))
              assertEquals(failures, Vector.empty)
            } else {
              assert(failures.nonEmpty)
              failures.foreach { failure =>
                val retained = leaves(failure.getCause)
                assertEquals(retained.length, expected.length)
                assert(retained.zip(expected).forall { case (actual, wanted) => actual eq wanted })
              }
            }
          }
          rejection <- result.fold(AdapterFailure.raiseIfPresent[IO], _ => IO.unit).attempt
          _ <- IO(assertEquals(rejection.isRight, expected.isEmpty))
        } yield ()
      }
    }
  }

  test("source ownership preserves recovered errors and successful finalizer exit cases") {
    for {
      exit <- Deferred[IO, Resource.ExitCase]
      body = Stream
        .raiseError[IO](new java.io.IOException("recovered"))
        .handleErrorWith(_ => Stream.emit(42.toByte))
        .onFinalizeCase(value => exit.complete(value).void)
      _ <- AdapterFailure.recorder[IO]().use { failures =>
        val source = failures.source("source")(body)
        source.compile.toVector.assertEquals(Vector(42.toByte))
      }
      _ <- exit.get.assertEquals(Resource.ExitCase.Succeeded)
    } yield ()
  }

  test("source ownership closes scopes with the original unrecovered source error") {
    val original = new java.io.IOException("source failure")
    for {
      exit <- Deferred[IO, Resource.ExitCase]
      error <- AdapterFailure
        .recorder[IO]()
        .use { failures =>
          val body = Stream
            .raiseError[IO](original)
            .onFinalizeCase(value => exit.complete(value).void)
          failures.source("source")(body).compile.drain
        }
        .intercept[AdapterFailure]
      _ <- IO(assertEquals(error.getCause, original))
      _ <- exit.get.assertEquals(Resource.ExitCase.Errored(original))
    } yield ()
  }

  test("source ownership preserves recovery after internal interruption") {
    for {
      started <- Deferred[IO, Unit]
      observed <- Ref.of[IO, Vector[AdapterFailure]](Vector.empty)
      recovered = new java.io.IOException("recovered after interruption")
      body = (Stream.exec(started.complete(()).void) ++ Stream.never[IO])
        .interruptWhen(started.get.as(Right(()): Either[Throwable, Unit])) ++
        Stream.eval(IO.raiseError[Int](recovered)).handleErrorWith(_ => Stream.emit(42))
      _ <- AdapterFailure.recorder[IO](Some(error => observed.update(_ :+ error))).use { failures =>
        failures.source("source")(body).compile.toVector.assertEquals(Vector(42))
      }
      _ <- observed.get.assertEquals(Vector.empty)
    } yield ()
  }

  test("source ownership permits successful reevaluation after self-cancellation") {
    for {
      runs <- Ref.of[IO, Int](0)
      observed <- Ref.of[IO, Vector[AdapterFailure]](Vector.empty)
      body = Stream.eval(runs.updateAndGet(_ + 1)).flatMap {
        case 1 => Stream.exec(IO.canceled)
        case _ =>
          Stream
            .eval(IO.raiseError[Int](new java.io.IOException("recovered")))
            .handleErrorWith(_ => Stream.emit(42))
      }
      _ <- AdapterFailure.recorder[IO](Some(error => observed.update(_ :+ error))).use { failures =>
        val source = failures.source("source")(body)
        source.compile.drain.start.flatMap(_.join).flatMap(assertCanceled) *>
          source.compile.toVector.assertEquals(Vector(42))
      }
      _ <- observed.get.assertEquals(Vector.empty)
    } yield ()
  }

  test("source ownership finishes weak finalizers before reporting EOF") {
    for {
      finalizing <- Deferred[IO, Unit]
      finish <- Deferred[IO, Unit]
      completed <- Ref.of[IO, Boolean](false)
      exit <- Deferred[IO, Resource.ExitCase]
      body = Stream.emit(42.toByte).covary[IO].onFinalizeCaseWeak { value =>
        exit.complete(value).void *> finalizing.complete(()).void *> finish.get
      }
      _ <- AdapterFailure.recorder[IO]().use { failures =>
        val run = failures.source("source")(body).compile.toVector <* completed.set(true)
        Resource.make(run.start)(_.cancel).use { fiber =>
          (finalizing.get *> completed.get.assertEquals(false) *>
            exit.get.assertEquals(Resource.ExitCase.Succeeded))
            .guarantee(finish.complete(()).void) *>
            fiber.joinWithNever.assertEquals(Vector(42.toByte))
        }
      }
    } yield ()
  }

  test("source ownership permits repeated stream evaluation") {
    for {
      runs <- Ref.of[IO, Int](0)
      finalizers <- Ref.of[IO, Int](0)
      body = Stream
        .eval(runs.updateAndGet(_ + 1))
        .onFinalize(finalizers.update(_ + 1))
      _ <- AdapterFailure.recorder[IO]().use { failures =>
        val source = failures.source("source")(body)
        source.compile.toVector.assertEquals(Vector(1)) *>
          source.compile.toVector.assertEquals(Vector(2))
      }
      _ <- finalizers.get.assertEquals(2)
    } yield ()
  }

  test("fixture cleanup defers and retains synchronous finalizer construction failures") {
    val cleanupError = new java.io.IOException("constructing cleanup")
    var constructed = false
    for {
      observed <- Ref.of[IO, Vector[AdapterFailure]](Vector.empty)
      result <- AdapterFailure
        .recorder[IO](Some(error => observed.update(_ :+ error)))
        .use { failures =>
          val cleanup = failures.fixtureCleanup {
            constructed = true
            throw cleanupError
          }
          IO(assert(!constructed)) *> cleanup
        }
        .attempt
      failures <- observed.get
      _ <- IO {
        assert(constructed)
        assert(result.left.toOption.flatMap(AdapterFailure.find).exists(_.getCause eq cleanupError))
        assert(failures.exists(_.getCause eq cleanupError))
      }
    } yield ()
  }

  test("fixture cleanup retains failure alongside the original resource error and exit") {
    val primary = new java.io.IOException("fixture operation")
    val cleanupError = new java.io.IOException("fixture release")
    for {
      exit <- Deferred[IO, Resource.ExitCase]
      result <- AdapterFailure
        .recorder[IO]()
        .use { failures =>
          Resource
            .makeCase(IO.unit)((_, original) =>
              failures
                .fixtureCleanup(exit.complete(original).void *> IO.raiseError[Unit](cleanupError))
            )
            .use(_ => IO.raiseError[Unit](primary))
        }
        .attempt
      _ <- exit.get.assertEquals(Resource.ExitCase.Errored(primary))
      _ <- IO {
        assertEquals(result, Left(primary))
        assert(AdapterFailure.find(primary).exists(_.getCause eq cleanupError))
      }
    } yield ()
  }

  private def assertCanceled(outcome: Outcome[IO, Throwable, Unit]): IO[Unit] =
    outcome match {
      case Outcome.Canceled() => IO.unit
      case Outcome.Errored(error) => IO.raiseError(error)
      case Outcome.Succeeded(result) =>
        result *> IO(fail("operation completed before cancellation"))
    }

}
