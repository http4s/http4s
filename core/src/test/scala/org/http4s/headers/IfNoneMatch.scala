package org.http4s
package headers


class IfNoneMatch extends HeaderParserSpec(`If-None-Match`) {

  "If-None-Match" should {

    "parse If-None-Match" in {
      val header = `If-None-Match`("hash")
      hparse(header.value) must_== Some(header)
    }
  }
}
