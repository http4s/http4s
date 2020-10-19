/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.headers

import org.scalacheck.Gen

trait ForwardedAuxiliaryGenerators {
  import Forwarded.{PortMax, PortMin}

  val obfuscatedCharGen: Gen[Char] = Gen.oneOf(Gen.alphaNumChar, Gen.oneOf('.', '_', '-'))

  val obfuscatedStringGen: Gen[String] =
    Gen.nonEmptyListOf(obfuscatedCharGen).map('_' :: _).map(_.mkString)

  val portNumGen: Gen[Int] = Gen.chooseNum(PortMin, PortMax)
}
