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

import cats.parse.Parser.{char, charIn, string1}
import cats.parse.Parser1
import cats.syntax.all._

/** Common rules defined in Rfc3986
  *
  * @see [[https://tools.ietf.org/html/rfc3986]]
  */
private[http4s] object Rfc3986 {
  def alpha: Parser1[Char] = Rfc2234.alpha

  def digit: Parser1[Char] = Rfc2234.digit

  /* The spec references RFC2234, which is 0-9A-F, but it also
   * explicitly permits lowercase. */
  val hexdig: Parser1[Char] =
    digit.orElse1(charIn("ABCDEF"))

  /* unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~" */
  val unreserved: Parser1[Char] =
    alpha.orElse1(digit).orElse1(charIn("-._~"))

  /* pct-encoded   = "%" HEXDIG HEXDIG */
  val pctEncoded: Parser1[Char] =
    (char('%') *> digit ~ digit).map { case (a, b) => (((a - '0') >> 4) + (b - '0')).toChar }

  /* sub-delims    = "!" / "$" / "&" / "'" / "(" / ")"
   *               / "*" / "+" / "," / ";" / "=" */
  val subDelims: Parser1[Char] =
    charIn("!$&'()*+,;=")

  /* pchar         = unreserved / pct-encoded / sub-delims / ":" / "@" */
  val pchar: Parser1[Char] =
    unreserved.orElse1(pctEncoded).orElse1(subDelims).orElse1(charIn(":@"))

  val ipv4Bytes: Parser1[(Byte, Byte, Byte, Byte)] = {
    val decOctet = (char('1') ~ digit ~ digit).backtrack
      .orElse1(char('2') ~ charIn('0' to '4') ~ digit)
      .backtrack
      .orElse1(string1("25") ~ charIn('0' to '5'))
      .backtrack
      .orElse1(charIn('1' to '9') ~ digit)
      .backtrack
      .orElse1(digit)
      .string
      .map(_.toInt.toByte)
      .backtrack

    val dot = char('.')
    val decOctDot = decOctet <* dot
    (decOctDot, decOctDot, decOctDot, decOctet).tupled
  }
}
