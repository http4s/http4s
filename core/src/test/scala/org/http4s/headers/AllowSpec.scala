package org.http4s
package headers


class AllowSpec extends HeaderParserSpec(Allow) {

  "Allow Header" should {
    "parse Allow" in {
      val header = Allow(Method.GET, Method.POST)
      hparse(header.value) must_== Some(header)
    }
  }
}
