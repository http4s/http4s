/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import munit._

/** Common stack for http4s' munit based tests
  */
trait Http4sSuite extends CatsEffectSuite with DisciplineSuite with munit.ScalaCheckEffectSuite {}

object Http4sSuite {}
