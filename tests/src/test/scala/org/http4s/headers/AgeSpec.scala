package org.http4s.headers

import cats.implicits._

import org.http4s.{ParseFailure, ParseResult}

import scala.concurrent.duration._

class AgeSpec extends HeaderLaws {
  checkAll("Age", headerLaws(Age))

  "render" should {
    "age in seconds" in {
      Age(120.seconds).map(_.renderString) must_== ParseResult.success("Age: 120")
    }
  }

  "build" should {
    "build correctly for positives" in {
      Age(0.seconds).map(_.value) must beLike { case Right("0") => ok }
    }
    "fail for negatives" in {
      Age(-10.seconds).map(_.value) must beLeft
    }
    "build unsafe for positives" in {
      Age.unsafeFromDuration(0.seconds).value must_== "0"
    }
    "fail unsafe for negatives" in {
      Age.unsafeFromDuration(-10.seconds).value must throwA[ParseFailure]
    }
  }

  "parse" should {
    "accept duration on seconds" in {
      Age.parse("120").map(_.age) must beRight(120.seconds)
    }
    "reject negative values" in {
      Age.parse("-120").map(_.age) must beLeft
    }
  }
}
