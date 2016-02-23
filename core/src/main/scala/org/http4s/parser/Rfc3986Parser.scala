package org.http4s
package parser

import org.http4s.util.encoding.UriCodingUtils
import org.parboiled2._
import java.nio.charset.Charset
import java.net.URLDecoder
import shapeless.HNil
import scalaz.syntax.std.option._
import org.http4s.util.CaseInsensitiveString._
import org.http4s.{ Query => Q }

private[http4s] trait Rfc3986Parser { this: Parser =>
  import CharPredicate.{Alpha, Digit, HexDigit}

  def Uri: Rule1[org.http4s.Uri] = rule { AbsoluteUri | RelativeRef }

  def AbsoluteUri = rule {
    Scheme ~ ":" ~ HierPart ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) ~> { (scheme, auth, path, query, fragment) =>
      org.http4s.Uri(Some(scheme), auth, path, query.map(Q.fromString).getOrElse(Q.noQuery), fragment)
    }
  }

  def RelativeRef = rule {
    RelativePart ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) ~> { (auth, path, query, fragment) =>
    org.http4s.Uri(None, auth, path, query.map(Q.fromString).getOrElse(Q.noQuery), fragment)
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

  def UserInfo = rule { capture(zeroOrMore(PctEncoded | Rfc3986CharPredicate.UserInfo)) ~> (decode _) }

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
    optional((1 to 2).times(H16).separatedBy(":"))  ~ "::" ~ 3.times(H16 ~ ":") ~ LS32 |
    optional((1 to 3).times(H16).separatedBy(":"))  ~ "::" ~ 2.times(H16 ~ ":") ~ LS32 |
    optional((1 to 4).times(H16).separatedBy(":"))  ~ "::" ~         H16 ~ ":"  ~ LS32 |
    optional((1 to 5).times(H16).separatedBy(":"))  ~ "::" ~                      LS32 |
    optional((1 to 6).times(H16).separatedBy(":"))  ~ "::" ~                      H16  |
    optional((1 to 7).times(H16).separatedBy(":"))  ~ "::"
  }

  def H16 = rule { (1 to 4).times(HexDigit) }

  def LS32 = rule { (H16 ~ ":" ~ H16) | IpV4Address }

  def IpV4Address = rule { 3.times(DecOctet ~ ".") ~ DecOctet }


  def DecOctet = rule {
    "1"         ~ Digit       ~ Digit |
    "2"         ~ ("0" - "4") ~ Digit |
    "25"        ~ ("0" - "5")         |
    ("1" - "9") ~ Digit               |
    Digit
  }

  def RegName: Rule0 = rule { zeroOrMore(PctEncoded | Rfc3986CharPredicate.RegName) }

  def Path: Rule1[String] = rule { (PathAbempty | PathAbsolute | PathNoscheme | PathRootless | PathEmpty) ~> { s: String => decode(s)} }

  def PathAbempty: Rule1[String] = rule { capture(zeroOrMore("/" ~ Segment)) }

  def PathAbsolute: Rule1[String] = rule { capture(oneOrMore("/" ~ Segment)) }

  def PathNoscheme: Rule1[String] = rule { capture(SegmentNzNc ~ zeroOrMore("/" ~ Segment)) }

  def PathRootless: Rule1[String] = rule { capture(SegmentNz ~ zeroOrMore("/" ~ Segment)) }

  def PathEmpty: Rule1[String] = rule { push("") }

  def Segment = rule { zeroOrMore(Pchar) }

  def SegmentNz = rule { oneOrMore(Pchar) }

  def SegmentNzNc = rule { oneOrMore(PctEncoded | Rfc3986CharPredicate.SegmentNzNc) }

  def Pchar = rule { PctEncoded | Rfc3986CharPredicate.Pchar }

  // NOTE: The Query is NOT url decoded.
  def Query = rule { capture(zeroOrMore(PctEncoded | Rfc3986CharPredicate.Query)) }

  // NOTE: The Fragment is NOT url decoded.
  def Fragment = rule { capture(zeroOrMore(PctEncoded | Rfc3986CharPredicate.Fragment)) }

  def PctEncoded = rule { "%" ~ 2.times(HexDigit) }

  def Unreserved = rule { Rfc3986CharPredicate.Unreserved }

  def Reserved = rule { Rfc3986CharPredicate.Reserved }

  def GenDelims = rule { Rfc3986CharPredicate.GenDelims }

  def SubDelims = rule { Rfc3986CharPredicate.SubDelims }

  private[this] def decode(s: String) = UriCodingUtils.unsafePercentDecode(s)
}

object Rfc3986CharPredicate {
  import CharPredicate.{Alpha, Digit}
  def UserInfo    = CharPredicate(Unreserved, SubDelims, ':')
  def RegName     = CharPredicate(Unreserved, SubDelims)
  def Pchar       = CharPredicate(Unreserved, SubDelims, '@', ':')
  def SegmentNzNc = CharPredicate(Unreserved, SubDelims, '@')
  def Query       = CharPredicate(Pchar, '/', '?')
  def Fragment    = CharPredicate(Pchar, '/', '?')
  def Unreserved  = CharPredicate(Alpha, Digit, '-', '.', '_', '~')
  def Reserved    = CharPredicate(GenDelims, SubDelims)
  def GenDelims   = CharPredicate(':', '/', '?', '#', '[', ']', '@')
  def SubDelims   = CharPredicate('!', '$', '&', ''', '(', ')', '*', '+', ',', ';', '=')
}
