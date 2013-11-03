package org.http4s

import org.scalatest.{Matchers, WordSpec}

/**
 * @author Bryce Anderson
 *         Created on 11/3/13
 */
class HeaderSpec extends WordSpec with Matchers {
  "Headers" should {
    "Equate same headers" in {
      val h1 = Headers.`Content-Length`(4)
      val h2 = Headers.`Content-Length`(4)

      h1 should equal (h2)
    }

    "not equal different headers" in {
      val h1 = Headers.`Content-Length`(4)
      val h2 = Headers.`Content-Length`(5)

      h1 shouldNot equal (h2)
    }
  }

}
