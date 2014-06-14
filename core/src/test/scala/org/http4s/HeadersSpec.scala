package org.http4s

import org.scalatest.{Matchers, WordSpec}
import Header._
import org.http4s.util.CaseInsensitiveString._

/**
 * Created by Bryce Anderson on 6/14/14.
 */
class HeadersSpec extends WordSpec with Matchers {

  val clength = `Content-Length`(10)
  val raw = Header.Raw("raw-header".ci, "Raw value")

  val base = Headers(clength.toRaw, raw)

  "Headers" should {
    "Not find a header that isn't there" in {
      base.get(`Content-Base`) should equal(None)
    }

    "Find an existing header and return its parsed form" in {
      base.get(`Content-Length`) should equal(Some(clength))
      base.get("raw-header".ci) should equal(Some(raw))
    }

    "Replaces headers" in {
      val newlen = `Content-Length`(0)

      base.put(newlen).get(newlen.key) should equal(Some(newlen))
      base.put(newlen.toRaw).get(newlen.key) should equal(Some(newlen))
    }

  }


}
