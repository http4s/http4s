package org.http4s
package headers

import java.net.InetAddress

import scalaz.NonEmptyList

class XForwardedForSpec extends HeaderParserSpec(`X-Forwarded-For`) {
  "X-Forwarded-For" should {
    "parse X-Forward-Spec" in {
      val header1 = `X-Forwarded-For`(NonEmptyList(Some(InetAddress.getLocalHost)))
      hparse(header1.value) must_== Some(header1)

      val header2 = `X-Forwarded-For`(NonEmptyList(Some(InetAddress.getLocalHost),
        Some(InetAddress.getLoopbackAddress)))
      hparse(header2.value) must_== Some(header2)

      val bad = Header(header1.name.toString, "foo")
      hparse(bad.value) must_== None
    }
  }
}
