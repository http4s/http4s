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

import cats.kernel.laws.discipline.{BoundedEnumerableTests, HashTests, OrderTests}
import org.http4s.laws.discipline.ArbitraryInstances._
import org.scalacheck.Gen._
import org.scalacheck.Prop._

class HttpVersionSuite extends Http4sSuite {
  import HttpVersion._

  checkAll("HttpVersion", OrderTests[HttpVersion].order)

  checkAll("HttpVersion", HashTests[HttpVersion].hash)

  checkAll("HttpVersion", BoundedEnumerableTests[HttpVersion].boundedEnumerable)

  test("sort by descending major version") {
    forAll { (x: HttpVersion, y: HttpVersion) =>
      x.major > y.major ==> (x > y)
    }
  }

  test("sort by descending minor version if major versions equal") {
    forAll(choose(0, 9), choose(0, 9), choose(0, 9)) { (major, xMinor, yMinor) =>
      val x = HttpVersion.fromVersion(major, xMinor).yolo
      val y = HttpVersion.fromVersion(major, yMinor).yolo
      (xMinor > yMinor) ==> (x > y)
    }
  }

  test("fromString is consistent with toString") {
    forAll { (v: HttpVersion) =>
      fromString(v.toString) == Right(v)
    }
  }

  test("protocol is case sensitive") {
    assert(HttpVersion.fromString("http/1.0").isLeft)
  }
}
