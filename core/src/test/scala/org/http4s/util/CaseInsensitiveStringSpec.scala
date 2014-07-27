package org.http4s.util

import org.specs2.mutable.Specification

class CaseInsensitiveStringSpec extends Specification with CaseInsensitiveStringSyntax {
  "Two case-insensitively equal strings" should {
    "be equal" in {
      "DON'T YELL".ci must be_== ("don't yell".ci)
    }

    "have same hash" in {
      "DON'T YELL".ci.## must be_== ("don't yell".ci.##)
    }
  }

  "A case-insenstive string" should {
    "preserve the original value" in {
      "What Goes In Must Come Out".ci.toString must be_== ("What Goes In Must Come Out")
    }
  }
}
