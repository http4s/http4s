/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.parser

import org.http4s.Http4sSpec
import org.http4s.Uri.{Ipv4Address, Ipv6Address}
import org.scalacheck.Prop

class Rfc3986ParserSpec extends Http4sSpec {

  "Rfc3986 ipv4 parser" should {
    "parse generated ipv4 addresses" in {
      Prop.forAll(http4sTestingArbitraryForIpv4Address.arbitrary) { (ipv4: Ipv4Address) =>
        Ipv4Address.fromString(ipv4.value) must beRight(ipv4)
      }
    }
  }

  "Rfc3986 ipv6 parser" should {

    "parse ipv6 address with all sections filled" in {
      val ipv6 =
        Ipv6Address(1, 2173, 21494, -32768, 0, 1, 21787, 31704) // 1:87d:53f6:8000:0:1:551b:7bd8
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the first and second spots" in {
      val ipv6 = Ipv6Address(0, 0, 21494, -32768, 0, 1, 21787, 31704) // ::53f6:8000:0:1:551b:7bd8
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the first three spots" in {
      val ipv6 = Ipv6Address(0, 0, 0, -32768, 0, 1, 21787, 31704) // ::8000:0:1:551b:7bd8
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the second and third spots" in {
      val ipv6 = Ipv6Address(21494, 0, 0, -32768, 0, 1, 21787, 31704) // 53f6::8000:0:1:551b:7bd8
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the first four spots" in {
      val ipv6 = Ipv6Address(0, 0, 0, 0, -32768, 1, 21787, 31704) // ::8000:1:551b:7bd8
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the third and fourth spots" in {
      val ipv6 = Ipv6Address(21494, -32768, 0, 0, 1, 1, 21787, 31704) // 53f6:8000::1:1:551b:7bd8
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the first five spots" in {
      val ipv6 = Ipv6Address(0, 0, 0, 0, 0, 1, 21787, 31704) // ::1:551b:7bd8
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the fourth and fifth spots" in {
      val ipv6 = Ipv6Address(21494, -32768, 1, 0, 0, 1, 21787, 31704) // 53f6:8000:1::1:551b:7bd8
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the first six spots" in {
      val ipv6 = Ipv6Address(0, 0, 0, 0, 0, 0, 21787, 31704) // ::551b:7bd8
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the fifth and sixth spots" in {
      val ipv6 = Ipv6Address(21494, -32768, 1, 1, 0, 0, 21787, 31704) // 53f6:8000:1:1::551b:7bd8
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the first seven spots" in {
      val ipv6 = Ipv6Address(0, 0, 0, 0, 0, 0, 0, 31704) // ::7bd8
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the sixth and seventh spots" in {
      val ipv6 = Ipv6Address(21494, -32768, 1, 1, 21787, 0, 0, 31704) // 53f6:8000:1:1:551b::7bd8
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the all eight spots" in {
      val ipv6 = Ipv6Address(0, 0, 0, 0, 0, 0, 0, 0) // ::
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with zeros in the seventh and eighth spots" in {
      val ipv6 = Ipv6Address(21494, -32768, 1, 1, 21787, 31704, 0, 0) // 53f6:8000:1:1:551b:7bd8::
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "parse ipv6 address with three zeros in a row" in {
      val ipv6 = Ipv6Address(22322, -32768, 24194, 32767, 0, 0, 0, 1) // 5732:8000:5e82:7fff::1
      Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
    }

    "not parse an invalid ipv6 address containing an invalid letter such as 'O'" in {
      val invalidIp = "1200:0000:AB00:1234:O000:2552:7777:1313"
      Ipv6Address.fromString(invalidIp) must beLeft
    }

    "only parse part of an invalid ipv6 address where a single section is shortened (must be 2 or more)" in {
      val invalidIp = "2001:db8::1:1:1:1:1"
      Ipv6Address.fromString(invalidIp).map(_.value) must beRight("2001:db8::1:1:1:1")
    }

    "only parse part of an ipv6 address where multiple sections are shortened (only 1 allowed)" in {
      val invalidIp = "56FE::2159:5BBC::6594"
      Ipv6Address.fromString(invalidIp).map(_.value) must beRight("56fe::2159:5bbc")
    }

    "parse an ipv6 address which is too long (only parse what can be used)" in {
      val ipv6 = "56FE:2159:5BBC:6594:1234:5678:9018:0123:2020"
      Ipv6Address.fromString(ipv6).map(_.value) must beRight(
        "56fe:2159:5bbc:6594:1234:5678:9018:123")
    }

    "not parse an ipv6 address which is too short" in {
      val invalidIp = "56FE:2159:5BBC:6594:1234:5678:9018"
      Ipv6Address.fromString(invalidIp) must beLeft
    }

    "parse ipv6 address with 6 sections and then ipv4 address for the last 32 bits" in {
      val ipv6 = "2001:db8:0:0:0:0:127.0.0.1"
      Ipv6Address.fromString(ipv6).map(_.value) must beRight("2001:db8::7f00:1")
    }

    "parse ipv6 address with 4 sections + a shortened section + ipv4 address for the last 32 bits" in {
      val ipv6 = "2001:db8:0:0::127.0.0.1"
      Ipv6Address.fromString(ipv6).map(_.value) must beRight("2001:db8::7f00:1")
    }

    "parse ipv6 address with 3 sections + a shortened section + ipv4 address for the last 32 bits" in {
      val ipv6 = "2001:db8:0::0:127.0.0.1"
      Ipv6Address.fromString(ipv6).map(_.value) must beRight("2001:db8::7f00:1")
    }

    "parse ipv6 address with 2 sections + a shortened section + ipv4 address for the last 32 bits" in {
      val ipv6 = "2001:db8::0:0:127.0.0.1"
      Ipv6Address.fromString(ipv6).map(_.value) must beRight("2001:db8::7f00:1")
    }

    "parse ipv6 address with 1 section + a shortened section + ipv4 address for the last 32 bits" in {
      val ipv6 = "2001::db8:0:0:127.0.0.1"
      Ipv6Address.fromString(ipv6).map(_.value) must beRight("2001::db8:0:0:7f00:1")
    }

    "parse ipv6 address with 0 sections + a shortened section + ipv4 address for the last 32 bits" in {
      val ipv6 = "::2001:db8:0:0:127.0.0.1"
      Ipv6Address.fromString(ipv6).map(_.value) must beRight("::2001:db8:0:0:7f00:1")
    }

    "parse ipv6 address with 0 sections + a single shortened section + ipv4 address for the last 32 bits" in {
      val ipv6 = "::ffff:127.0.0.1"
      Ipv6Address.fromString(ipv6).map(_.value) must beRight("::ffff:7f00:1")
    }

    "parse random ipv6 addresses" in {
      Prop.forAll(http4sTestingArbitraryForIpv6Address.arbitrary) { (ipv6: Ipv6Address) =>
        Ipv6Address.fromString(ipv6.value) must beRight(ipv6)
      }
    }
  }
}
