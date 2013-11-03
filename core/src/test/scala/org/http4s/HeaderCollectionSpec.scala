package org.http4s

import org.scalatest.{WordSpec, Matchers}

class HeaderCollectionSpec extends WordSpec with Matchers {
  "put" should {
    "replace duplicate headers" in {
      val headers = HeaderCollection(
        Headers.SetCookie(Cookie("foo", "bar")),
        Headers.SetCookie(Cookie("baz", "quux"))
      )
      headers.getAll(Headers.SetCookie) should have length (2)
      headers.put(Headers.SetCookie(Cookie("piff", "paff"))).getAll(Headers.SetCookie) should be (Seq(
        Headers.SetCookie(Cookie("piff", "paff"))
      ))
    }
  }

  "getAll by header key" should {
    "find raw headers of the same type" in {
      val headers = HeaderCollection(
        Headers.SetCookie(Cookie("foo", "bar")),
        Headers.RawHeader("Set-Cookie", Cookie("baz", "quux").toString)
      )
      headers.getAll(Headers.SetCookie) should have length (2)
    }
  }
}
