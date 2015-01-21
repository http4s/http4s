package org.http4s
package parser

import org.parboiled2._
import java.nio.charset.Charset
import java.net.URLDecoder
import shapeless.HNil
import scalaz.syntax.std.option._
import org.http4s.util.CaseInsensitiveString._
import org.http4s.{ Query => Q }

private[parser] trait Rfc3986Parser { this: Parser =>
  import CharPredicate.{Alpha, Digit, HexDigit}

  def charset: Charset

  def Uri: Rule1[org.http4s.Uri] = rule { AbsoluteUri | RelativeRef }

  def AbsoluteUri = rule {
    Scheme ~ ":" ~ HierPart ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) ~> { (scheme, auth, path, query, fragment) =>
      org.http4s.Uri(Some(scheme), auth, path, query.map(Q.fromString).getOrElse(Q.empty), fragment)
    }
  }

  def RelativeRef = rule { RelativePart ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) ~> { (auth, path, query, fragment) =>
    org.http4s.Uri(None, auth, path, query.map(Q.fromString).getOrElse(Q.empty), fragment)
    }
  }

  def HierPart: Rule2[Option[org.http4s.Uri.Authority], org.http4s.Uri.Path] = rule {
    "//" ~ Authority ~ PathAbempty ~> {(auth: org.http4s.Uri.Authority, path: org.http4s.Uri.Path) => auth.some :: path :: HNil} |
      PathAbsolute ~> (None :: _ :: HNil) |
      PathRootless ~> (None :: _ :: HNil) |
      PathEmpty ~> {(e: String) => None :: e :: HNil}
  }

  def RelativePart: Rule2[Option[org.http4s.Uri.Authority], org.http4s.Uri.Path] = rule {
    "//" ~ Authority ~ PathAbempty ~> {(auth: org.http4s.Uri.Authority, path: org.http4s.Uri.Path) => auth.some :: path :: HNil} |
      PathAbsolute ~> (None :: _ :: HNil) |
      PathNoscheme ~> (None :: _ :: HNil) |
      PathEmpty ~> {(e: String) => None :: e :: HNil}
  }

  def Scheme = rule {
    capture(Alpha ~ zeroOrMore(Alpha | Digit | "+" | "-" | ".")) ~> (_.ci)
  }

  def Authority: Rule1[org.http4s.Uri.Authority] = rule { optional(UserInfo ~ "@") ~ Host ~ Port ~> (org.http4s.Uri.Authority.apply _) }

  def UserInfo = rule { capture(zeroOrMore(Unreserved | PctEncoded | SubDelims | ":")) ~> (decode _) }

  def Host: Rule1[org.http4s.Uri.Host] = rule {
    capture(IpV4Address) ~> { s: String => org.http4s.Uri.IPv4(s.ci) } |
      (IpLiteral | capture(IpV6Address)) ~> { s: String => org.http4s.Uri.IPv6(s.ci) } |
      capture(RegName) ~> { s: String => org.http4s.Uri.RegName(decode(s).ci) }
  }

  def Port = rule { ":" ~ (capture(oneOrMore(Digit)) ~> {s: String => (Some(s.toInt))} |  push(None)) |  push(None) }

  def IpLiteral = rule { "[" ~ capture(IpV6Address | IpVFuture) ~ "]" }

  def IpVFuture = rule { "v" ~ oneOrMore(HexDigit) ~ "." ~ oneOrMore(Unreserved | SubDelims | ":" ) }

  def IpV6Address: Rule0 = rule {
                                                   6.times(H16 ~ ":") ~ LS32 |
                                            "::" ~ 5.times(H16 ~ ":") ~ LS32 |
          optional(H16) ~                   "::" ~ 4.times(H16 ~ ":") ~ LS32 |
    (0 to 2).times(H16).separatedBy(":")  ~ "::" ~ 3.times(H16 ~ ":") ~ LS32 |
    (0 to 3).times(H16).separatedBy(":")  ~ "::" ~ 2.times(H16 ~ ":") ~ LS32 |
    (0 to 4).times(H16).separatedBy(":")  ~ "::" ~         H16 ~ ":"  ~ LS32 |
    (0 to 5).times(H16).separatedBy(":")  ~ "::" ~                      LS32 |
    (0 to 6).times(H16).separatedBy(":")  ~ "::" ~                      H16  |
    (0 to 7).times(H16).separatedBy(":")  ~ "::"
  }

  def H16 = rule { (1 to 4).times(HexDigit) }

  def LS32 = rule { (H16 ~ ":" ~ H16) | IpV4Address }

  def IpV4Address = rule { 3.times(DecOctet ~ ".") ~ DecOctet }

  def DecOctet = rule {
      "1" ~ 2.times(Digit)      |
      ("1" - "9") ~ Digit       |
      "2" ~ ("0" - "4") ~ Digit |
      "25" ~ ("0" - "5")        |
      Digit
  }

  def RegName: Rule0 = rule { zeroOrMore(Unreserved | PctEncoded | SubDelims) }

  def Path: Rule1[String] = rule { (PathAbempty | PathAbsolute | PathNoscheme | PathRootless | PathEmpty) ~> { s: String => decode(s)} }

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
  def Query = rule { capture(zeroOrMore(Pchar | "/" | "?")) }

  // NOTE: The Fragment is NOT url decoded.
  def Fragment = rule { capture(zeroOrMore(Pchar | "/" | "?")) }

  def PctEncoded = rule { "%" ~ 2.times(HexDigit) }

  def Unreserved = rule { Alpha | Digit | "-" | "." | "_" | "~" }

  def Reserved = rule { GenDelims | SubDelims }

  def GenDelims = rule { ":" | "/" | "?" | "#" | "[" | "]" | "@" }

  def SubDelims = rule { "!" | "$" | "&" | "'" | "(" | ")" | "*" | "+" | "," | ";" | "=" }

  private[this] def decode(s: String) = URLDecoder.decode(s, charset.name)
}
