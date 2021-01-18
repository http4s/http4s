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
package parser

import cats.syntax.all._
import org.http4s.Uri.Scheme.{http, https}

import java.nio.charset.Charset
import org.http4s.{Query => Q}
import org.http4s.internal.parboiled2._
import org.http4s.internal.parboiled2.CharPredicate.{Alpha, Digit, HexDigit}
import org.http4s.internal.parboiled2.support.HNil
import org.typelevel.ci.CIString

private[http4s] trait Rfc3986Parser
    extends Parser
    with Rfc3986Parser.UriSchemeParser
    with Rfc3986Parser.UriInfoParser
    with Rfc3986Parser.Ipv4ParserParser
    with Rfc3986Parser.Ipv6ParserParser
    with IpParser
    with StringBuilding {

  def charset: Charset

  def Uri: Rule1[org.http4s.Uri] = rule((AbsoluteUri | RelativeRef) ~ EOI)

  def AbsoluteUri =
    rule {
      scheme ~ ":" ~ HierPart ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) ~> {
        (scheme, auth, path, query, fragment) =>
          org.http4s
            .Uri(
              Some(scheme),
              auth,
              org.http4s.Uri.Path.fromString(path),
              query.map(Q.fromString).getOrElse(Q.empty),
              fragment)
      }
    }

  def RelativeRef =
    rule {
      RelativePart ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) ~> {
        (auth, path, query, fragment) =>
          org.http4s.Uri(
            None,
            auth,
            org.http4s.Uri.Path.fromString(path),
            query.map(Q.fromString).getOrElse(Q.empty),
            fragment)
      }
    }

  def HierPart: Rule2[Option[org.http4s.Uri.Authority], String] =
    rule {
      "//" ~ Authority ~ PathAbempty ~> { (auth: org.http4s.Uri.Authority, path: String) =>
        Some(auth) :: path :: HNil
      } |
        PathAbsolute ~> (None :: _ :: HNil) |
        PathRootless ~> (None :: _ :: HNil) |
        PathEmpty ~> { (e: String) =>
          None :: e :: HNil
        }
    }

  def RelativePart: Rule2[Option[org.http4s.Uri.Authority], String] =
    rule {
      "//" ~ Authority ~ PathAbempty ~> { (auth: org.http4s.Uri.Authority, path: String) =>
        Some(auth) :: path :: HNil
      } |
        PathAbsolute ~> (None :: _ :: HNil) |
        PathNoscheme ~> (None :: _ :: HNil) |
        PathEmpty ~> { (e: String) =>
          None :: e :: HNil
        }
    }

  def Authority: Rule1[org.http4s.Uri.Authority] =
    rule {
      optional(userInfo ~ "@") ~ Host ~ Port ~> (org.http4s.Uri.Authority.apply _)
    }

  def Host: Rule1[org.http4s.Uri.Host] =
    rule {
      // format: off
    ipv4Address |
    "[" ~ ipv6Address ~ "]" |
    capture(RegName) ~> { (s: String) =>
      org.http4s.Uri.RegName(CIString(decode(s)))
    }
    // format:on
  }

  def Port = rule {
    ":" ~ (capture(oneOrMore(Digit)) ~> { (s: String) =>
      val int: Option[Int] = Either.catchOnly[NumberFormatException](s.toInt).toOption

      test(int.nonEmpty) ~ push(int)
    } | push(None)) | push(None)
  }

  def IpLiteral = rule {"[" ~ capture(IpV6Address | IpVFuture) ~ "]"}

  def IpVFuture = rule {"v" ~ oneOrMore(HexDigit) ~ "." ~ oneOrMore(Unreserved | SubDelims | ":")}

  def RegName: Rule0 = rule {zeroOrMore(Unreserved | PctEncoded | SubDelims)}

  def Path: Rule1[org.http4s.Uri.Path] = rule {
    (PathAbempty | PathAbsolute | PathNoscheme | PathRootless | PathEmpty) ~> { (s: String) =>
      org.http4s.Uri.Path.fromString(decode(s))
    }
  }

  def PathAbempty: Rule1[String] = rule {capture(zeroOrMore("/" ~ Segment))}

  def PathAbsolute: Rule1[String] = rule {capture(oneOrMore("/" ~ Segment))}

  def PathNoscheme: Rule1[String] = rule {capture(SegmentNzNc ~ zeroOrMore("/" ~ Segment))}

  def PathRootless: Rule1[String] = rule {capture(SegmentNz ~ zeroOrMore("/" ~ Segment))}

  def PathEmpty: Rule1[String] = rule {push("")}

  def Segment = rule {zeroOrMore(Pchar)}

  def SegmentNz = rule {oneOrMore(Pchar)}

  def SegmentNzNc = rule {oneOrMore(Unreserved | PctEncoded | SubDelims | "@")}

  def Pchar = rule {Unreserved | PctEncoded | SubDelims | ":" | "@"}

  // NOTE: The Query is NOT url decoded.
  def Query = rule {
    clearSB() ~ zeroOrMore(
      capture(Pchar) ~> { (s: String) =>
        appendSB(s)
      } |
        "/" ~ appendSB() |
        "?" ~ appendSB() |
        // These are illegal, but common in the wild.  We will be "conservative
        // in our sending behavior and liberal in our receiving behavior", and
        // encode them.
        "[" ~ appendSB("%5B") |
        "]" ~ appendSB("%5D")
    ) ~ push(sb.toString)
  }

  // NOTE: The Fragment is NOT url decoded.
  def Fragment = rule {capture(zeroOrMore(Pchar | "/" | "?"))}

  def PctEncoded = rule {"%" ~ 2.times(HexDigit)}

  def Reserved = rule {GenDelims | SubDelims}

  def Unreserved = rule {Alpha | Digit | "-" | "." | "_" | "~"}

  def GenDelims = rule {":" | "/" | "?" | "#" | "[" | "]" | "@"}

  def SubDelims = rule {"!" | "$" | "&" | "'" | "(" | ")" | "*" | "+" | "," | ";" | "="}

  protected def decode(s: String) = org.http4s.Uri.decode(s, charset)
}

