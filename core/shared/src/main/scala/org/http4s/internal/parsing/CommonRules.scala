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
import cats.parse.Parser
import cats.parse.Parser.char
import cats.parse.Parser.charIn
import cats.parse.Parser0
import cats.parse.Rfc5234.alpha
import cats.parse.Rfc5234.digit
import cats.parse.Rfc5234.dquote
import cats.parse.Rfc5234.htab
import cats.parse.Rfc5234.sp
import cats.parse.Rfc5234.vchar

private[parsing] trait CommonRules {
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

  /*https://datatracker.ietf.org/doc/html/rfc7230#section-3.2.3 last paragraph */
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

  private def surroundedBy[A](a: Parser0[A], b: Parser[Any]): Parser[A] =
    b *> a <* b
  private def between[A](a: Parser[Any], b: Parser0[A], c: Parser[Any]): Parser[A] =
    a *> b <* c

  /*quoted-string  = DQUOTE *( qdtext / quoted-pair ) DQUOTE*/
  val quotedString: Parser[String] =
    surroundedBy(qdText.orElse(quotedPair).rep0.string, dquote)

  /* HTAB / SP / %x21-27 / %x2A-5B / %x5D-7E / obs-text */
  val cText: Parser[Char] =
    charIn('\t', ' ', 0x21.toChar)
      .orElse(charIn(0x21.toChar to 0x27.toChar))
      .orElse(charIn(0x2a.toChar to 0x5b.toChar))
      .orElse(charIn(0x5d.toChar to 0x7e.toChar))
      .orElse(obsText)

  /* "(" *( ctext / quoted-pair / comment ) ")" */
  def comment(maxDepth: Int): Parser[String] = {
    def go(n: Int): Parser[String] =
      between(
        char('('),
        if (n <= 0) Parser.failWith("exceeded maximum comment depth")
        else cText.orElse(quotedPair).orElse[Any](go(n - 1)).rep0.string,
        char(')'),
      )
    go(maxDepth)
  }

  final val CommentDefaultMaxDepth = 10

  @deprecated("Use comment(Int) instead", "0.23.17")
  private[http4s] lazy val comment: Parser[String] =
    comment(CommentDefaultMaxDepth)

  def headerRep[A](element: Parser[A]): Parser0[List[A]] =
    headerRep1(element).?.map(_.fold(List.empty[A])(_.toList))

  /* `1#element => *( "," OWS ) element *( OWS "," [ OWS element ] )` */
  def headerRep1[A](element: Parser[A]): Parser[NonEmptyList[A]] = {
    val prelude = (char(',') <* ows).rep0
    val tailOpt = (ows.with1 *> char(',') *> (ows.with1 *> element).?).rep0
    val tail = tailOpt.map(_.collect { case Some(x) => x })

    (prelude.with1 *> element ~ tail).map { case (h, t) =>
      NonEmptyList(h, t)
    }
  }

  def listSep: Parser[Unit] = Parser.char(',').surroundedBy(ows)
}

/** Common rules defined in RFC7230
  *
  * @see [[https://datatracker.ietf.org/doc/html/rfc7230#appendix-B]]
  */
private[http4s] object CommonRules extends CommonRules
