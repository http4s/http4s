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
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.util.Renderer

class TransferCodingSpec extends Http4sSpec {
  "equals" should {
    "be consistent with equalsIgnoreCase of the codings" in prop {
      (a: TransferCoding, b: TransferCoding) =>
        (a == b) must_== a.coding.equalsIgnoreCase(b.coding)
    }
  }

  "compare" should {
    "be consistent with coding.compareToIgnoreCase" in {
      prop { (a: TransferCoding, b: TransferCoding) =>
        a.coding.compareToIgnoreCase(b.coding) must_== a.compare(b)
      }
    }
  }

  "hashCode" should {
    "be consistent with equality" in
      prop { (a: TransferCoding, b: TransferCoding) =>
        a == b must_== (a.## == b.##)
      }
  }

  "parser" should {
    "parse TransferCoding" in {
      prop { (a: TransferCoding) =>
        TransferCoding.parser.parseAll(a.coding) must beRight(a)
      }
    }
  }

  "render" should {
    "return coding" in prop { (s: TransferCoding) =>
      Renderer.renderString(s) must_== s.coding
    }
  }

  checkAll("Order[TransferCoding]", OrderTests[TransferCoding].order)
  checkAll("HttpCodec[TransferCoding]", HttpCodecTests[TransferCoding].httpCodec)
}
