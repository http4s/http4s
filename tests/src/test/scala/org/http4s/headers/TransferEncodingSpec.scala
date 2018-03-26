package org.http4s
package headers

import cats.data.NonEmptyList
import cats.syntax.foldable._
import org.scalacheck.Prop.forAll

class TransferEncodingSpec extends HeaderLaws {
  checkAll("TransferEncoding", headerLaws(`Transfer-Encoding`))

  "render" should {
    "include all the encodings" in {
      `Transfer-Encoding`(TransferCoding.chunked).renderString must_== "Transfer-Encoding: chunked"
      `Transfer-Encoding`(TransferCoding.chunked, TransferCoding.gzip).renderString must_== "Transfer-Encoding: chunked, gzip"
    }
  }

  "parse" should {
    "accept single codings" in {
      `Transfer-Encoding`.parse("chunked").map(_.values) must beRight(
        NonEmptyList.one(TransferCoding.chunked))
    }
    "accept multiple codings" in {
      `Transfer-Encoding`.parse("chunked, gzip").map(_.values) must beRight(
        NonEmptyList.of(TransferCoding.chunked, TransferCoding.gzip))
      `Transfer-Encoding`.parse("chunked,gzip").map(_.values) must beRight(
        NonEmptyList.of(TransferCoding.chunked, TransferCoding.gzip))
    }
  }

  "hasChunked" should {
    "detect chunked" in {
      forAll { t: `Transfer-Encoding` =>
        t.hasChunked must_== (t.values.contains_(TransferCoding.chunked))
      }
    }
  }
}
