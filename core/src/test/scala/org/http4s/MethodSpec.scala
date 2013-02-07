package org.http4s

import org.specs2.mutable.Specification

class MethodSpec extends Specification {
  "A standard method" should {
    "be findable by uppercase" in {
      Method("GET") should beSome(Method.Get)
    }

    "be findable by lowercase" in {
      Method("get") should beSome(Method.Get)
    }
  }

  "PATCH" should {
    "be registered" in {
      Method("PATCH") should beSome(Method.Patch)
    }
  }

  "Extension methods" should {
    "not be registered by default" in {
      Method("huh") should beNone
    }
  }
}
