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

import cats.data.NonEmptyList
import cats.kernel.laws.discipline.OrderTests
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.laws.discipline.arbitrary._
import org.http4s.util.Renderer
import org.scalacheck.Prop

class TransferCodingSpec extends Http4sSuite {

  test("equals should be consistent with equalsIgnoreCase of the codings") {
    Prop.forAll { (a: TransferCoding, b: TransferCoding) =>
      (a == b) == a.coding.equalsIgnoreCase(b.coding)
    }
  }

  test("compare should be consistent with coding.compareToIgnoreCase") {
    Prop.forAll { (a: TransferCoding, b: TransferCoding) =>
      a.coding.compareToIgnoreCase(b.coding) == a.compare(b)
    }
  }

  test("hashCode should be consistent with equality") {
    Prop.forAll { (a: TransferCoding, b: TransferCoding) =>
      a == b == (a.## == b.##)
    }
  }

  test("parse should parse single items") {
    Prop.forAll { (a: TransferCoding) =>
      TransferCoding.parseList(a.coding) == ParseResult.success(NonEmptyList.one(a))
    }
  }
  test("parse should parse multiple items") {
    assertEquals(
      TransferCoding.parseList("gzip, chunked"),
      ParseResult.success(NonEmptyList.of(TransferCoding.gzip, TransferCoding.chunked)),
    )
  }

  test("render should return coding") {
    Prop.forAll { (s: TransferCoding) =>
      Renderer.renderString(s) == s.coding
    }
  }

  checkAll("Order[TransferCoding]", OrderTests[TransferCoding].order)
  checkAll("HttpCodec[TransferCoding]", HttpCodecTests[TransferCoding].httpCodec)
}
