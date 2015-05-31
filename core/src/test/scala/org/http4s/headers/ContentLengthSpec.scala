package org.http4s
package headers


class ContentLengthSpec extends HeaderParserSpec(`Content-Length`) {
  "Content-Length" should {
    "parse Content-Length" in {
      val header = `Content-Length`(4)
      hparse(header.value) must_== Some(header)

      val bad = Header(header.name.toString, "foo")
      hparse(bad.value) must_== None
    }
  }
}