object Rfc3986Parser {
  private[http4s] trait UriSchemeParser { self: Parser =>
    def scheme =
      rule {
        "https" ~ Alpha.unary_!() ~ push(https) |
          "http" ~ Alpha.unary_!() ~ push(http) |
          capture(Alpha ~ zeroOrMore(Alpha | Digit | "+" | "-" | ".")) ~> (new Uri.Scheme(_))
      }
  }

  private[http4s] trait UriInfoParser { self: Rfc3986Parser =>
    def userInfo: Rule1[org.http4s.Uri.UserInfo] =
      rule {
        capture(zeroOrMore(Unreserved | PctEncoded | SubDelims)) ~
          (":" ~ capture(zeroOrMore(Unreserved | PctEncoded | SubDelims | ":"))).? ~>
          ((username: String, password: Option[String]) =>
            new org.http4s.Uri.UserInfo(decode(username), password.map(decode)))
      }
  }

  private[http4s] trait Ipv4ParserParser { self: Parser with IpParser =>
    def ipv4Address: Rule1[org.http4s.Uri.Ipv4Address] =
      rule {
        // format: off
        decOctet ~ "." ~ decOctet ~ "." ~ decOctet ~ "." ~ decOctet ~>
          { (a: Byte, b: Byte, c: Byte, d: Byte) => new org.http4s.Uri.Ipv4Address(a, b, c, d) }
        // format:on
      }

    private def decOctet = rule {capture(DecOctet) ~> (_.toInt.toByte)}
  }

  private[http4s] trait Ipv6ParserParser { self: Parser with IpParser =>
    // format: off
    def ipv6Address: Rule1[org.http4s.Uri.Ipv6Address] = rule {
      6.times(h16 ~ ":") ~ ls32 ~>
        { (ls: collection.Seq[Short], r0: Short, r1: Short) => toIpv6(ls, Seq(r0, r1)) } |
        "::" ~ 5.times(h16 ~ ":") ~ ls32 ~>
          { (ls: collection.Seq[Short], r0: Short, r1: Short) => toIpv6(ls, Seq(r0, r1)) } |
        optional(h16) ~ "::" ~ 4.times(h16 ~ ":") ~ ls32 ~>
          { (l: Option[Short], rs: collection.Seq[Short], r0: Short, r1: Short) => toIpv6(l.toSeq, rs :+ r0 :+ r1) } |
        optional((1 to 2).times(h16).separatedBy(":")) ~ "::" ~ 3.times(h16 ~ ":") ~ ls32 ~>
          { (ls: Option[collection.Seq[Short]], rs: collection.Seq[Short], r0: Short, r1: Short) => toIpv6(ls.getOrElse(Seq.empty), rs :+ r0 :+ r1) } |
        optional((1 to 3).times(h16).separatedBy(":")) ~ "::" ~ 2.times(h16 ~ ":") ~ ls32 ~>
          { (ls: Option[collection.Seq[Short]], rs: collection.Seq[Short], r0: Short, r1: Short) => toIpv6(ls.getOrElse(Seq.empty), rs :+ r0 :+ r1) } |
        optional((1 to 4).times(h16).separatedBy(":")) ~ "::" ~ h16 ~ ":" ~ ls32 ~>
          { (ls: Option[collection.Seq[Short]], r0: Short, r1: Short, r2: Short) => toIpv6(ls.getOrElse(Seq.empty), Seq(r0, r1, r2)) } |
        optional((1 to 5).times(h16).separatedBy(":")) ~ "::" ~ ls32 ~>
          { (ls: Option[collection.Seq[Short]], r0: Short, r1: Short) => toIpv6(ls.getOrElse(Seq.empty), Seq(r0, r1)) } |
        optional((1 to 6).times(h16).separatedBy(":")) ~ "::" ~ h16 ~>
          { (ls: Option[collection.Seq[Short]], r0: Short) => toIpv6(ls.getOrElse(Seq.empty), Seq(r0)) } |
        optional((1 to 7).times(h16).separatedBy(":")) ~ "::" ~>
          { (ls: Option[collection.Seq[Short]]) => toIpv6(ls.getOrElse(Seq.empty), Seq.empty) }
    }
    // format:on


    def ls32: Rule2[Short, Short] = rule {
      (h16 ~ ":" ~ h16) |
        (decOctet ~ "." ~ decOctet ~ "." ~ decOctet ~ "." ~ decOctet) ~> { (a: Byte, b: Byte, c: Byte, d: Byte) =>
          push(((a << 8) | b).toShort) ~ push(((c << 8) | d).toShort)
        }
    }

    def h16: Rule1[Short] = rule {
      capture((1 to 4).times(HexDigit)) ~> { (s: String) => java.lang.Integer.parseInt(s, 16).toShort }
    }
    // format:on

    private def toIpv6(lefts: collection.Seq[Short], rights: collection.Seq[Short]): org.http4s.Uri.Ipv6Address =
      (lefts ++ collection.Seq.fill(8 - lefts.size - rights.size)(0.toShort) ++ rights) match {
        case collection.Seq(a, b, c, d, e, f, g, h) =>
          org.http4s.Uri.Ipv6Address(a, b, c, d, e, f, g, h)
      }

    private def decOctet = rule {capture(DecOctet) ~> (_.toInt.toByte)}
  }

}