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

package org.http4s
package parser

import org.http4s.Uri.Ipv4Address
import org.http4s.Uri.Ipv6Address
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Prop

class Rfc3986ParserSpec extends Http4sSuite {

  test("Rfc3986 ipv4 parser should parse generated ipv4 addresses") {
    Prop.forAll(http4sTestingArbitraryForIpv4Address.arbitrary) { (ipv4: Ipv4Address) =>
      Ipv4Address.fromString(ipv4.value) == Right(ipv4)
    }
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with all sections filled") {
    val ipv6 =
      Ipv6Address.fromShorts(1, 2173, 21494, -32768, 0, 1, 21787,
        31704) // 1:87d:53f6:8000:0:1:551b:7bd8
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with zeros in the first and second spots") {
    val ipv6 =
      Ipv6Address.fromShorts(0, 0, 21494, -32768, 0, 1, 21787, 31704) // ::53f6:8000:0:1:551b:7bd8
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with zeros in the first three spots") {
    val ipv6 = Ipv6Address.fromShorts(0, 0, 0, -32768, 0, 1, 21787, 31704) // ::8000:0:1:551b:7bd8
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with zeros in the second and third spots") {
    val ipv6 =
      Ipv6Address.fromShorts(21494, 0, 0, -32768, 0, 1, 21787, 31704) // 53f6::8000:0:1:551b:7bd8
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with zeros in the first four spots") {
    val ipv6 = Ipv6Address.fromShorts(0, 0, 0, 0, -32768, 1, 21787, 31704) // ::8000:1:551b:7bd8
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with zeros in the third and fourth spots") {
    val ipv6 =
      Ipv6Address.fromShorts(21494, -32768, 0, 0, 1, 1, 21787, 31704) // 53f6:8000::1:1:551b:7bd8
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with zeros in the first five spots") {
    val ipv6 = Ipv6Address.fromShorts(0, 0, 0, 0, 0, 1, 21787, 31704) // ::1:551b:7bd8
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with zeros in the fourth and fifth spots") {
    val ipv6 =
      Ipv6Address.fromShorts(21494, -32768, 1, 0, 0, 1, 21787, 31704) // 53f6:8000:1::1:551b:7bd8
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with zeros in the first six spots") {
    val ipv6 = Ipv6Address.fromShorts(0, 0, 0, 0, 0, 0, 21787, 31704) // ::551b:7bd8
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with zeros in the fifth and sixth spots") {
    val ipv6 =
      Ipv6Address.fromShorts(21494, -32768, 1, 1, 0, 0, 21787, 31704) // 53f6:8000:1:1::551b:7bd8
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with zeros in the first seven spots") {
    val ipv6 = Ipv6Address.fromShorts(0, 0, 0, 0, 0, 0, 0, 31704) // ::7bd8
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with zeros in the sixth and seventh spots") {
    val ipv6 =
      Ipv6Address.fromShorts(21494, -32768, 1, 1, 21787, 0, 0, 31704) // 53f6:8000:1:1:551b::7bd8
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with zeros in the all eight spots") {
    val ipv6 = Ipv6Address.fromShorts(0, 0, 0, 0, 0, 0, 0, 0) // ::
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with zeros in the seventh and eighth spots") {
    val ipv6 =
      Ipv6Address.fromShorts(21494, -32768, 1, 1, 21787, 31704, 0, 0) // 53f6:8000:1:1:551b:7bd8::
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test("Rfc3986 ipv6 parser should parse ipv6 address with three zeros in a row") {
    val ipv6 =
      Ipv6Address.fromShorts(22322, -32768, 24194, 32767, 0, 0, 0, 1) // 5732:8000:5e82:7fff::1
    assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
  }

  test(
    "Rfc3986 ipv6 parser should not parse an invalid ipv6 address containing an invalid letter such as 'O'"
  ) {
    val invalidIp = "1200:0000:AB00:1234:O000:2552:7777:1313"
    assert(Ipv6Address.fromString(invalidIp).isLeft)
  }

  test(
    "Rfc3986 ipv6 parser should only parse part of an invalid ipv6 address where a single section is shortened (must be 2 or more)"
  ) {
    val invalidIp = "2001:db8::1:1:1:1:1"
    assert(Ipv6Address.fromString(invalidIp).map(_.value).isLeft)
  }

  test(
    "Rfc3986 ipv6 parser should only parse part of an ipv6 address where multiple sections are shortened (only 1 allowed)"
  ) {
    val invalidIp = "56FE::2159:5BBC::6594"
    assert(Ipv6Address.fromString(invalidIp).map(_.value).isLeft)
  }

  test(
    "Rfc3986 ipv6 parser should parse an ipv6 address which is too long (only parse what can be used)"
  ) {
    val ipv6 = "56FE:2159:5BBC:6594:1234:5678:9018:0123:2020"
    assert(Ipv6Address.fromString(ipv6).map(_.value).isLeft)
  }

  test("Rfc3986 ipv6 parser should not parse an ipv6 address which is too short") {
    val invalidIp = "56FE:2159:5BBC:6594:1234:5678:9018"
    assert(Ipv6Address.fromString(invalidIp).isLeft)
  }

  test(
    "Rfc3986 ipv6 parser should parse ipv6 address with 6 sections and then ipv4 address for the last 32 bits"
  ) {
    val ipv6 = "2001:db8:0:0:0:0:127.0.0.1"
    assertEquals(Ipv6Address.fromString(ipv6).map(_.value), Right("2001:db8::7f00:1"))
  }

  test(
    "Rfc3986 ipv6 parser should parse ipv6 address with 4 sections + a shortened section + ipv4 address for the last 32 bits"
  ) {
    val ipv6 = "2001:db8:0:0::127.0.0.1"
    assertEquals(Ipv6Address.fromString(ipv6).map(_.value), Right("2001:db8::7f00:1"))
  }

  test(
    "Rfc3986 ipv6 parser should parse ipv6 address with 3 sections + a shortened section + ipv4 address for the last 32 bits"
  ) {
    val ipv6 = "2001:db8:0::0:127.0.0.1"
    assertEquals(Ipv6Address.fromString(ipv6).map(_.value), Right("2001:db8::7f00:1"))
  }

  test(
    "Rfc3986 ipv6 parser should parse ipv6 address with 2 sections + a shortened section + ipv4 address for the last 32 bits"
  ) {
    val ipv6 = "2001:db8::0:0:127.0.0.1"
    assertEquals(Ipv6Address.fromString(ipv6).map(_.value), Right("2001:db8::7f00:1"))
  }

  test(
    "Rfc3986 ipv6 parser should parse ipv6 address with 1 section + a shortened section + ipv4 address for the last 32 bits"
  ) {
    val ipv6 = "2001::db8:0:0:127.0.0.1"
    assertEquals(Ipv6Address.fromString(ipv6).map(_.value), Right("2001::db8:0:0:7f00:1"))
  }

  test(
    "Rfc3986 ipv6 parser should parse ipv6 address with 0 sections + a shortened section + ipv4 address for the last 32 bits"
  ) {
    val ipv6 = "::2001:db8:0:0:127.0.0.1"
    assertEquals(Ipv6Address.fromString(ipv6).map(_.value), Right("::2001:db8:0:0:7f00:1"))
  }

  test(
    "Rfc3986 ipv6 parser should parse ipv6 address with 0 sections + a single shortened section + ipv4 address for the last 32 bits"
  ) {
    val ipv6 = "::ffff:127.0.0.1"
    assertEquals(Ipv6Address.fromString(ipv6).map(_.value), Right("::ffff:7f00:1"))
  }

  test("Rfc3986 ipv6 parser should parse random ipv6 addresses") {
    Prop.forAll(http4sTestingArbitraryForIpv6Address.arbitrary) { (ipv6: Ipv6Address) =>
      assertEquals(Ipv6Address.fromString(ipv6.value), Right(ipv6))
    }
  }

}
