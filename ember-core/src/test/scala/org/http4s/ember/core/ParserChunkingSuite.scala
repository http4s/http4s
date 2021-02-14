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

package org.http4s.ember.core

import org.http4s.Http4sSuite
import org.scalacheck.Gen
import org.scalacheck.Prop._

class ParserChunkingSuite extends Http4sSuite {
  
  def subdivided[A](as: List[A]): Gen[List[List[A]]] = {
    def go(out: List[List[A]], remaining: Int): Gen[List[List[A]]] = {
      // println(s"go: $out")
      if (remaining > 0) {
        Gen.chooseNum(0, out.length - 1).flatMap { idx =>
          // println(s"index: $idx")
          val prefix = out.take(idx)
          val curr = out(idx)
          val suffix = out.drop(idx + 1)

          // println(s"prefix: $prefix, curr: $curr, suffix: $suffix")

          Gen.chooseNum(0, curr.length - 1).flatMap { splitIdx => 
            val (l, r) = curr.splitAt(splitIdx)
            val split = List(l, r).filter(!_.isEmpty)
            // println(s"split: $split")
            val next = List(prefix, split, suffix).filter(!_.isEmpty).flatten
            // println(s"next: $next")
            // println("----")
            go(next, remaining - 1)
          }
        }
      } else Gen.const(out)
    }

    go(List(as), 5)
  }

  test("subdivided") {
    val gen: Gen[(List[Int], List[List[Int]])] = for {
      size <- Gen.choose(1, 100)
      list <- Gen.listOfN(size, Gen.choose(Int.MinValue, Int.MaxValue))
      divided <- subdivided(list)
    } yield (list, divided)

    forAll(gen) { case (a, b) =>
      a.length == b.map(_.length).sum
    }
  }


}
