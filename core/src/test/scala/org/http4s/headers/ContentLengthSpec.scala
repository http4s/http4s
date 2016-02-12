package org.http4s.headers

import org.http4s.Http4sSpec

import scalaz.{\/, -\/}

class ContentLengthSpec extends Http4sSpec {
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
      `Content-Length`.fromLong(length) must be_-\/
    }}

    "be consistent with apply" in prop { length: Long => length >= 0 ==> {
      `Content-Length`.fromLong(length) must be_\/-(`Content-Length`(length))
    }}
  }

  "fromString" should {
    "reject negative lengths" in prop { length: Long => length < 0 ==> {
      `Content-Length`.fromString(length.toString) must be_-\/
    }}

    "reject non-numeric strings" in prop { s: String => !s.matches("[0-9]+") ==> {
      `Content-Length`.fromString(s) must be_-\/
    }}

    "be consistent with apply" in prop { length: Long => length >= 0 ==> {
      `Content-Length`.fromString(length.toString) must be_\/-(`Content-Length`(length))
    }}
  }
}
