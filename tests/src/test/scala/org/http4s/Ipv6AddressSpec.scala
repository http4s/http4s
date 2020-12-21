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

import cats.kernel.laws.discipline.{HashTests, OrderTests}
import org.http4s.Uri.Ipv6Address
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.util.Renderer.renderString
import org.specs2.execute._, Typecheck._
import org.specs2.matcher.TypecheckMatchers._

class Ipv6AddressSpec extends Http4sSpec {
  checkAll("Order[Ipv6Address]", OrderTests[Ipv6Address].order)
  checkAll("Hash[Ipv6Address]", HashTests[Ipv6Address].hash)
  checkAll("HttpCodec[Ipv6Address]", HttpCodecTests[Ipv6Address].httpCodec)

  "render consistently with RFC5952" should {
    "4.1: handle leading zeroes in a 16-bit field" in {
      renderString(ipv6"2001:0db8::0001") must_== "2001:db8::1"
    }
    "4.2.1: shorten as much as possible" in {
      renderString(ipv6"2001:db8:0:0:0:0:2:1") must_== "2001:db8::2:1"
    }
    "4.2.2: handle one 16-bit 0 field" in {
      renderString(ipv6"2001:db8:0:1:1:1:1:1") must_== "2001:db8:0:1:1:1:1:1"
    }
    """4.2.3: chose placement of "::"""" in {
      renderString(ipv6"2001:0:0:1:0:0:0:1") must_== "2001:0:0:1::1"
      renderString(ipv6"2001:db8:0:0:1:0:0:1") must_== "2001:db8::1:0:0:1"
    }
    "4.3: lowercase" in {
      renderString(ipv6"::A:B:C:D:E:F") must_== "::a:b:c:d:e:f"
    }
  }

  "fromInet6Address" should {
    "round trip with toInet6Address" in prop { (ipv6: Ipv6Address) =>
      Ipv6Address.fromInet6Address(ipv6.toInet6Address) must_== ipv6
    }
  }

  "fromByteArray" should {
    "round trip with toByteArray" in prop { (ipv6: Ipv6Address) =>
      Ipv6Address.fromByteArray(ipv6.toByteArray) must_== Right(ipv6)
    }
  }

  "compare" should {
    "be consistent with unsigned int" in prop { (xs: List[Ipv6Address]) =>
      def tupled(a: Ipv6Address) = (a.a, a.b, a.c, a.d)
      xs.sorted.map(tupled) must_== xs.map(tupled).sorted
    }

    "be consistent with Ordered" in prop { (a: Ipv6Address, b: Ipv6Address) =>
      math.signum(a.compareTo(b)) must_== math.signum(a.compare(b))
    }
  }

  "ipv6 interpolator" should {
    "be consistent with fromString" in {
      Right(ipv6"::1") must_== Ipv6Address.fromString("::1")
      Right(ipv6"::") must_== Ipv6Address.fromString("::")
      Right(ipv6"2001:db8::7") must_== Ipv6Address.fromString("2001:db8::7")
    }

    "reject invalid values" in {
      typecheck("""ipv6"127.0.0.1"""") must not succeed
    }
  }
}
