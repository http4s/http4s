/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
import cats.kernel.laws.discipline.OrderTests
import org.scalacheck.Gen._
import org.scalacheck.Prop._

class HttpVersionSpec extends Http4sSpec {
  import HttpVersion._

  checkAll("HttpVersion", OrderTests[HttpVersion].order)

  "sort by descending major version" in {
    prop { (x: HttpVersion, y: HttpVersion) =>
      x.major > y.major ==> (x > y)
    }
  }

  "sort by descending minor version if major versions equal" in {
    forAll(choose(0, 9), choose(0, 9), choose(0, 9)) { (major, xMinor, yMinor) =>
      val x = HttpVersion.fromVersion(major, xMinor).yolo
      val y = HttpVersion.fromVersion(major, yMinor).yolo
      (xMinor > yMinor) ==> (x > y)
    }
  }

  "fromString is consistent with toString" in {
    prop { (v: HttpVersion) =>
      fromString(v.toString) must beRight(v)
    }
  }

  "protocol is case sensitive" in {
    HttpVersion.fromString("http/1.0") must beLeft
  }
}
