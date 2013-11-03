package org.http4s

import org.scalatest.{WordSpec, Matchers}

class HeaderCollectionSpec extends WordSpec with Matchers {
  "put" should {
    "replace duplicate headers" in {
      val headers = HeaderCollection(
        Headers.`Set-Cookie`(Cookie("foo", "bar")),
        Headers.`Set-Cookie`(Cookie("baz", "quux"))
      )
      headers.getAll(Headers.`Set-Cookie`) should have length (2)
      headers.put(Headers.`Set-Cookie`(Cookie("piff", "paff"))).getAll(Headers.`Set-Cookie`) should be (Seq(
        Headers.`Set-Cookie`(Cookie("piff", "paff"))
      ))
    }
  }

  "getAll by header key" should {
    "also find headers created raw" in {
      val headers = HeaderCollection(
        Headers.`Set-Cookie`(Cookie("foo", "bar")),
        Headers.RawHeader("Set-Cookie", Cookie("baz", "quux").toString)
      )
      headers.getAll(Headers.`Set-Cookie`) should have length (2)
    }
  }
}
