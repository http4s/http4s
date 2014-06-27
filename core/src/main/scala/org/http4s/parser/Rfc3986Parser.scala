package org.http4s
package parser

import org.parboiled2._
import java.nio.charset.Charset
import java.net.URLDecoder
import shapeless.HNil
import scalaz.syntax.std.option._
import org.http4s.util.CaseInsensitiveString._

private[parser] trait Rfc3986Parser { this: Parser =>
  import CharPredicate.{Alpha, Digit, HexDigit}

  def charset: Charset

  def Uri = rule { Scheme ~ ":" ~ HierPart ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) }

  def HierPart: Rule2[Option[org.http4s.Uri.Authority], org.http4s.Uri.Path] = rule {
    "//" ~ Authority ~ PathAbempty ~> {(auth: org.http4s.Uri.Authority, path: org.http4s.Uri.Path) => auth.some :: path :: HNil} |
      PathAbsolute ~> (None :: _ :: HNil) |
      PathRootless ~> (None :: _ :: HNil) |
      PathEmpty ~> {(e: String) => None :: e :: HNil}
  }

  def UriReference = rule { Uri | RelativeRef }

  def AbsoluteUri = rule {
    Scheme ~ ":" ~ HierPart ~ optional("?" ~ Query) ~>
      ((scheme, auth, path, query) => org.http4s.Uri(scheme = Some(scheme), authority = auth, path = path, query = query))
  }

  def RelativeRef = rule { RelativePart ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) }

  def RelativePart: Rule2[Option[org.http4s.Uri.Authority], org.http4s.Uri.Path] = rule {
    "//" ~ Authority ~ PathAbempty ~> {(auth: org.http4s.Uri.Authority, path: org.http4s.Uri.Path) => auth.some :: path :: HNil} |
      PathAbsolute ~> (None :: _ :: HNil) |
      PathNoscheme ~> (None :: _ :: HNil) |
      PathEmpty ~> {(e: String) => None :: e :: HNil}
  }

  def Scheme = rule {
    capture(Alpha ~ zeroOrMore(Alpha | Digit | "+" | "-" | ".")) ~> (_.ci)
  }

  def Authority: Rule1[org.http4s.Uri.Authority] = rule { optional(UserInfo ~ "@") ~ IPv4 ~ IPv6 ~ RName ~ Port ~> (org.http4s.Uri.Authority.from _) }

  def UserInfo = rule { capture(zeroOrMore(Unreserved | PctEncoded | SubDelims | ":")) ~> (decode _) }

  def IPv4 = rule { capture(IpV4Address) ~> {s: String => if(s.nonEmpty) (Some(org.http4s.Uri.IPv4(s.ci))) else None} | push(None) }

  def IPv6 = rule { (IpLiteral | capture(IpV6Address)) ~> {s: String => if(s.nonEmpty) (Some(org.http4s.Uri.IPv6(s.ci))) else None} | push(None) }

  def RName = rule { capture(RegName) ~> {s: String => if(s.nonEmpty) (Some(org.http4s.Uri.RegName(decode(s).ci))) else None} | push(None) }

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

  def PathAbempty: Rule1[String] = rule { capture(oneOrMore("/" ~ Segment)) | push("/") }

  def PathAbsolute: Rule1[String] = rule { capture("/" ~ optional(SegmentNz ~ zeroOrMore("/" ~ Segment))) }

  def PathNoscheme: Rule1[String] = rule { capture(SegmentNzNc ~ zeroOrMore("/" ~ Segment)) }

  def PathRootless: Rule1[String] = rule { capture(SegmentNz ~ zeroOrMore("/" ~ Segment)) }

  def PathEmpty: Rule1[String] = rule { push("/") }

  def Segment = rule { zeroOrMore(Pchar) }

  def SegmentNz = rule { oneOrMore(Pchar) }

  def SegmentNzNc = rule { oneOrMore(Unreserved | PctEncoded | SubDelims | "@") }

  def Pchar = rule { Unreserved | PctEncoded | SubDelims | ":" | "@" }

  def Query = rule { capture(oneOrMore(Pchar | "/" | "?")) ~> (decode _) }

  def Fragment = rule { capture(zeroOrMore(Pchar | "/" | "?")) ~> (decode _) }

  def PctEncoded = rule { "%" ~ 2.times(HexDigit) }

  def Unreserved = rule { Alpha | Digit | "-" | "." | "_" | "~" }

  def Reserved = rule { GenDelims | SubDelims }

  def GenDelims = rule { ":" | "/" | "?" | "#" | "[" | "]" | "@" }

  def SubDelims = rule { "!" | "$" | "&" | "'" | "(" | ")" | "*" | "+" | "," | ";" | "=" }

  private[this] def decode(s: String) = URLDecoder.decode(s, charset.name)
}
