package org.http4s

import org.http4s.scalacheck.ScalazProperties
import org.scalacheck.Gen._
import org.scalacheck.Prop._

class HttpVersionSpec extends Http4sSpec {
  import HttpVersion._

  checkAll(ScalazProperties.equal.laws[HttpVersion])
  checkAll(ScalazProperties.order.laws[HttpVersion])

  "sort by descending major version" in {
    prop { (x: HttpVersion, y: HttpVersion) => x.major > y.major ==> (x > y) }
  }

  "sort by descending minor version if major versions equal" in {
    forAll(choose(0, 9), choose(0, 9), choose(0, 9)) { (major, xMinor, yMinor) =>
      val x = HttpVersion.fromVersion(major, xMinor).fold(throw _, identity)
      val y = HttpVersion.fromVersion(major, yMinor).fold(throw _, identity)
      (xMinor > yMinor) ==> (x > y)
    }
  }

  "fromString is consistent with toString" in {
    prop { v: HttpVersion => fromString(v.toString) must beSuccessful(v) }
  }

  "protocol is case sensitive" in {
    HttpVersion.fromString("http/1.0") must beFailing(InvalidHttpVersion("http/1.0"))
  }
}
