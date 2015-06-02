package org.http4s
package headers


class ContentEncodingSpec extends HeaderParserSpec(`Content-Encoding`) {
  "Content-Encoding header" should {
    "parse Content-Encoding" in {
      val header = `Content-Encoding`(ContentCoding.`pack200-gzip`)
      hparse(header.value) must_== Some(header)
    }
  }
}
