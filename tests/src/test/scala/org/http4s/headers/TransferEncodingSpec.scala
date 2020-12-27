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
package headers

import cats.data.NonEmptyList
import cats.syntax.foldable._
import org.scalacheck.Prop.forAll

class TransferEncodingSpec extends HeaderLaws {
  checkAll("TransferEncoding", headerLaws(`Transfer-Encoding`))

  "render" should {
    "include all the encodings" in {
      `Transfer-Encoding`(TransferCoding.chunked).renderString must_== "Transfer-Encoding: chunked"
      `Transfer-Encoding`(
        TransferCoding.chunked,
        TransferCoding.gzip).renderString must_== "Transfer-Encoding: chunked, gzip"
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
      forAll { (t: `Transfer-Encoding`) =>
        t.hasChunked must_== t.values.contains_(TransferCoding.chunked)
      }
    }
  }
}
