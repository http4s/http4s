/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://raw.githubusercontent.com/etorreborre/specs2/c0cbfc71390b644db1a5deeedc099f74a237ebde/matcher-extra/src/main/scala-scalaz-7.0.x/org/specs2/matcher/TaskMatchers.scala
 * Copyright (c) 2007-2012 Eric Torreborre <etorreborre@yahoo.com>
 */

package org.http4s.testing

import cats.effect.{IO, Sync}
import scala.concurrent.duration.FiniteDuration

/**
  * Matchers for cats.effect.IO
  */
@deprecated("Provided by specs2-cats in org.specs2.matcher.IOMatchers", "0.21.0-RC2")
trait IOMatchers extends RunTimedMatchers[IO] {
  protected implicit def F: Sync[IO] = IO.ioEffect
  protected def runWithTimeout[A](fa: IO[A], timeout: FiniteDuration): Option[A] =
    fa.unsafeRunTimed(timeout)
  protected def runAwait[A](fa: IO[A]): A = fa.unsafeRunSync
}

@deprecated("Provided by specs2-cats in org.specs2.matcher.IOMatchers", "0.21.0-RC2")
object IOMatchers extends IOMatchers
