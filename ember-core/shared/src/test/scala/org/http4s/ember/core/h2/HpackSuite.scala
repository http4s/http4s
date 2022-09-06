/*
 * Copyright 2019 http4s.org
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
package ember.core.h2

import cats.data.NonEmptyList
import cats.effect.IO
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalacheck.effect.PropF.forAllF
import org.typelevel.ci.CIString

class HpackSuite extends Http4sSuite {

  implicit val http4sTestingArbitraryForRawHeader: Arbitrary[Header.Raw] =
    Arbitrary {
      for {
        token <- Gen.frequency(
          8 -> genToken,
          1 -> Gen.nonEmptyBuildableOf[String, Char](Gen.asciiChar),
        )
        value <- genFieldValue
      } yield Header.Raw(CIString(token), value)
    }

  test("hpack round-trip") {
    forAllF { (headers: NonEmptyList[Header.Raw]) =>
      for {
        hpack <- Hpack.create[IO]
        bv <- hpack.encodeHeaders(headers.map { case Header.Raw(n, v) => (n.toString, v, false) })
        decoded <- hpack.decodeHeaders(bv)
      } yield assertEquals(decoded, headers.map { case Header.Raw(n, v) => (n.toString, v) })
    }
  }

}
