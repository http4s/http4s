package org.http4s

import cats.data.NonEmptyList
import cats.kernel.laws.OrderLaws

class TransferCodingSpec extends Http4sSpec {

  "parse" should {
    "parse single items" in {
      prop { (a: TransferCoding) =>
        TransferCoding.parseList(a.coding) must_== ParseResult.success(NonEmptyList.one(a))
      }
    }
    "parse multiple items" in {
      TransferCoding.parseList("gzip, chunked") must_== ParseResult.success(NonEmptyList.of(TransferCoding.gzip, TransferCoding.chunked))
    }
  }

  checkAll("order", OrderLaws[TransferCoding].order)
}
