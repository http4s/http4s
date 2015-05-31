package org.http4s
package headers

class HostSpec extends HeaderParserSpec(Host) {

  "Host header" should {

    "parse Host" in {
      val header1 = Host("foo", Some(5))
      hparse(header1.value) must_== Some(header1)

      val header2 = Host("foo", None)
      hparse(header2.value) must_== Some(header2)

      val bad = Header(header1.name.toString, "foo:bar")
      hparse(bad.value) must_== None
    }
  }

}
