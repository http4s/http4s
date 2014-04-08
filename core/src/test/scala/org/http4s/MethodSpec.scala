package org.http4s

import org.scalatest.{OptionValues, Matchers, WordSpec}

class MethodSpec extends WordSpec with Matchers with OptionValues {

  def resolve(str: String) = Method.getOrElseCreate(str)

  "A standard method" should {
    "be findable by name" in {
      resolve("GET") should be theSameInstanceAs (Method.Get)
    }

    "be case sensitive" in {
      resolve("get") should not be (Method.Get)
    }
  }

  "PATCH" should {
    "be registered" in {
      resolve("PATCH") should be theSameInstanceAs (Method.Patch)
    }
  }

  "Extension methods" should {
    "be non-idempotent" in {
      resolve("huh").isIdempotent should be (false)
    }
  }
}
