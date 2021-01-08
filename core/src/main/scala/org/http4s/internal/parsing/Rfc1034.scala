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

package org.http4s.internal.parsing

import cats.parse.Parser.{char, charIn, rep1Sep}
import cats.parse.Parser1
import org.typelevel.ci.CIString

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

    /* <let-dig-hyp> ::= <let-dig> | "-"
     * <ldh-str> ::= <let-dig-hyp> | <let-dig-hyp> <ldh-str>
     * <label> ::= <letter> [ [ <ldh-str> ] <let-dig> ]
     *
     * RFC1123: One aspect of host name syntax is hereby changed: the
     * restriction on the first character is relaxed to allow either a
     * letter or a digit.  Host software MUST support this more liberal
     * syntax.
     */
    val label = rep1Sep(letDig.rep1, 1, char('-'))

    /* <subdomain> ::= <label> | <subdomain> "." <label> */
    rep1Sep(label, 1, char('.')).string.map(CIString(_))
  }
}
