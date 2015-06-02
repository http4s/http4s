package org.http4s
package headers

class TransferEncoding extends HeaderParserSpec(`Transfer-Encoding`) {

  "Transfer-Encoding" should {

    "parse Transfer-Encoding" in {
      val header = `Transfer-Encoding`(TransferCoding.chunked)
      hparse(header.value) must_== Some(header)

      val header2 = `Transfer-Encoding`(TransferCoding.compress)
      hparse(header2.value) must_== Some(header2)
    }
  }

}
