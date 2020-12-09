/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.kernel.laws.discipline._
import org.http4s.laws.discipline.ArbitraryInstances.http4sTestingArbitraryForRegName
import org.http4s.laws.discipline.ArbitraryInstances.http4sTestingCogenForRegName

final class RegNameSpec extends Http4sSpec {

  "Uri.RegName instances" should {
    "be lawful" in {
      checkAll("Order[Uri.RegName]", OrderTests[Uri.RegName].order)
      checkAll("Hash[Uri.RegName]", HashTests[Uri.RegName].hash)
    }
  }
}
