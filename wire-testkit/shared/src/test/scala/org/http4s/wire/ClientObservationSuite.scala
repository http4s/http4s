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

class ClientObservationSuite extends RuntimeDiagnostics.Suite {
  for (failed <- Vector(false, true))
    test(s"consumption result is observed before release: failed=$failed") {
      val failure = new RuntimeException("body")
      for {
        events <- Ref.of[IO, Vector[String]](Vector.empty)
        resource = Resource.make(events.update(_ :+ "acquire").as(42))(_ =>
          events.update(_ :+ "release")
        )
        _ <- ClientObservation(resource)(value =>
          events.update(_ :+ "consume") *> (if (failed) IO.raiseError[Int](failure)
                                            else IO.pure(value))
        ) { result =>
          events.get.assertEquals(Vector("acquire", "consume")) *>
            IO(assertEquals(result, if (failed) Left(failure) else Right(42))) *>
            events.update(_ :+ "observe")
        }
        _ <- events.get.assertEquals(Vector("acquire", "consume", "observe", "release"))
      } yield ()
    }

  test("acquisition failure reaches classification without invoking consumption") {
    val failure = new RuntimeException("acquire")
    ClientObservation(Resource.eval(IO.raiseError[Int](failure)))(_ =>
      IO.raiseError[Int](new AssertionError("consumed an unacquired value"))
    )(result => IO(assertEquals(result, Left(failure))))
  }

  test("observation failure is propagated once and releases the resource") {
    val failure = new RuntimeException("wire verdict")
    for {
      calls <- Ref.of[IO, Int](0)
      released <- Ref.of[IO, Boolean](false)
      result <- ClientObservation(Resource.make(IO.unit)(_ => released.set(true)))(_ => IO.unit)(
        _ => calls.update(_ + 1) *> IO.raiseError[Unit](failure)
      ).attempt
      _ <- IO(assertEquals(result, Left(failure)))
      _ <- calls.get.assertEquals(1)
      _ <- released.get.assertEquals(true)
    } yield ()
  }

  test("release failure is propagated without reclassifying it as target rejection") {
    val failure = new RuntimeException("release")
    for {
      calls <- Ref.of[IO, Int](0)
      result <- ClientObservation(Resource.make(IO.unit)(_ => IO.raiseError[Unit](failure)))(_ =>
        IO.unit
      )(_ => calls.update(_ + 1)).attempt
      _ <- IO(assertEquals(result, Left(failure)))
      _ <- calls.get.assertEquals(1)
    } yield ()
  }

  for (stage <- Vector("consume", "observe"))
    test(s"cancellation during $stage releases once and is never classified as rejection") {
      for {
        entered <- Deferred[IO, Unit]
        releases <- Ref.of[IO, Int](0)
        observations <- Ref.of[IO, Int](0)
        run = ClientObservation(Resource.make(IO.unit)(_ => releases.update(_ + 1)))(_ =>
          if (stage == "consume") entered.complete(()).void *> IO.never[Unit] else IO.unit
        )(_ => observations.update(_ + 1) *> entered.complete(()).void *> IO.never[Unit])
        _ <- Resource.make(run.start)(_.cancel).use { fiber =>
          entered.get *> releases.get.assertEquals(0) *> fiber.cancel *>
            fiber.join.flatMap {
              case Outcome.Canceled() => IO.unit
              case outcome => IO(fail(s"expected cancellation, obtained $outcome"))
            }
        }
        _ <- releases.get.assertEquals(1)
        _ <- observations.get.assertEquals(if (stage == "consume") 0 else 1)
      } yield ()
    }

  test("a wire witness supplied only by release cannot finish observation") {
    for {
      entered <- Deferred[IO, Unit]
      released <- Deferred[IO, Unit]
      run = ClientObservation(Resource.make(IO.unit)(_ => released.complete(()).void))(_ =>
        IO.unit
      )(_ => entered.complete(()).void *> released.get)
      _ <- Resource.make(run.start)(_.cancel).use { fiber =>
        entered.get *> released.tryGet.assertEquals(None) *> fiber.cancel
      }
      _ <- released.tryGet.assertEquals(Some(()))
    } yield ()
  }
}
