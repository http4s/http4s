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
import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.all._
import org.http4s.util.Renderer
import org.scalacheck.Prop._
import org.scalacheck.Test
import org.scalacheck.rng.Seed

class ContentCodingSuite extends Http4sSuite {

  test("equals should be consistent with equalsIgnoreCase of the codings and quality") {
    forAll { (a: ContentCoding, b: ContentCoding) =>
      (a == b) == (a.coding.equalsIgnoreCase(b.coding) && a.qValue == b.qValue)
    }
  }

  test("compare should be consistent with coding.compareToIgnoreCase for same qValue") {
    forAll { (a: ContentCoding, b: ContentCoding, q: QValue) =>
      val aq = a.withQValue(q)
      val bq = b.withQValue(q)
      (aq.compare(bq)) == (aq.coding.compareToIgnoreCase(bq.coding))
    }
  }

  test("hashCode should be consistent with equality") {
    forAll { (a: ContentCoding, b: ContentCoding) =>
      (a == b) == (a.## == b.##)
    }
  }

  test("ContentCoding.* should always matches") {
    forAll { (a: ContentCoding) =>
      ContentCoding.`*`.matches(a)
    }
  }
  test("ContentCoding should always matches itself") {
    forAll { (a: ContentCoding) =>
      a.matches(a)
    }
  }

  test("parses should parse plain coding") {
    assertEquals(ContentCoding.parse("gzip"), ParseResult.success(ContentCoding.gzip))
  }
  test("parses should parse custom codings") {
    assertEquals(ContentCoding.parse("mycoding"), ContentCoding.fromString("mycoding"))
  }
  test("parses should parse with quality") {
    assertEquals(
      ContentCoding.parse("gzip;q=0.8"),
      Right(ContentCoding.gzip.withQValue(qValue"0.8")),
    )
  }
  test("parses should fail on empty") {
    assert(ContentCoding.parse("").isLeft)
    assert(ContentCoding.parse(";q=0.8").isLeft)
  }
  test("parses should fail on non token") {
    assert(ContentCoding.parse("\\\\").isLeft)
  }
  test("parses should parse *") {
    assertEquals(ContentCoding.parse("*"), ParseResult.success(ContentCoding.`*`))
  }
  test("parses should parse tokens starting with *") {
    // Strange content coding but valid
    assertEquals(
      ContentCoding.parse("*fahon"),
      ParseResult.success(ContentCoding.unsafeFromString("*fahon")),
    )
    assertEquals(
      ContentCoding.parse("*fahon;q=0.1"),
      ParseResult.success(ContentCoding.unsafeFromString("*fahon").withQValue(qValue"0.1")),
    )
  }

  test("render sould return coding and quality") {
    forAll { (s: ContentCoding) =>
      Renderer
        .renderString(s) == s"${s.coding.toLowerCase}${Renderer.renderString(s.qValue)}"
    }
  }

  checkAll("Order[ContentCoding]", OrderTests[ContentCoding].order)
  checkAll("HttpCodec[ContentCoding]", HttpCodecTests[ContentCoding].httpCodec)
}

class ContentCodingSuiteFixedSeed extends Http4sSuite {
  override protected def scalaCheckTestParameters: Test.Parameters = Test.Parameters.default
    .withMinSuccessfulTests(1)
    .withInitialSeed(Seed.fromBase64("2kpw5tJ8tADijqPv2GG3pUWPhjzpJUnaypQufFSWHBB=").toOption)

  checkAll("Order[ContentCoding] for #3328", OrderTests[ContentCoding].order)
}
