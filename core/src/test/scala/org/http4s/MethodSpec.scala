package org.http4s

import org.specs2.mutable.Specification

class MethodSpec extends Specification {
  "A standard method" should {
    "be findable by name" in {
      Method.get("GET") should beSome(Method.Get)
    }

    "be case sensitive" in {
      Method.get("get") should beNone`
    }
  }

  "PATCH" should {
    "be registered" in {
      Method.get("PATCH") should beSome(Method.Patch)
    }
  }

  "Extension methods" should {
    "not be returned by get" in {
      Method.get("huh") should beNone
    }

    "be created on demand by apply" in {
      Method("huh") should beAnInstanceOf[ExtensionMethod]
    }

    "not be registered by apply" in {
      Method("huh")
      Method.get("huh") should beNone
    }
  }
}
