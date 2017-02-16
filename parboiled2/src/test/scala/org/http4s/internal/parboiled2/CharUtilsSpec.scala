/*
 * Copyright (C) 2009-2013 Mathias Doenitz, Alexander Myltsev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.internal.parboiled2

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import org.scalacheck.{Gen, Prop}

class CharUtilsSpec extends Specification with ScalaCheck {

  val hexChars = for (i ← Gen.choose(0, 15)) yield i -> Integer.toHexString(i).charAt(0)

  "CharUtils" >> {
    "hexValue" in {
      val p = Prop.forAll(hexChars) { case (i, c) ⇒ CharUtils.hexValue(c) == i }
      check(p, defaultParameters, defaultFreqMapPretty)
    }
    "numberOfHexDigits" in prop {
      l: Long ⇒ CharUtils.numberOfHexDigits(l) === java.lang.Long.toHexString(l).length
    }
    "upperHexString" in prop {
      l: Long ⇒ CharUtils.upperHexString(l) === java.lang.Long.toHexString(l).toUpperCase
    }
    "lowerHexString" in prop {
      l: Long ⇒ CharUtils.lowerHexString(l) === java.lang.Long.toHexString(l)
    }
    "numberOfDecimalDigits" in prop {
      l: Long ⇒ CharUtils.numberOfDecimalDigits(l) === java.lang.Long.toString(l).length
    }
    "signedDecimalString" in prop {
      l: Long ⇒ CharUtils.signedDecimalString(l) === java.lang.Long.toString(l)
    }
  }
}
