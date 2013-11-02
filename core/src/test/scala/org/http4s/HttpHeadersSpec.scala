package org.http4s

import org.scalatest.{WordSpec, Matchers}

class HttpHeadersSpec extends WordSpec with Matchers {
  "put" should {
    "replace duplicate headers" in {
      val headers = HttpHeaders(
        HttpHeaders.SetCookie(HttpCookie("foo", "bar")),
        HttpHeaders.SetCookie(HttpCookie("baz", "quux"))
      )
      headers.getAll(HttpHeaders.SetCookie) should have length (2)
      headers.put(HttpHeaders.SetCookie(HttpCookie("piff", "paff"))).getAll(HttpHeaders.SetCookie) should be (Seq(
        HttpHeaders.SetCookie(HttpCookie("piff", "paff"))
      ))
    }
  }
}
