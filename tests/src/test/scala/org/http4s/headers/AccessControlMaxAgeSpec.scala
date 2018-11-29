package org.http4s.headers

import org.http4s.{ParseResult}
import org.scalacheck.Prop.forAll
import scala.concurrent.duration._

class AccessControlMaxAgeSpec extends HeaderLaws {
  checkAll("Access-Control-Max-Age", headerLaws(`Access-Control-Max-Age`))

  "render" should {
    "Access-Control-Max-Age in seconds" in {
      `Access-Control-Max-Age`.fromLong(120).right.map(_.renderString) must_== ParseResult.success("Access-Control-Max-Age: 120")
    }
  }

  "build" should {
    "build correctly for positives" in {
      `Access-Control-Max-Age`.fromLong(0).right.map(_.value) must beLike { case Right("0") => ok }
    }
    "build correctly for negatives" in {
      `Access-Control-Max-Age`.fromLong(-1).right.map(_.value) must beLike { case Right("-1") => ok }
    }
    "build unsafe for positives" in {
      `Access-Control-Max-Age`.unsafeFromDuration(0.seconds).value must_== "0"
      `Access-Control-Max-Age`.unsafeFromLong(10).value must_== "10"
    }
    "build unsafe for negatives" in {
      `Access-Control-Max-Age`.unsafeFromDuration(-1.seconds).value must_== "-1"
      `Access-Control-Max-Age`.unsafeFromLong(-1).value must_== "-1"
    }
  }

  "produce duration" should {
    "safe" in {
      `Access-Control-Max-Age`.unsafeFromLong(10).duration must_== Option(10.seconds)
    }
    "unsafe" in {
      `Access-Control-Max-Age`.unsafeFromLong(10).unsafeDuration must_== 10.seconds
    }
  }

  "parse" should {
    "accept duration on seconds" in {
      `Access-Control-Max-Age`.parse("120").right.map(_.age) must beRight(120)
    }
    "reject negative values" in {
      `Access-Control-Max-Age`.parse("-1").right.map(_.age) must beRight(-1)
    }
    "roundtrip" in {
      forAll { l: Long =>
        (l >= 0) ==> {
          `Access-Control-Max-Age`.fromLong(l).right.map(_.value).right.flatMap(`Access-Control-Max-Age`.parse) must_== `Access-Control-Max-Age`.fromLong(l)
        }
      }
    }
  }
}
