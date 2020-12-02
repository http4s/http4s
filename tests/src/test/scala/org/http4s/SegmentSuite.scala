/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.kernel.laws.discipline.OrderTests
import org.http4s.Uri.Path.Segment
import org.http4s.laws.discipline.arbitrary._

class SegmentSuite extends Http4sSuite {
  checkAll("Order[Segment]", OrderTests[Segment].order)
}
