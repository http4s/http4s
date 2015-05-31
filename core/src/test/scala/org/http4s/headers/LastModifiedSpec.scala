package org.http4s
package headers

class LastModifiedSpec extends HeaderParserSpec(`Last-Modified`) {

  "Last-Modified" should {

    "parse Last-Modified" in {
      val header = `Last-Modified`(DateTime.now)
      hparse(header.value) must_== Some(header)

      val bad = Header(header.name.toString, "foo")
      hparse(bad.value) must_== None
    }
  }

}
