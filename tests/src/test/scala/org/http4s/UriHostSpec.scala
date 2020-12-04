/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.kernel.laws.discipline._
import org.http4s.laws.discipline.ArbitraryInstances.http4sTestingCogenForUriHost

final class UriHostSpec extends Http4sSpec {

  "Uri.Host instances" should {
    "be lawful" in {
      checkAll("Order[Uri.Host]", OrderTests[Uri.Host].order)
      checkAll("Hash[Uri.Host]", HashTests[Uri.Host].hash)
    }
  }
}
