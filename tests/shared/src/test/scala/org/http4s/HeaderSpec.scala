package org.http4s

import org.http4s.headers._
import org.specs2.mutable.Specification

class HeaderSpec extends Specification {
  "Headers" should {
    "Equate same headers" in {
      val h1 = `Content-Length`.unsafeFromLong(4)
      val h2 = `Content-Length`.unsafeFromLong(4)

      h1 == h2 should beTrue
      h2 == h1 should beTrue
    }

    "not equal different headers" in {
      val h1 = `Content-Length`.unsafeFromLong(4)
      val h2 = `Content-Length`.unsafeFromLong(5)

      h1 == h2 should beFalse
      h2 == h1 should beFalse
    }

    "equal same raw headers" in {
      val h1 = `Content-Length`.unsafeFromLong(44)
      val h2 = Header("Content-Length", "44")

      h1 == h2 should beTrue
      h2 == h1 should beTrue

      val h3 = Date(HttpDate.now).toRaw.parsed
      val h4 = h3.toRaw

      h3 == h4 should beTrue
      h4 == h3 should beTrue
    }

    "not equal same raw headers" in {
      val h1 = `Content-Length`.unsafeFromLong(4)
      val h2 = Header("Content-Length", "5")

      h1 == h2 should beFalse
      h2 == h1 should beFalse
    }

    "equate raw to same raw headers" in {
      val h1 = Header("Content-Length", "4")
      val h2 = Header("Content-Length", "4")

      h1 == h2 should beTrue
      h2 == h1 should beTrue
    }

    "not equate raw to same raw headers" in {
      val h1 = Header("Content-Length", "4")
      val h2 = Header("Content-Length", "5")

      h1 == h2 should beFalse
      h2 == h1 should beFalse
    }
  }

}
