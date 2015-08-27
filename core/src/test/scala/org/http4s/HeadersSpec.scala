package org.http4s

import org.http4s.headers._
import org.http4s.util.CaseInsensitiveString._
import org.specs2.mutable.Specification

class HeadersSpec extends Specification {

  val clength = `Content-Length`(10)
  val raw = Header.Raw("raw-header".ci, "Raw value")

  val base = Headers(clength.toRaw, raw)

  "Headers" should {
    "Not find a header that isn't there" in {
      base.get(`Content-Base`) should beNone
    }

    "Find an existing header and return its parsed form" in {
      base.get(`Content-Length`) should beSome (clength)
      base.get("raw-header".ci) should beSome (raw)
    }

    "Replaces headers" in {
      val newlen = `Content-Length`(0)
      base.put(newlen).get(newlen.key) should beSome(newlen)
      base.put(newlen.toRaw).get(newlen.key) should beSome (newlen)
    }

    "also find headers created raw" in {
      val headers = Headers(
        org.http4s.headers.`Cookie`(org.http4s.Cookie("foo", "bar")),
        Header("Cookie", org.http4s.Cookie("baz", "quux").toString)
      )
      headers.get(org.http4s.headers.Cookie).map(_.values.list.length) must beSome (2)
    }

    "Find the headers with DefaultHeaderKey keys" in {
      val headers = Headers(
        `Set-Cookie`(org.http4s.Cookie("foo", "bar")),
        Header("Accept-Patch",""),
        Header("Access-Control-Allow-Credentials","")
      )
      headers.get(`Accept-Patch`).map(_.value) must beSome ("")
    }

    "Remove duplicate headers which are not of type Recurring on concatenation (++)" in {
      val hs = Headers(clength) ++ Headers(clength)
      hs.toList.length must_== 1
      hs.head must_== clength
    }

    "Allow multiple Set-Cookie headers" in {
      val h1 = `Set-Cookie`(org.http4s.Cookie("foo1", "bar1")).toRaw
      val h2 = `Set-Cookie`(org.http4s.Cookie("foo2", "bar2")).toRaw
      val hs = Headers(clength) ++ Headers(h1) ++ Headers(h2)
      hs.filter(_.parsed match { case `Set-Cookie`(_) => true; case _ => false }).size must_== 2
      hs.exists(_ == clength) must_== true
    }

    "Work with Raw headers (++)" in {
      val h1 = `Accept-Encoding`(ContentCoding("foo".ci)).toRaw
      val h2 = `Accept-Encoding`(ContentCoding("bar".ci)).toRaw
      val hs = Headers(clength.toRaw) ++ Headers(h1) ++ Headers(h2)
      hs.get(`Accept-Encoding`) must_== Some(`Accept-Encoding`(ContentCoding("foo".ci), ContentCoding("bar".ci)))
      hs.exists(_ == clength) must_== true
    }

    "Avoid making copies if there are duplicate collections" in {
      base ++ Headers.empty eq base must_== true
      Headers.empty ++ base eq base must_== true
    }

    "Preserve original headers when processing" in {
      val rawAuth = Header("Authorization", "test this")

      // Mapping to strings because Header equality is based on the *parsed* version
      (Headers(rawAuth) ++ base).map(_.toString) must contain(===(rawAuth.toString))
    }
  }
}
