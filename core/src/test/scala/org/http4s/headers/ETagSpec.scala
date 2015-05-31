package org.http4s
package headers

class ETagSpec extends HeaderParserSpec(ETag) {
  "ETag" should {
    "parse ETag" in {
      val header = ETag("hash")
      hparse(header.value) must_== Some(header)
    }
  }
}
