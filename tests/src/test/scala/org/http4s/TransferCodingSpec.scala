package org.http4s

import cats.data.NonEmptyList
import cats.kernel.laws.OrderLaws
import org.http4s.testing.HttpCodecTests

class TransferCodingSpec extends Http4sSpec {
  "compareTo" should {
    "be consistent with coding.compareToIgnoreCase" in {
      prop { (a: TransferCoding, b: TransferCoding) =>
        a.coding.compareToIgnoreCase(b.coding) must_== a.compareTo(b)
      }
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

  checkAll("order", OrderLaws[TransferCoding].order)
  checkAll("httpCodec", HttpCodecTests[TransferCoding].httpCodec)
}
