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
package multipart

import cats.effect.IO
import cats.effect.std.Random
import org.scalacheck.effect.PropF._

class MultipartsSuite extends Http4sSuite {
  private val multiparts =
    Random
      .scalaUtilRandom[IO]
      .map(Multiparts.fromRandom[IO])
      .syncStep(Int.MaxValue)
      .unsafeRunSync()
      .toOption
      .get
  private val alphabet =
    Set('A' to 'Z': _*) ++ Set('a' to 'z': _*) ++ Set('0' to '9': _*) ++ Set('_', '-')

  test("generates 30-70 character boundaries") {
    forAllF { (_: Unit) =>
      for {
        b <- multiparts.boundary
        len = b.value.length
      } yield assert(len >= 30 && len <= 70, b.value)
    }
  }

  test("pulls boundaries from correct alphabet") {
    forAllF { (_: Unit) =>
      multiparts.boundary.map(b => assert(b.value.forall(alphabet), b.value))
    }
  }
}
