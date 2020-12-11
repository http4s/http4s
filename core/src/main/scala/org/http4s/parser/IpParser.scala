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

import org.http4s.internal.parboiled2._
import org.http4s.internal.parboiled2.CharPredicate.{Digit, HexDigit}

private[http4s] trait IpParser { this: Parser =>

  def IpV6Address: Rule0 =
    rule {
      6.times(H16 ~ ":") ~ LS32 |
        "::" ~ 5.times(H16 ~ ":") ~ LS32 |
        optional(H16) ~ "::" ~ 4.times(H16 ~ ":") ~ LS32 |
        optional((1 to 2).times(H16).separatedBy(":")) ~ "::" ~ 3.times(H16 ~ ":") ~ LS32 |
        optional((1 to 3).times(H16).separatedBy(":")) ~ "::" ~ 2.times(H16 ~ ":") ~ LS32 |
        optional((1 to 4).times(H16).separatedBy(":")) ~ "::" ~ H16 ~ ":" ~ LS32 |
        optional((1 to 5).times(H16).separatedBy(":")) ~ "::" ~ LS32 |
        optional((1 to 6).times(H16).separatedBy(":")) ~ "::" ~ H16 |
        optional((1 to 7).times(H16).separatedBy(":")) ~ "::"
    }

  def H16 = rule((1 to 4).times(HexDigit))

  def LS32 = rule((H16 ~ ":" ~ H16) | IpV4Address)

  def IpV4Address = rule(3.times(DecOctet ~ ".") ~ DecOctet)

  def DecOctet =
    rule {
      "1" ~ Digit ~ Digit |
        "2" ~ ("0" - "4") ~ Digit |
        "25" ~ ("0" - "5") |
        ("1" - "9") ~ Digit |
        Digit
    }
}
