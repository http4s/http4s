package org.http4s

import java.util.Locale

import scalaz.scalacheck.ScalazProperties

import org.http4s.parser.Rfc2616BasicRules
import org.scalacheck.Prop.forAll

class MethodSpec extends Http4sSpec {
  import Method._

  "fromString is inverse of renderString" in {
    forAll(tokens) { token => fromString(token).map(_.renderString) must beRightDisjunction(token) }
  }

  "only tokens are valid methods" in {
    prop { s: String => fromString(s).isRight must equal (Rfc2616BasicRules.isToken(s)) }
  }

  "standard methods are memoized" in {
    forAll(standardMethods) { m => fromString(m.name) must beRightDisjunction.like { case m1 => m1 must be (m) } }
  }

  "name is case sensitive" in {
    prop { m: Method => {
      val upper = m.name.toUpperCase(Locale.ROOT)
      val lower = m.name.toLowerCase(Locale.ROOT)
      (upper != lower) ==> { fromString(upper) must_!= fromString(lower) }
    }}
  }

  checkAll(ScalazProperties.equal.laws[Method])

  "methods are equal by name" in {
    prop { m: Method => Method.fromString(m.name) must beRightDisjunction(m) }
  }
}
