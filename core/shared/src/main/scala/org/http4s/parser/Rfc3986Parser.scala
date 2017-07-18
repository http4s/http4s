package org.http4s
package parser

import cats.implicits._
import java.net.URLDecoder
import java.nio.charset.Charset
import org.http4s.{Query => Q}
import org.http4s.internal.parboiled2._
import org.http4s.internal.parboiled2.CharPredicate.{Alpha, Digit, HexDigit}
import org.http4s.internal.parboiled2.support.HNil
import org.http4s.syntax.string._

private[parser] trait Rfc3986Parser
    extends Parser
    with Uri.Scheme.Parser
    with IpParser
    with StringBuilding {
  // scalastyle:off public.methods.have.type

  def charset: Charset

  def Uri: Rule1[org.http4s.Uri] = rule { (AbsoluteUri | RelativeRef) ~ EOI }

  def AbsoluteUri = rule {
    scheme ~ ":" ~ HierPart ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) ~> {
      (scheme, auth, path, query, fragment) =>
        org.http4s
          .Uri(Some(scheme), auth, path, query.map(Q.fromString).getOrElse(Q.empty), fragment)
    }
  }

  def RelativeRef = rule {
    RelativePart ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) ~> {
      (auth, path, query, fragment) =>
        org.http4s.Uri(None, auth, path, query.map(Q.fromString).getOrElse(Q.empty), fragment)
    }
  }

  def HierPart: Rule2[Option[org.http4s.Uri.Authority], org.http4s.Uri.Path] = rule {
    "//" ~ Authority ~ PathAbempty ~> {
      (auth: org.http4s.Uri.Authority, path: org.http4s.Uri.Path) =>
        auth.some :: path :: HNil
    } |
      PathAbsolute ~> (None :: _ :: HNil) |
      PathRootless ~> (None :: _ :: HNil) |
      PathEmpty ~> { (e: String) =>
        None :: e :: HNil
      }
  }

  def RelativePart: Rule2[Option[org.http4s.Uri.Authority], org.http4s.Uri.Path] = rule {
    "//" ~ Authority ~ PathAbempty ~> {
      (auth: org.http4s.Uri.Authority, path: org.http4s.Uri.Path) =>
        auth.some :: path :: HNil
    } |
      PathAbsolute ~> (None :: _ :: HNil) |
      PathNoscheme ~> (None :: _ :: HNil) |
      PathEmpty ~> { (e: String) =>
        None :: e :: HNil
      }
  }

  def Authority: Rule1[org.http4s.Uri.Authority] = rule {
    optional(UserInfo ~ "@") ~ Host ~ Port ~> (org.http4s.Uri.Authority.apply _)
  }

  def UserInfo = rule {
    capture(zeroOrMore(Unreserved | PctEncoded | SubDelims | ":")) ~> (decode _)
  }

  def Host: Rule1[org.http4s.Uri.Host] = rule {
    capture(IpV4Address) ~> { s: String =>
      org.http4s.Uri.IPv4(s.ci)
    } |
      (IpLiteral | capture(IpV6Address)) ~> { s: String =>
        org.http4s.Uri.IPv6(s.ci)
      } |
      capture(RegName) ~> { s: String =>
        org.http4s.Uri.RegName(decode(s).ci)
      }
  }

  def Port = rule {
    ":" ~ (capture(oneOrMore(Digit)) ~> { s: String =>
      (Some(s.toInt))
    } | push(None)) | push(None)
  }

  def IpLiteral = rule { "[" ~ capture(IpV6Address | IpVFuture) ~ "]" }

  def IpVFuture = rule { "v" ~ oneOrMore(HexDigit) ~ "." ~ oneOrMore(Unreserved | SubDelims | ":") }

  def RegName: Rule0 = rule { zeroOrMore(Unreserved | PctEncoded | SubDelims) }

  def Path: Rule1[String] = rule {
    (PathAbempty | PathAbsolute | PathNoscheme | PathRootless | PathEmpty) ~> { s: String =>
      decode(s)
    }
  }

  def PathAbempty: Rule1[String] = rule { capture(zeroOrMore("/" ~ Segment)) }

  def PathAbsolute: Rule1[String] = rule { capture(oneOrMore("/" ~ Segment)) }

  def PathNoscheme: Rule1[String] = rule { capture(SegmentNzNc ~ zeroOrMore("/" ~ Segment)) }

  def PathRootless: Rule1[String] = rule { capture(SegmentNz ~ zeroOrMore("/" ~ Segment)) }

  def PathEmpty: Rule1[String] = rule { push("") }

  def Segment = rule { zeroOrMore(Pchar) }

  def SegmentNz = rule { oneOrMore(Pchar) }

  def SegmentNzNc = rule { oneOrMore(Unreserved | PctEncoded | SubDelims | "@") }

  def Pchar = rule { Unreserved | PctEncoded | SubDelims | ":" | "@" }

  // NOTE: The Query is NOT url decoded.
  def Query = rule {
    clearSB() ~ zeroOrMore(
      capture(Pchar) ~> { s: String =>
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
  def Fragment = rule { capture(zeroOrMore(Pchar | "/" | "?")) }

  def PctEncoded = rule { "%" ~ 2.times(HexDigit) }

  def Reserved = rule { GenDelims | SubDelims }

  def Unreserved = rule { Alpha | Digit | "-" | "." | "_" | "~" }

  def GenDelims = rule { ":" | "/" | "?" | "#" | "[" | "]" | "@" }

  def SubDelims = rule { "!" | "$" | "&" | "'" | "(" | ")" | "*" | "+" | "," | ";" | "=" }

  private[this] def decode(s: String) = URLDecoder.decode(s, charset.name)
  // scalastyle:on public.methods.have.type
}
