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

import cats.kernel.laws.discipline.OrderTests
import java.nio.charset.StandardCharsets.ISO_8859_1
import org.http4s.headers._
import org.http4s.util.StringWriter

class HeaderSpec extends Http4sSpec {
  "Headers" should {
    "Equate same headers" in {
      val h1 = `Content-Length`.unsafeFromLong(4)
      val h2 = `Content-Length`.unsafeFromLong(4)

      h1 == h2 should beTrue
      h2 == h1 should beTrue
    }

    "not equal different headers" in {
      val h1 = `Content-Length`.unsafeFromLong(4)
      val h2 = `Content-Length`.unsafeFromLong(5)

      h1 == h2 should beFalse
      h2 == h1 should beFalse
    }

    "equal same raw headers" in {
      val h1 = `Content-Length`.unsafeFromLong(44)
      val h2 = Header("Content-Length", "44")

      h1 == h2 should beTrue
      h2 == h1 should beTrue

      val h3 = Date(HttpDate.Epoch).toRaw.parsed
      val h4 = h3.toRaw

      h3 == h4 should beTrue
      h4 == h3 should beTrue
    }

    "not equal same raw headers" in {
      val h1 = `Content-Length`.unsafeFromLong(4)
      val h2 = Header("Content-Length", "5")

      h1 == h2 should beFalse
      h2 == h1 should beFalse
    }

    "equate raw to same raw headers" in {
      val h1 = Header("Content-Length", "4")
      val h2 = Header("Content-Length", "4")

      h1 == h2 should beTrue
      h2 == h1 should beTrue
    }

    "not equate raw to same raw headers" in {
      val h1 = Header("Content-Length", "4")
      val h2 = Header("Content-Length", "5")

      h1 == h2 should beFalse
      h2 == h1 should beFalse
    }
  }

  "rendered length" should {
    "is rendered length including \\r\\n" in prop { (h: Header) =>
      h.render(new StringWriter << "\r\n")
        .result
        .getBytes(ISO_8859_1)
        .length
        .toLong must_== h.renderedLength
    }
  }

  "Order instance for Header" should {
    "be lawful" in {
      checkAll("Order[Header]", OrderTests[Header].order)
    }
  }
}
