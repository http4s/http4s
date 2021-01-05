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

import cats.data.NonEmptyList
import cats.parse.{Parser0, Parser}
import cats.parse.Parser.{char, charIn}
import cats.parse.Rfc5234.{alpha, digit, dquote, htab, sp, vchar}

/** Common rules defined in RFC7230
  *
  * @see [[https://tools.ietf.org/html/rfc7230#appendix-B]]
  */
private[http4s] object Rfc7230 {
  /* `tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
   *  "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA`
   */
  val tchar: Parser[Char] = charIn("!#$%&'*+-.^_`|~").orElse(digit).orElse(alpha)

  /* `token = 1*tchar` */
  val token: Parser[String] = tchar.rep.string

  /* `obs-text = %x80-FF` */
  val obsText: Parser[Char] = charIn(0x80.toChar to 0xff.toChar)

  /* `OWS = *( SP / HTAB )` */
  val ows: Parser0[Unit] = sp.orElse(htab).rep0.void

  /*https://tools.ietf.org/html/rfc7230#section-3.2.3 last paragraph */
  val bws: Parser0[Unit] = ows

  /*   qdtext         = HTAB / SP /%x21 / %x23-5B / %x5D-7E / obs-text */
  val qdText: Parser[Char] =
    charIn('\t', ' ', 0x21.toChar)
      .orElse(charIn(0x23.toChar to 0x5b.toChar))
      .orElse(charIn(0x5d.toChar to 0x7e.toChar))
      .orElse(obsText)

  val qdPairChar: Parser[Char] = charIn('\t', ' ').orElse(vchar).orElse(obsText)

  /* quoted-pair    = "\" ( HTAB / SP / VCHAR / obs-text ) */
  val quotedPair: Parser[Char] = char('\\') *> qdPairChar

  /*quoted-string  = DQUOTE *( qdtext / quoted-pair ) DQUOTE*/
  val quotedString: Parser0[String] =
    qdText.orElse(quotedPair).rep0.string.surroundedBy(dquote)

  /* `1#element => *( "," OWS ) element *( OWS "," [ OWS element ] )` */
  def headerRep1[A](element: Parser[A]): Parser[NonEmptyList[A]] = {
    val prelude = (char(',') <* ows).rep0
    val tailOpt = (ows.with1 *> char(',') *> (ows.with1 *> element).?).rep0
    val tail = tailOpt.map(_.collect { case Some(x) => x })

    (prelude.with1 *> element ~ tail).map { case (h, t) =>
      NonEmptyList(h, t)
    }
  }
}
