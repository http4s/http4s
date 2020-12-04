/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.kernel.laws.discipline._
import org.http4s.Uri.Path
import org.http4s.laws.discipline.arbitrary._

class PathSuite extends Http4sSuite {
  checkAll("Order[Path]", OrderTests[Path].order)
  checkAll("Semigroup[Path]", SemigroupTests[Path].semigroup)
}
