package org.http4s

import org.specs2.mutable.Specification

class MethodSpec extends Specification {

  def resolve(str: String) = Method.getOrElseCreate(str)

  "A standard method" should {
    "be findable by name" in {
      resolve("GET") must be (Method.Get)
    }

    "be case sensitive" in {
      resolve("get") must_!= (Method.Get)
    }
  }

  "PATCH" should {
    "be registered" in {
      resolve("PATCH") must be (Method.Patch)
    }
  }

  "Extension methods" should {
    "be non-idempotent" in {
      resolve("huh").isIdempotent should beFalse
    }
  }
}
