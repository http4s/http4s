package org.http4s.headers

class ContentLengthSpec extends HeaderLaws {
  checkAll("Content-Length", headerLaws(`Content-Length`))

  "apply" should {
    "reject negative lengths" in prop { length: Long => length < 0 ==> {
      `Content-Length`(length) must throwA[IllegalArgumentException]
    }}

    "accept non-negative lengths" in prop { length: Long => length >= 0 ==> {
      `Content-Length`(length).length must_== (length)
    }}
  }

  "fromLong" should {
    "reject negative lengths" in prop { length: Long => length < 0 ==> {
      `Content-Length`.fromLong(length) must beXorLeft
    }}

    "be consistent with apply" in prop { length: Long => length >= 0 ==> {
      `Content-Length`.fromLong(length) must beXorRight(`Content-Length`(length))
    }}
  }

  "fromString" should {
    "reject negative lengths" in prop { length: Long => length < 0 ==> {
      `Content-Length`.parse(length.toString) must beXorLeft
    }}

    "reject non-numeric strings" in prop { s: String => !s.matches("[0-9]+") ==> {
      `Content-Length`.parse(s) must beXorLeft
    }}

    "be consistent with apply" in prop { length: Long => length >= 0 ==> {
      `Content-Length`.parse(length.toString) must beXorRight(`Content-Length`(length))
    }}
  }
}
