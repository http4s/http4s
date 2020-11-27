/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import java.util.Locale

import cats.Hash
import cats.implicits._
import cats.kernel.laws.discipline._
import org.scalacheck.Gen
import org.scalacheck.Prop.{forAll, forAllNoShrink}

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
    forAll(genNonToken) { nonToken =>
      fromString(nonToken) must beLeft
    }
  }

  "parse a whole input string" in { // see #3680
    val genAlmostToken = {
      val genNonToken =
        Gen
          .oneOf(
            Gen.choose('\u0000', '\u001F'),
            Gen.oneOf("()<>@,;:\\\"/[]?={} \t\u007F")
          )
          .map(_.toString)

      for {
        tokenHead <- genToken
        nonToken <- genNonToken
        tokenTail <- Gen.option(genToken).map(_.orEmpty)
      } yield tokenHead + nonToken + tokenTail
    }
    forAllNoShrink(genAlmostToken) { almostToken =>
      fromString(almostToken) must beLeft((_: ParseFailure).sanitized ==== "Invalid method")
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
