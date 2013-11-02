package org.http4s

import org.scalatest.{OptionValues, Matchers, WordSpec}

class MethodSpec extends WordSpec with Matchers with OptionValues {
  "A standard method" should {
    "be findable by name" in {
      Methods.getForKey("GET").value should equal (Methods.Get)
    }

    "be case sensitive" in {
      Methods.getForKey("get") should be (None)
    }
  }

  "PATCH" should {
    "be registered" in {
      Methods.getForKey("PATCH").value should equal (Methods.Patch)
    }
  }

  "Extension methods" should {
    "not be returned by get" in {
      Methods.get("huh") should be (None)
    }

    "not be registered by apply" in {
      Methods("huh")
      Methods.get("huh") should be (None)
    }
  }
}
