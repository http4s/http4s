/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.data.NonEmptyList
import cats.implicits._
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

  "parse" should {
    "parse single items" in {
      prop { (a: TransferCoding) =>
        TransferCoding.parseList(a.coding) must_== ParseResult.success(NonEmptyList.one(a))
      }
    }
    "parse multiple items" in {
      TransferCoding.parseList("gzip, chunked") must_== ParseResult.success(
        NonEmptyList.of(TransferCoding.gzip, TransferCoding.chunked))
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
