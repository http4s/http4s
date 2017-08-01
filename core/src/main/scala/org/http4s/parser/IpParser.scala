package org.http4s
package parser

import org.http4s.internal.parboiled2._
import org.http4s.internal.parboiled2.CharPredicate.{Alpha, Digit, HexDigit}

private[http4s] trait IpParser { this: Parser =>

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
}
