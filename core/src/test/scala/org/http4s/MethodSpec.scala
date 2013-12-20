package org.http4s

import org.scalatest.{OptionValues, Matchers, WordSpec}

class MethodSpec extends WordSpec with Matchers with OptionValues {
  "A standard method" should {
    "be findable by name" in {
      Method.resolve("GET") should be theSameInstanceAs (Method.Get)
    }

    "be case sensitive" in {
      Method.resolve("get") should not be (Method.Get)
    }
  }

  "PATCH" should {
    "be registered" in {
      Method.resolve("PATCH") should be theSameInstanceAs (Method.Patch)
    }
  }

  "Extension methods" should {
    "be non-idempotent" in {
      Method.resolve("huh").isIdempotent should be (false)
    }
  }
}
