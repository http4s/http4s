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

class CharPredicateSpec extends Specification {

  "CharPredicates" should {

    "correctly mask characters" in {
      inspectMask(CharPredicate("4")) === "0010000000000000|0000000000000000"
      inspectMask(CharPredicate("a")) === "0000000000000000|0000000200000000"
      CharPredicate("&048z{~").toString === "CharPredicate.MaskBased(&048z{~)"
      show(CharPredicate("&048z{~")) === "&048z{~"
    }

    "support `testAny`" in {
      CharPredicate("abc").matchesAny("0125!") must beFalse
      CharPredicate("abc").matchesAny("012c5!") must beTrue
    }

    "support `indexOfFirstMatch`" in {
      CharPredicate("abc").indexOfFirstMatch("0125!") === -1
      CharPredicate("abc").indexOfFirstMatch("012c5!") === 3
    }

    "correctly support non-masked content" in {
      val colonSlashEOI = CharPredicate(':', '/', EOI)
      colonSlashEOI(':') must beTrue
      colonSlashEOI('/') must beTrue
      colonSlashEOI(EOI) must beTrue
      colonSlashEOI('x') must beFalse
    }

    "be backed by a mask where possible" in {
      CharPredicate('1' to '9').toString === "CharPredicate.MaskBased(123456789)"
      (CharPredicate('1' to '3') ++ CharPredicate('5' to '8')).toString === "CharPredicate.MaskBased(1235678)"
      (CharPredicate('1' to '3') ++ "5678").toString === "CharPredicate.MaskBased(1235678)"
      (CharPredicate('1' to '6') -- CharPredicate('2' to '4')).toString === "CharPredicate.MaskBased(156)"
      (CharPredicate('1' to '6') -- "234").toString === "CharPredicate.MaskBased(156)"
    }
    "be backed by an array where possible" in {
      CharPredicate("abcäüö").toString === "CharPredicate.ArrayBased(abcäöü)"
      (CharPredicate("abcäüö") -- "äö").toString === "CharPredicate.ArrayBased(abcü)"
    }
    "be backed by a range where possible" in {
      CharPredicate('1' to 'Ä').toString === "CharPredicate.RangeBased(start = 1, end = Ä, step = 1, inclusive = true)"
    }
  }

  def show(pred: CharPredicate): String = {
    val chars: Array[Char] = ('\u0000' to '\u0080').flatMap(c ⇒ if (pred(c)) Some(c) else None)(collection.breakOut)
    new String(chars)
  }

  def inspectMask(pred: CharPredicate) = {
    val CharPredicate.MaskBased(lowMask, highMask) = pred
    "%016x|%016x" format (lowMask, highMask)
  }
}
