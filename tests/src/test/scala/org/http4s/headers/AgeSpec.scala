package org.http4s.headers

import org.http4s.{ParseFailure, ParseResult}
import org.scalacheck.Prop.forAll
import scala.concurrent.duration._

class AgeSpec extends HeaderLaws {
  checkAll("Age", headerLaws(Age))

  "render" should {
    "age in seconds" in {
      Age.fromLong(120).map(_.renderString) must_== ParseResult.success("Age: 120")
    }
  }

  "build" should {
    "build correctly for positives" in {
      Age.fromLong(0).map(_.value) must beLike { case Right("0") => ok }
    }
    "fail for negatives" in {
      Age.fromLong(-10).map(_.value) must beLeft
    }
    "build unsafe for positives" in {
      Age.unsafeFromDuration(0.seconds).value must_== "0"
      Age.unsafeFromLong(10).value must_== "10"
    }
    "fail unsafe for negatives" in {
      Age.unsafeFromDuration(-10.seconds).value must throwA[ParseFailure]
      Age.unsafeFromLong(-10).value must throwA[ParseFailure]
    }
  }

  "produce duration" should {
    "safe" in {
      Age.unsafeFromLong(10).duration must_== Option(10.seconds)
    }
    "unsafe" in {
      Age.unsafeFromLong(10).unsafeDuration must_== 10.seconds
    }
  }

  "parse" should {
    "accept duration on seconds" in {
      Age.parse("120").map(_.age) must beRight(120)
    }
    "reject negative values" in {
      Age.parse("-120").map(_.age) must beLeft
    }
    "roundtrip" in {
      forAll { l: Long =>
        (l >= 0) ==> {
          Age.fromLong(l).map(_.value).flatMap(Age.parse) must_== Age.fromLong(l)
        }
      }
    }
  }
}
