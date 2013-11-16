package org.http4s.util

import org.scalatest.{Matchers, WordSpec}

class CaseInsensitiveStringSpec extends WordSpec with Matchers with CaseInsensitiveStringSyntax {
  "Two case-insensitively equal strings" should {
    "be equal" in {
      "DON'T YELL".ci should equal ("don't yell".ci)
    }

    "have same hash" in {
      "DON'T YELL".ci.## should equal ("don't yell".ci.##)
    }
  }

  "A case-insenstive string" should {
    "preserve the original value" in {
      "What Goes In Must Come Out".ci.toString should equal ("What Goes In Must Come Out")
    }
  }
}
