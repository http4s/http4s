/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.parser

import org.http4s.Http4sSpec
import org.http4s.Uri.Ipv6Address
import org.http4s.internal.parsing.Rfc3986
import org.scalacheck.Prop

class Rfc3986ParserSpec extends Http4sSpec {
  "Rfc3986 parser" should {
    "parse ipv6" in {
      Prop.forAll(http4sTestingArbitraryForIpv6Address.arbitrary) { (ipv6: Ipv6Address) =>
        Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
      }
    }
  }
}
