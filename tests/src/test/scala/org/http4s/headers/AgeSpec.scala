package org.http4s.headers

import cats.implicits._

import scala.concurrent.duration._

class AgeSpec extends HeaderLaws {
  checkAll("Age", headerLaws(Age))

  "render" should {
    "age in seconds" in {
      Age(120.seconds).renderString must_== "Age: 120"
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
