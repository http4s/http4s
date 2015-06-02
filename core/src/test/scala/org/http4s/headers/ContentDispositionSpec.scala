package org.http4s
package headers


class ContentDispositionSpec extends HeaderParserSpec(`Content-Disposition`) {
  "Content Disposition" should {
    "parse Content-Disposition" in {
      val header = `Content-Disposition`("foo", Map("one" -> "two", "three" -> "four"))
      hparse(header.value) must_== Some(header)

      val bad = Header(header.name.toString, "foo; bar")
      hparse(bad.value) must_== None
    }
  }
}
