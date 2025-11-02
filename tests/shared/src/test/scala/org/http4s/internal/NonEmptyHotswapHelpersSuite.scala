/*
 * Copyright 2025 http4s.org
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

package org.http4s.internal

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.NonEmptyHotswap
import cats.syntax.all._
import org.http4s.Http4sSuite

final class NonEmptyHotswapHelpersSuite extends Http4sSuite {

  test("requireSome returns the contained value when present") {
    assertIO(NonEmptyHotswapHelpers.requireSome[IO, Int](42.some, "missing"), 42)
  }

  test("requireSome raises IllegalStateException when empty") {
    NonEmptyHotswapHelpers.requireSome[IO, Int](none[Int], "missing").attempt.flatMap {
      case Left(e: IllegalStateException) => IO(assertEquals(e.getMessage, "missing"))
      case Left(other) => IO(fail(s"Unexpected exception: $other"))
      case Right(value) => IO(fail(s"Expected failure but succeeded with $value"))
    }
  }

  test("requireCurrent returns the current value when present") {
    NonEmptyHotswap.empty[IO, Int].use { hs: NonEmptyHotswap[IO, Option[Int]] =>
      for {
        _ <- hs.swap(Resource.pure(42.some: Option[Int]))
        value <- NonEmptyHotswapHelpers.requireCurrent(hs, "missing")
        _ <- IO(assertEquals(value, 42))
      } yield ()
    }
  }

  test("requireCurrent raises IllegalStateException when empty") {
    NonEmptyHotswap.empty[IO, Int].use { hs: NonEmptyHotswap[IO, Option[Int]] =>
      for {
        _ <- hs.swap(Resource.pure(none[Int]: Option[Int]))
        outcome <- NonEmptyHotswapHelpers.requireCurrent(hs, "missing").attempt
        _ <- outcome match {
          case Left(e: IllegalStateException) => IO(assertEquals(e.getMessage, "missing"))
          case Left(other) => IO(fail(s"Unexpected exception: $other"))
          case Right(value) => IO(fail(s"Expected failure but succeeded with $value"))
        }
      } yield ()
    }
  }
}
