package org.http4s.util

import org.scalatest.{Matchers, WordSpec}

class LowercaseSpec extends WordSpec with Matchers with LowercaseSyntax {
  ".lowercase on a String" should {
    "convert it to lowercase" in {
      "DON'T YELL".lowercase should equal ("don't yell")
    }
  }
}
