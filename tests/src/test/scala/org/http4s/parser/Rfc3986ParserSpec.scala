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

    "parse ipv6 address with all sections filled" in {
      val ipv6 = Ipv6Address(1,2173,21494,-32768,0,1,21787,31704) // 1:87d:53f6:8000:0:1:551b:7bd8
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the first and second spots" in {
      val ipv6 = Ipv6Address(0,0,21494,-32768,0,1,21787,31704) // ::53f6:8000:0:1:551b:7bd8
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the first three spots" in {
      val ipv6 = Ipv6Address(0,0,0,-32768,0,1,21787,31704) // ::8000:0:1:551b:7bd8
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the second and third spots" in {
      val ipv6 = Ipv6Address(21494,0,0,-32768,0,1,21787,31704) // 53f6::8000:0:1:551b:7bd8
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the first four spots" in {
      val ipv6 = Ipv6Address(0,0,0,0,-32768,1,21787,31704) // ::8000:1:551b:7bd8
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the third and fourth spots" in {
      val ipv6 = Ipv6Address(21494,-32768,0,0,1,1,21787,31704) // 53f6:8000::1:1:551b:7bd8
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the first five spots" in {
      val ipv6 = Ipv6Address(0,0,0,0,0,1,21787,31704) // ::1:551b:7bd8
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the fourth and fifth spots" in {
      val ipv6 = Ipv6Address(21494,-32768,1,0,0,1,21787,31704) // 53f6:8000:1::1:551b:7bd8
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the first six spots" in {
      val ipv6 = Ipv6Address(0,0,0,0,0,0,21787,31704) // ::551b:7bd8
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the fifth and sixth spots" in {
      val ipv6 = Ipv6Address(21494,-32768,1,1,0,0,21787,31704) // 53f6:8000:1:1::551b:7bd8
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the first seven spots" in {
      val ipv6 = Ipv6Address(0,0,0,0,0,0,0,31704) // ::7bd8
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the sixth and seventh spots" in {
      val ipv6 = Ipv6Address(21494,-32768,1,1,21787,0,0,31704) // 53f6:8000:1:1:551b::7bd8
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the all eight spots" in {
      val ipv6 = Ipv6Address(0,0,0,0,0,0,0,0) // ::
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the seventh and eighth spots" in {
      val ipv6 = Ipv6Address(21494,-32768,1,1,21787,31704,0,0) // 53f6:8000:1:1:551b:7bd8::
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse ipv6 address with three zeros in a row" in {
      val ipv6 = Ipv6Address(22322,-32768,24194,32767,0,0,0,1) // 5732:8000:5e82:7fff::1
      Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
    }

    "parse random ipv6 addresses" in {
      Prop.forAll(http4sTestingArbitraryForIpv6Address.arbitrary) { (ipv6: Ipv6Address) =>
        Rfc3986.ipv6.parse(ipv6.value).map(_._2) must beRight(ipv6)
      }
    }
  }
}
