/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.testing

import cats.effect.{Async, Resource, Sync}
import cats.effect.std.Dispatcher
import org.specs2.execute.{AsResult, Result}

import scala.concurrent.duration._

/** copy of [cats.effect.testing.specs2.CatsEffect](https://github.com/djspiewak/cats-effect-testing/blob/series%2F0.x/specs2/src/main/scala/cats/effect/testing/specs2/CatsEffect.scala) adapted to cats-effect 3
  */
trait CatsEffect {
  protected val Timeout: Duration = 10.seconds

  implicit def effectAsResult[F[_]: Async, R](implicit
      R: AsResult[R],
      D: Dispatcher[F]): AsResult[F[R]] = new AsResult[F[R]] {
    def asResult(t: => F[R]): Result =
      R.asResult(D.unsafeRunTimed(t, Timeout))
  }

  implicit def resourceAsResult[F[_]: Async, R](implicit
      R: AsResult[R],
      D: Dispatcher[F]): AsResult[Resource[F, R]] = new AsResult[Resource[F, R]] {
    def asResult(t: => Resource[F, R]): Result = {
      val result = t.use(r => Sync[F].delay(R.asResult(r)))
      D.unsafeRunTimed(result, Timeout)
    }
  }
}
