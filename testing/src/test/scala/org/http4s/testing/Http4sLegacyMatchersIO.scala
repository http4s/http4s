/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package testing

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import scala.concurrent.duration.FiniteDuration

trait Http4sLegacyMatchersIO extends Http4sLegacyMatchers[IO] {

  protected def runWithTimeout[A](fa: IO[A], d: FiniteDuration): A = fa.timeout(d).unsafeRunSync()
  protected def runAwait[A](fa: IO[A]): A = fa.unsafeRunSync()

}
