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

  }


}
