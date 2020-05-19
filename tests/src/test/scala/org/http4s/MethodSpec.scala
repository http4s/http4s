/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.Hash
import cats.kernel.laws.discipline._
import java.util.Locale
import org.http4s.parser.Rfc2616BasicRules
import org.scalacheck.Prop.forAll

class MethodSpec extends Http4sSpec {
  import Method._

  checkAll("Method", HashTests[Method].eqv)
  checkAll("Hash[Method]", SerializableTests.serializable(Hash[Method]))

  "parses own string rendering to equal value" in {
    forAll(genToken) { token =>
      fromString(token).map(_.renderString) must beRight(token)
    }
  }

  "only tokens are valid methods" in {
    prop { (s: String) =>
      fromString(s).isRight must_== Rfc2616BasicRules.isToken(s)
    }
  }

  "name is case sensitive" in {
    prop { (m: Method) =>
      val upper = m.name.toUpperCase(Locale.ROOT)
      val lower = m.name.toLowerCase(Locale.ROOT)
      (upper != lower) ==> { fromString(upper) must_!= fromString(lower) }
    }
  }

  "methods are equal by name" in {
    prop { (m: Method) =>
      Method.fromString(m.name) must beRight(m)
    }
  }

  "safety implies idempotence" in {
    foreach(Method.all.filter(_.isSafe))(_.isIdempotent)
  }
}
