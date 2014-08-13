package org.http4s

import Header._
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

    "Remove duplicate headers which are not of type Recurring on concatenation (++)" in {
      val hs = Headers(clength) ++ Headers(clength)
      hs.length must_== 1
      hs.head must_== clength
    }

    "Fuse duplicate headers which are of type Recurring on concatenation (++)" in {
      val h1 = `Accept-Encoding`(ContentCoding("foo".ci))
      val h2 = `Accept-Encoding`(ContentCoding("bar".ci))
      val hs = Headers(clength) ++ Headers(h1) ++ Headers(h2)
      hs.length must_== 2
      hs.exists(_ == `Accept-Encoding`(ContentCoding("foo".ci), ContentCoding("bar".ci))) must_== true
      hs.exists(_ == clength) must_== true
    }

    "Avoid making copies if there are duplicate collections" in {
      base ++ Headers.empty eq base must_== true
      Headers.empty ++ base eq base must_== true
    }

  }
}
