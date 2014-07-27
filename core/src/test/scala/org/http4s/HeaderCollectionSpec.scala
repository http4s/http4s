package org.http4s

import Header._
import org.specs2.mutable.Specification

class HeaderCollectionSpec extends Specification {
  "put" should {
    "replace duplicate headers" in {
      val headers = Headers(
        `Set-Cookie`(Cookie("foo", "bar")),
        `Set-Cookie`(Cookie("baz", "quux"))
      )
      headers.count(_ is `Set-Cookie`) must_== (2)
      headers.put(`Set-Cookie`(Cookie("piff", "paff"))).filter(_ is `Set-Cookie`) must_== Headers(
        `Set-Cookie`(Cookie("piff", "paff"))
      )
    }
  }

  "get by header key" should {
    "also find headers created raw" in {
      val headers = Headers(
        Header.`Cookie`(Cookie("foo", "bar")),
        Header("Cookie", Cookie("baz", "quux").toString)
      )
      headers.get(Header.Cookie).map(_.values.list.length) must beSome (2)
    }
  }

  "get with DefaultHeaderKeys" should {
    "Find the headers with DefaultHeaderKey keys" in {
      val headers = Headers(
        Header.`Set-Cookie`(Cookie("foo", "bar")),
        Header("Accept-Patch",""),
        Header("Access-Control-Allow-Credentials","")
      )
      headers.get(`Accept-Patch`).map(_.value) must beSome ("")
    }
  }
}
