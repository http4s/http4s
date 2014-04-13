package org.http4s

import org.scalatest.{Matchers, WordSpec}
import org.joda.time.DateTime

/**
 * @author Bryce Anderson
 *         Created on 11/3/13
 */
class HeaderSpec extends WordSpec with Matchers {
  "Headers" should {
    "Equate same headers" in {
      val h1 = Header.`Content-Length`(4)
      val h2 = Header.`Content-Length`(4)

      h1 == h2 should equal (true)
      h2 == h1 should equal (true)
    }

    "not equal different headers" in {
      val h1 = Header.`Content-Length`(4)
      val h2 = Header.`Content-Length`(5)

      h1 == h2 should equal (false)
      h2 == h1 should equal (false)
    }

    "equal same raw headers" in {
      val h1 = Header.`Content-Length`(44)
      val h2 = Header("Content-Length", "44")

      h1 == h2 should equal (true)
      h2 == h1 should equal (true)

      val h3 = Header.Date(new DateTime()).toRaw.parsed
      val h4 = h3.toRaw

      h3 == h4 should equal (true)
      h4 == h3 should equal (true)
    }

    "not equal same raw headers" in {
      val h1 = Header.`Content-Length`(4)
      val h2 = Header("Content-Length", "5")

      h1 == h2 should equal (false)
      h2 == h1 should equal (false)
    }

    "equate raw to same raw headers" in {
      val h1 = Header("Content-Length", "4")
      val h2 = Header("Content-Length", "4")

      h1 == h2 should equal (true)
      h2 == h1 should equal (true)
    }

    "not equate raw to same raw headers" in {
      val h1 = Header("Content-Length", "4")
      val h2 = Header("Content-Length", "5")

      h1 == h2 should equal (false)
      h2 == h1 should equal (false)
    }
  }

}
