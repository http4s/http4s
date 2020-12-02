/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.effect.Blocker
import org.http4s.internal.threads.newBlockingPool
import munit._

/** Common stack for http4s' munit based tests
  */
trait Http4sSuite extends CatsEffectSuite with DisciplineSuite with munit.ScalaCheckEffectSuite {

  val testBlocker: Blocker = Http4sSpec.TestBlocker
}

object Http4sSuite {
  val TestBlocker: Blocker =
    Blocker.liftExecutorService(newBlockingPool("http4s-spec-blocking"))
}
