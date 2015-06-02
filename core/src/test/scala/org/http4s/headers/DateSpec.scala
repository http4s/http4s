package org.http4s
package headers


class DateSpec extends HeaderParserSpec(Date) {

  "Date header" should {
    "parse Date" in {       // mills are lost, get rid of them
    val header = Date(DateTime.now)
      hparse(header.value) must_== Some(header)

      val bad = Header(header.name.toString, "foo")
      hparse(bad.value) must_== None
    }
  }

}
