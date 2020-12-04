/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import org.http4s.laws.discipline.ArbitraryInstances.http4sTestingCogenForAuthority
import cats.kernel.laws.discipline._

final class AuthoritySpec extends Http4sSpec {

  "Uri.Authority instances" should {
    "be lawful" in {
      checkAll("Order[Uri.Authority]", OrderTests[Uri.Authority].order)
      checkAll("Hash[Uri.Authority]", HashTests[Uri.Authority].hash)
    }
  }
}
