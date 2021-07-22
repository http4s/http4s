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
import com.comcast.ip4s.{Ipv4Address, Ipv6Address}
import java.nio.ByteBuffer

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

  private val ipv4Bytes: Parser[(Byte, Byte, Byte, Byte)] = {
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

  val ipv4Address: Parser[Ipv4Address] =
    ipv4Bytes.map { case (a, b, c, d) => Ipv4Address.fromBytes(a.toInt, b.toInt, c.toInt, d.toInt) }

  val ipv6Address: Parser[Ipv6Address] = {
    import cats.parse.{Parser => P}
    import cats.parse.Parser.{char, string}

    def toIpv6(lefts: collection.Seq[Short], rights: collection.Seq[Short]): Ipv6Address =
      lefts ++ collection.Seq.fill(8 - lefts.size - rights.size)(0.toShort) ++ rights match {
        case collection.Seq(a, b, c, d, e, f, g, h) =>
          val bb = ByteBuffer.allocate(16)
          bb.putShort(a)
          bb.putShort(b)
          bb.putShort(c)
          bb.putShort(d)
          bb.putShort(e)
          bb.putShort(f)
          bb.putShort(g)
          bb.putShort(h)
          Ipv6Address.fromBytes(bb.array).get
      }

    val h16: P[Short] =
      (hexdig ~ hexdig.? ~ hexdig.? ~ hexdig.?).string.map { (s: String) =>
        java.lang.Integer.parseInt(s, 16).toShort
      }

    val colon = char(':')
    val doubleColon = string("::").void
    val h16Colon = h16 <* colon

    val parsedIpv4Bytes = ipv4Bytes.map { case (a: Byte, b: Byte, c: Byte, d: Byte) =>
      List(((a << 8) | b).toShort, ((c << 8) | d).toShort)
    }

    def rightsWithIpv4(n: Int) = (1 to n)
      .map { i =>
        (h16Colon.repExactlyAs[List[Short]](i) ~ parsedIpv4Bytes).backtrack.map { case (l, r) =>
          l ++ r
        }
      }
      .foldLeft(parsedIpv4Bytes.backtrack)(_ | _)

    val ls32: P[List[Short]] = {
      val option1 = ((h16 <* colon.void) ~ h16).map(t => List(t._1, t._2))
      option1.backtrack.orElse(parsedIpv4Bytes)
    }

    val fullIpv6WihtOptionalIpv4 = (h16Colon.repExactlyAs[List[Short]](6) ~ ls32)
      .map { case (ls: List[Short], rs) => toIpv6(ls.toList, rs) }

    val shortIpv6WithIpv4 = for {
      lefts <- h16.repSep0(0, 5, colon).with1 <* doubleColon
      rights <- rightsWithIpv4(4 - lefts.size)
    } yield toIpv6(lefts, rights)

    val shortIpv6 = for {
      lefts <- h16.repSep0(0, 7, colon).with1 <* doubleColon
      rights <-
        if (6 - lefts.size > 0)(h16.repSep0(0, 6 - lefts.size, colon)) else Parser.pure(Nil)
    } yield toIpv6(lefts, rights)

    fullIpv6WihtOptionalIpv4.backtrack.orElse(shortIpv6WithIpv4.backtrack).orElse(shortIpv6)
  }
}
