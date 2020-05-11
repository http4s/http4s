/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.implicits._
import cats.kernel.laws.discipline.OrderTests
import org.http4s.Uri.Scheme
import org.http4s.internal.parboiled2.CharPredicate
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.util.Renderer

class SchemeSpec extends Http4sSpec {
  "equals" should {
    "be consistent with equalsIgnoreCase of the values" in {
      prop { (a: Scheme, b: Scheme) =>
        (a == b) must_== a.value.equalsIgnoreCase(b.value)
      }
    }
  }

  "compare" should {
    "be consistent with value.compareToIgnoreCase" in {
      prop { (a: Scheme, b: Scheme) =>
        a.value.compareToIgnoreCase(b.value) must_== a.compare(b)
      }
    }
  }

  "hashCode" should {
    "be consistent with equality" in {
      prop { (a: Scheme, b: Scheme) =>
        (a == b) ==> (a.## must_== b.##)
      }
    }
  }

  "render" should {
    "return value" in prop { (s: Scheme) =>
      Renderer.renderString(s) must_== s.value
    }
  }

  "fromString" should {
    "reject all invalid schemes" in { (s: String) =>
      (s.isEmpty ||
      !CharPredicate.Alpha(s.charAt(0)) ||
      !s.forall(CharPredicate.Alpha ++ CharPredicate(".-+"))) ==>
        (Scheme.fromString(s) must beLeft)
    }

    "accept valid literals prefixed by cached version" in {
      Scheme.fromString("httpx") must beRight
    }
  }

  "literal syntax" should {
    "accept valid literals" in {
      scheme"https" must_== Scheme.https
    }

    "reject invalid literals" in {
      illTyped("""scheme"нет"""")
      true
    }
  }

  checkAll("Order[Scheme]", OrderTests[Scheme].order)
  checkAll("HttpCodec[Scheme]", HttpCodecTests[Scheme].httpCodec)
}
