package org.http4s
package headers


class ConnectionSpec extends HeaderParserSpec(Connection) {
  "Connection header" should {
    "parse Connection" in {
      val header = Connection("closed".ci)
      hparse(header.value) must_== Some(header)
    }
  }
}
