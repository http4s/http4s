package org.http4s

import org.scalatest.{OptionValues, WordSpec, Matchers}
import Header._

class HeaderCollectionSpec extends WordSpec with Matchers with OptionValues {
  "put" should {
    "replace duplicate headers" in {
      val headers = HeaderCollection(
        `Set-Cookie`(Cookie("foo", "bar")),
        `Set-Cookie`(Cookie("baz", "quux"))
      )
      headers.count(_ is `Set-Cookie`) should equal (2)
      headers.put(`Set-Cookie`(Cookie("piff", "paff"))).filter(_ is `Set-Cookie`) should be (Seq(
        `Set-Cookie`(Cookie("piff", "paff"))
      ))
    }
  }

  "get by header key" should {
    "also find headers created raw" in {
      val headers = HeaderCollection(
        Header.`Cookie`(Cookie("foo", "bar")),
        Header("Cookie", Cookie("baz", "quux").toString)
      )
      headers.get(Header.Cookie).value.values.list should have length (2)
    }
  }

  "get with DefaultHeaderKeys" should {
    "Find the headers with DefaultHeaderKey keys" in {
      val headers = HeaderCollection(
        Header.`Set-Cookie`(Cookie("foo", "bar")),
        Header("Accept-Patch",""),
        Header("Access-Control-Allow-Credentials","")
      )
      headers.get(`Accept-Patch`).value.value should equal ("")
    }
  }
}
