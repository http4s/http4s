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

import cats.kernel.laws.discipline.HashTests
import cats.kernel.laws.discipline.OrderTests
import com.comcast.ip4s._
import org.http4s.Uri.Ipv6Address
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.laws.discipline.arbitrary._
import org.http4s.util.Renderer.renderString
import org.scalacheck.Prop._

class Ipv6AddressSuite extends Http4sSuite {
  checkAll("Order[Ipv6Address]", OrderTests[Ipv6Address].order)
  checkAll("Hash[Ipv6Address]", HashTests[Ipv6Address].hash)
  checkAll("HttpCodec[Ipv6Address]", HttpCodecTests[Ipv6Address].httpCodec)

  test("render consistently with RFC5952 should 4.1: handle leading zeroes in a 16-bit field") {
    assertEquals(renderString(Ipv6Address(ipv6"2001:0db8::0001")), "2001:db8::1")
  }
  test("render consistently with RFC5952 should 4.2.1: shorten as much as possible") {
    assertEquals(renderString(Ipv6Address(ipv6"2001:db8:0:0:0:0:2:1")), "2001:db8::2:1")
  }
  test("render consistently with RFC5952 should 4.2.2: handle one 16-bit 0 field") {
    assertEquals(renderString(Ipv6Address(ipv6"2001:db8:0:1:1:1:1:1")), "2001:db8:0:1:1:1:1:1")
  }
  test("""render consistently with RFC5952 should 4.2.3: chose placement of "::"""") {
    assertEquals(renderString(Ipv6Address(ipv6"2001:0:0:1:0:0:0:1")), "2001:0:0:1::1")
    renderString(Ipv6Address(ipv6"2001:db8:0:0:1:0:0:1")) == "2001:db8::1:0:0:1"
  }
  test("render consistently with RFC5952 should 4.3: lowercase") {
    assertEquals(renderString(Ipv6Address(ipv6"::A:B:C:D:E:F")), "::a:b:c:d:e:f")
  }

  test("fromByteArray should round trip with toByteArray") {
    forAll { (ipv6: Ipv6Address) =>
      Ipv6Address.fromByteArray(ipv6.toByteArray) == Right(ipv6)
    }
  }

  test("compare should be consistent with address") {
    forAll { (xs: List[Ipv6Address]) =>
      xs.sorted.map(_.address) == xs.map(_.address).sorted
    }
  }

  test("compare should be consistent with Ordered") {
    forAll { (a: Ipv6Address, b: Ipv6Address) =>
      math.signum(a.compareTo(b)) == math.signum(a.compare(b))
    }
  }

  test("ipv6 interpolator should be consistent with fromString") {
    assertEquals(Ipv6Address.fromString("::1"), Right(Ipv6Address(ipv6"::1")))
    assertEquals(Ipv6Address.fromString("::"), Right(Ipv6Address(ipv6"::")))
    assertEquals(Ipv6Address.fromString("2001:db8::7"), Right(Ipv6Address(ipv6"2001:db8::7")))
  }

  test("ipv6 interpolator should reject invalid values") {
    assert(compileErrors("""Ipv6Address(ipv6"127.0.0.1")""").nonEmpty)
  }

}
