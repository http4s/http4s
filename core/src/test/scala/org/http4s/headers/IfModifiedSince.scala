package org.http4s
package headers


class IfModifiedSince extends HeaderParserSpec(`If-Modified-Since`) {

  "If-Modified-Since" should {

    "parse If-Modified-Since" in {
      val header = `If-Modified-Since`(DateTime.now)
      hparse(header.value) must_== Some(header)

      val bad = Header(header.name.toString, "foo")
      hparse(bad.value) must_== None
    }
  }

}
