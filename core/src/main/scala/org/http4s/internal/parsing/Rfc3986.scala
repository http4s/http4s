/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package internal.parsing

import cats.parse.Parser.{char, charIn, string}
import cats.parse.Parser
import cats.syntax.all._

/** Common rules defined in Rfc3986
  *
  * @see [[https://tools.ietf.org/html/rfc3986]]
  */
private[http4s] object Rfc3986 {
  def alpha: Parser[Char] = Rfc2234.alpha

  def digit: Parser[Char] = Rfc2234.digit

  /* The spec references RFC2234, which is 0-9A-F, but it also
   * explicitly permits lowercase. */
  val hexdig: Parser[Char] =
    digit.orElse(charIn("ABCDEFabcdef"))

  /* unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~" */
  val unreserved: Parser[Char] =
    alpha.orElse(digit).orElse(charIn("-._~"))

  /* pct-encoded   = "%" HEXDIG HEXDIG */
  val pctEncoded: Parser[Char] =
    (char('%') *> hexdig ~ hexdig).map { case (a, b) =>
      def toInt(c: Char) = c match {
        case c if c >= '0' && c <= '9' => c - '0'
        case c if c >= 'a' && c <= 'f' => c - 'a' + 10
        case c if c >= 'A' && c <= 'F' => c - 'A' + 10
      }
      (toInt(a) << 4 + toInt(b)).toChar
    }

  /* sub-delims    = "!" / "$" / "&" / "'" / "(" / ")"
   *               / "*" / "+" / "," / ";" / "=" */
  val subDelims: Parser[Char] =
    charIn("!$&'()*+,;=")

  /* pchar         = unreserved / pct-encoded / sub-delims / ":" / "@" */
  val pchar: Parser[Char] =
    unreserved.orElse(pctEncoded).orElse(subDelims).orElse(charIn(":@"))

  val ipv4Bytes: Parser[(Byte, Byte, Byte, Byte)] = {
    val decOctet = (char('1') ~ digit ~ digit).backtrack
      .orElse(char('2') ~ charIn('0' to '4') ~ digit)
      .backtrack
      .orElse(string("25") ~ charIn('0' to '5'))
      .backtrack
      .orElse(charIn('1' to '9') ~ digit)
      .backtrack
      .orElse(digit)
      .string
      .map(_.toInt.toByte)
      .backtrack

    val dot = char('.')
    val decOctDot = decOctet <* dot
    (decOctDot, decOctDot, decOctDot, decOctet).tupled
  }
}
