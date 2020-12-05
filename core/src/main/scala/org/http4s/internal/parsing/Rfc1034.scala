/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.internal.parsing

import cats.parse.Parser.{char, charIn, defer}
import cats.parse.Parser1
import org.http4s.util.{CaseInsensitiveString => CIString}

/** Common rules defined in RFC1034, as amended by RFC1123.
  *
  * @see [[https://tools.ietf.org/html/rfc1034] RFC1034, Domain Names: Concepts and Facilities]]
  * @see [[https://tools.ietf.org/html/rfc1123] RFC1123, Requirements for Internet Hosts -- Application and Support]]
  */
private[http4s] object Rfc1034 {
  def subdomain: Parser1[CIString] = {
    /* <letter> ::= any one of the 52 alphabetic characters A through Z in
     * upper case and a through z in lower case */
    val letter = charIn('A' to 'Z').orElse1(charIn('a' to 'z'))

    /* <digit> ::= any one of the ten digits 0 through 9 */
    val digit = charIn('0' to '9')

    /* <let-dig> ::= <letter> | <digit> */
    val letDig = letter.orElse1(digit)

    /* <let-dig-hyp> ::= <let-dig> | "-" */
    val letDigHyp = letDig.void.orElse1(char('-')).string

    /* <ldh-str> ::= <let-dig-hyp> | <let-dig-hyp> <ldh-str> */
    def ldhStr: Parser1[String] = (letDigHyp <* ldhStr.?).string

    /* <label> ::= <letter> [ [ <ldh-str> ] <let-dig> ]
     *
     * RFC1123: One aspect of host name syntax is hereby changed: the
     * restriction on the first character is relaxed to allow either a
     * letter or a digit.  Host software MUST support this more liberal
     * syntax.
     */
    val label = letDig <* ((letDigHyp.?).with1 *> letDig).?

    /* <subdomain> ::= <label> | <subdomain> "." <label> */
    label.orElse1(defer(subdomain).with1 <* char('.') <* label).string.map(CIString(_))
  }
}
