package org.http4s
package parser

import org.http4s.internal.parboiled2._

trait Rfc3986Rules extends StringBuilding { self: Parser =>
  import Rfc3986Predicates._

  def pchar = rule {
    pcharNoPct ~ appendSB() | `pct-encoded`
  }

  def `pct-encoded` = rule {
    "%" ~ HEXDIG ~ HEXDIG ~
      run(sb.append("%").append(charAt(-2)).append(lastChar))
  }
}

object Rfc3986Predicates {
  val ALPHA = CharPredicate.Alpha
  val CR = '\r'
  val DIGIT = CharPredicate.Digit
  val DQUOTE = '"'
  val HEXDIG = CharPredicate.HexDigit
  val LF = '\n'
  val SP = ' '

  val `gen-delims` = CharPredicate(":/?#[]@")
  val `sub-delims` = CharPredicate("!$&'()*+,;=")
  val reserved = `gen-delims` ++ `sub-delims`
  val unreserved = ALPHA ++ DIGIT ++ "-._~"

  // These aren't in the RFC, but help parse efficiently
  val pcharNoPct = unreserved ++ `sub-delims` ++ ":@"
  val fragmentCharNoPct = pcharNoPct ++ "/?"
  val schemeChar = ALPHA ++ DIGIT ++ "+-."
  val userInfoChar = unreserved ++ `sub-delims` ++ ":"
}
