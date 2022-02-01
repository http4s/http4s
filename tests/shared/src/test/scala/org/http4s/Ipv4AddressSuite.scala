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
import org.http4s.Uri.Ipv4Address
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.laws.discipline.arbitrary._
import org.http4s.util.Renderer.renderString
import org.scalacheck.Prop._

class Ipv4AddressSuite extends Http4sSuite {
  checkAll("Order[Ipv4Address]", OrderTests[Ipv4Address].order)
  checkAll("Hash[Ipv4Address]", HashTests[Ipv4Address].hash)
  checkAll("HttpCodec[Ipv4Address]", HttpCodecTests[Ipv4Address].httpCodec)

  test("render should render all 4 octets") {
    assertEquals(renderString(Ipv4Address(ipv4"192.168.0.1")), "192.168.0.1")
  }

  test("fromByteArray should round trip with toByteArray") {
    forAll { (ipv4: Ipv4Address) =>
      assertEquals(Ipv4Address.fromByteArray(ipv4.toByteArray), Right(ipv4))
    }
  }

  test("compare should be consistent with unsigned int") {
    forAll { (xs: List[Ipv4Address]) =>
      assertEquals(xs.sorted.map(_.address), xs.map(_.address).sorted)
    }
  }

  test("compare should be consistent with Ordered") {
    forAll { (a: Ipv4Address, b: Ipv4Address) =>
      assertEquals(math.signum(a.compareTo(b)), math.signum(a.compare(b)))
    }
  }
}
