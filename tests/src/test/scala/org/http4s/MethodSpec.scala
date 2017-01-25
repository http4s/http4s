package org.http4s

import java.util.Locale

import cats.kernel.laws._
import org.http4s.parser.Rfc2616BasicRules
import org.scalacheck.Prop.forAll

import Http4s._

class MethodSpec extends Http4sSpec {
  import Method._

  "parses own string rendering to equal value" in {
    forAll(genToken) { token => fromString(token).map(_.renderString) must beRight(token) }
  }

  "only tokens are valid methods" in {
    prop { s: String => fromString(s).isRight must_== (Rfc2616BasicRules.isToken(s)) }
  }

  "name is case sensitive" in {
    prop { m: Method => {
      val upper = m.name.toUpperCase(Locale.ROOT)
      val lower = m.name.toLowerCase(Locale.ROOT)
      (upper != lower) ==> { fromString(upper) must_!= fromString(lower) }
    }}
  }

  checkAll("Method", OrderLaws[Method].eqv)

  "methods are equal by name" in {
    prop { m: Method => Method.fromString(m.name) must beRight(m) }
  }

  "safety implies idempotence" in {
    foreach(Method.registered.filter(_.isSafe)) { _.isIdempotent }
  }
}
