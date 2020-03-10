package org.http4s.headers

class ContentLengthSpec extends HeaderLaws {
  checkAll("Content-Length", headerLaws(`Content-Length`))

  "fromLong" should {
    "reject negative lengths" in prop { (length: Long) =>
      length < 0 ==> {
        `Content-Length`.fromLong(length) must beLeft
      }
    }
    "accept non-negative lengths" in prop { (length: Long) =>
      length >= 0 ==> {
        `Content-Length`.fromLong(length).map(_.length) must beRight(length)
      }
    }
  }

  "fromString" should {
    "reject negative lengths" in prop { (length: Long) =>
      length < 0 ==> {
        `Content-Length`.parse(length.toString) must beLeft
      }
    }

    "reject non-numeric strings" in prop { (s: String) =>
      !s.matches("[0-9]+") ==> {
        `Content-Length`.parse(s) must beLeft
      }
    }

    "be consistent with apply" in prop { (length: Long) =>
      length >= 0 ==> {
        `Content-Length`.parse(length.toString) must_== `Content-Length`.fromLong(length)
      }
    }
    "roundtrip" in prop { (l: Long) =>
      (l >= 0) ==> {
        `Content-Length`
          .fromLong(l)
          .map(_.value)
          .flatMap(`Content-Length`.parse) must_== `Content-Length`.fromLong(l)
      }
    }
  }

  "modify" should {
    "update the length if positive" in prop { (length: Long) =>
      length >= 0 ==> {
        `Content-Length`.zero.modify(_ + length) must_== `Content-Length`
          .fromLong(length)
          .toOption
      }
    }
    "fail to update if the result is negative" in prop { (length: Long) =>
      length > 0 ==> {
        `Content-Length`.zero.modify(_ - length) must beNone
      }
    }
  }
}
