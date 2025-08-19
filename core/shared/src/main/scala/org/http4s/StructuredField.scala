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

import cats.parse.Parser
import cats.parse.Parser0
import cats.parse.Rfc5234
import org.http4s.internal.parsing.Rfc7230
import org.http4s.util.Renderable
import org.http4s.util.Writer
import scodec.bits.ByteVector

/** Structured Field Values for HTTP (RFC8941)
  *
  * @see [[https://datatracker.ietf.org/doc/html/rfc8941]]
  */
sealed trait StructuredField extends Renderable

object StructuredField {

  /* bare items */

  sealed trait BareItem extends Renderable

  object BareItem {

    /* bare-item = sf-integer / sf-decimal / sf-string / sf-token
     *           / sf-binary / sf-boolean
     */
    val parser: Parser[BareItem] =
      SfDecimal.parser.backtrack
        .orElse(SfInteger.parser)
        .orElse(SfString.parser)
        .orElse(SfToken.parser)
        .orElse(SfBinary.parser)
        .orElse(SfBoolean.parser)
  }

  sealed abstract case class SfInteger(value: Long) extends BareItem {
    override def render(writer: Writer): writer.type =
      writer << value
  }

  object SfInteger {
    private val MaxValue: Long = 999999999999999L

    private val MinValue: Long = -MaxValue

    private def unsafeFromLong(l: Long): SfInteger =
      new SfInteger(l) {}

    def fromLong(l: Long): Option[SfInteger] =
      if (l < MinValue || l > MaxValue) None else Some(unsafeFromLong(l))

    def fromInt(i: Int): SfInteger =
      unsafeFromLong(i.toLong)

    /* sf-integer = ["-"] 1*15DIGIT
     */
    val parser: Parser[SfInteger] = {
      import Parser.char, Rfc5234.digit
      val pos = digit.rep(1, 15).string
      val neg = (char('-') *> pos).string
      (pos | neg).map(s => unsafeFromLong(s.toLong))
    }
  }

  sealed abstract case class SfDecimal(value: BigDecimal) extends BareItem {
    override def render(writer: Writer): writer.type =
      if (value.scale > 3)
        writer << value.setScale(3, BigDecimal.RoundingMode.HALF_UP).toString
      else if (value.scale < 1)
        writer << value.setScale(1).toString
      else
        writer << value.toString
  }

  object SfDecimal {
    private val MaxValue: BigDecimal = BigDecimal("999999999999.999")

    private val MinValue: BigDecimal = -MaxValue

    private def unsafeFromBigDecimal(d: BigDecimal): SfDecimal =
      new SfDecimal(d) {}

    def fromBigDecimal(d: BigDecimal): Option[SfDecimal] =
      if (d < MinValue || d > MaxValue) None else Some(unsafeFromBigDecimal(d))

    /* sf-decimal = ["-"] 1*12DIGIT "." 1*3DIGIT
     */
    val parser: Parser[SfDecimal] = {
      import Parser.char, Rfc5234.digit
      val pos = (digit.rep(1, 12) *> char('.') *> digit.rep(1, 3)).string
      val neg = (char('-') *> pos).string
      (pos | neg).map(s => unsafeFromBigDecimal(BigDecimal(s)))
    }
  }

  sealed abstract case class SfString(value: String) extends BareItem {
    override def render(writer: Writer): writer.type =
      writer <<# value
  }

  object SfString {
    private def unsafeFromString(s: String): SfString =
      new SfString(s) {}

    def fromString(s: String): Option[SfString] =
      Parser
        .charIn(0x20.toChar to 0x7e.toChar)
        .rep0
        .string
        .map(unsafeFromString)
        .parseAll(s)
        .toOption

    /* sf-string = DQUOTE *chr DQUOTE
     * chr       = unescaped / escaped
     * unescaped = %x20-21 / %x23-5B / %x5D-7E
     * escaped   = "\" ( DQUOTE / "\" )
     */
    val parser: Parser[SfString] = {
      import Parser.{char, charIn}, Rfc5234.dquote
      val escaped = char('\\') *> charIn('\\', '"')
      val unescaped =
        charIn(0x20.toChar to 0x21.toChar)
          .orElse(charIn(0x23.toChar to 0x5b.toChar))
          .orElse(charIn(0x5d.toChar to 0x7e.toChar))
      val chr = unescaped | escaped
      (dquote *> chr.rep0 <* dquote).map(xs => unsafeFromString(xs.mkString))
    }
  }

  sealed abstract case class SfToken(value: String) extends BareItem {
    override def render(writer: Writer): writer.type =
      writer << value
  }

  object SfToken {
    private def unsafeFromString(s: String): SfToken =
      new SfToken(s) {}

    def fromString(s: String): Option[SfToken] =
      parser.parseAll(s).toOption

    /* sf-token = ( ALPHA / "*" ) *( tchar / ":" / "/" )
     */
    val parser: Parser[SfToken] = {
      import Parser.{char, charIn}, Rfc5234.alpha, Rfc7230.tchar
      val head = alpha | char('*')
      val tail = (tchar | charIn(':', '/')).rep0
      (head *> tail).string.map(unsafeFromString)
    }
  }

  final case class SfBinary(value: ByteVector) extends BareItem {
    override def render(writer: Writer): writer.type =
      writer << ':' << value.toBase64 << ':'
  }

  object SfBinary {

    /* sf-binary = ":" *(base64) ":"
     * base64    = ALPHA / DIGIT / "+" / "/" / "="
     */
    val parser: Parser[SfBinary] = {
      import Parser.{char, charIn}, Rfc5234.{alpha, digit}
      val base64 = (alpha | digit | charIn('+', '/', '=')).rep0.string
      (char(':') *> base64 <* char(':')).mapFilter(ByteVector.fromBase64(_)).map(apply)
    }
  }

  final case class SfBoolean(value: Boolean) extends BareItem {
    override def render(writer: Writer): writer.type =
      value match {
        case false => writer << "?0"
        case true => writer << "?1"
      }
  }

  object SfBoolean {

    /* sf-boolean = "?" boolean
     * boolean    = "0" / "1"
     */
    val parser: Parser[SfBoolean] = {
      import Parser.char
      (char('?') *> (char('0').as(false) | char('1').as(true))).map(apply)
    }
  }

  /* key & parameters */

  sealed abstract case class Key(value: String) extends Renderable {
    override def render(writer: Writer): writer.type =
      writer << value
  }

  object Key {
    private def unsafeFromString(s: String): Key =
      new Key(s) {}

    def fromString(s: String): Option[Key] =
      parser.parseAll(s).toOption

    /* key      = ( lcalpha / "*" ) *( lcalpha / DIGIT / "_" / "-" / "." / "*" )
     * lcalpha  = %x61-7A ; a-z
     */
    val parser: Parser[Key] = {
      import Parser.{char, charIn}, Rfc5234.digit
      val lcalpha = charIn('a' to 'z')
      val head = lcalpha | char('*')
      val tail = (lcalpha | digit | charIn('_', '-', '.', '*')).rep0
      (head *> tail).string.map(unsafeFromString)
    }
  }

  final case class Parameters(values: List[(Key, BareItem)]) extends Renderable {
    override def render(writer: Writer): writer.type = {
      def toRenderable(kv: (Key, BareItem)): Renderable =
        new Renderable {
          def render(writer: Writer): writer.type =
            kv match {
              case (key, SfBoolean(true)) =>
                writer << ';' << key
              case (key, value) =>
                writer << ';' << key << '=' << value
            }
        }
      writer.addList(values.map(toRenderable), "", "", "")
    }
  }

  object Parameters {

    /* parameters = *( ";" *SP parameter )
     * parameter  = key [ "=" bare-item ]
     */
    val parser: Parser0[Parameters] = {
      import Parser.char, Rfc5234.sp
      val params = (char(';') *> sp.rep0 *> (Key.parser ~ (char('=') *> BareItem.parser).?)).rep0
      params
        .map(_.map(kv => (kv._1, kv._2.fold[BareItem](SfBoolean(true))(identity))))
        .map(apply)
    }
  }

  /* members */

  sealed trait Member extends Renderable

  object Member {

    /* member = sf-item / inner-list
     */
    val parser: Parser[Member] =
      SfItem.parser | InnerList.parser
  }

  final case class SfItem(value: BareItem, params: Parameters) extends Member with StructuredField {
    override def render(writer: Writer): writer.type =
      writer << value << params
  }

  object SfItem {

    /* sf-item = bare-item parameters
     */
    val parser: Parser[SfItem] =
      (BareItem.parser ~ Parameters.parser).map(t => apply(t._1, t._2))
  }

  final case class InnerList(values: List[SfItem], params: Parameters) extends Member {
    override def render(writer: Writer): writer.type =
      writer.addList(values, " ", "(", ")").append(params)
  }

  object InnerList {

    /* inner-list = "(" *SP [ sf-item *( 1*SP sf-item ) *SP ] ")" parameters
     */
    val parser: Parser[InnerList] = {
      import Parser.char, Rfc5234.sp
      val items = char('(') *> sp.rep0 *> SfItem.parser.repSep0(sp.rep) <* sp.rep0 <* char(')')
      (items ~ Parameters.parser).map(t => apply(t._1, t._2))
    }
  }

  /* containers */

  final case class SfList(values: List[Member]) extends StructuredField {
    override def render(writer: Writer): writer.type =
      writer.addList(values, ", ", "", "")
  }

  object SfList {

    /* sf-list = member *( OWS "," OWS member )
     */
    val parser: Parser[SfList] = {
      import Parser.char, Rfc7230.ows
      val list = Member.parser ~ (ows.with1 *> char(',') *> ows *> Member.parser).rep0
      list
        .map(t => t._1 +: t._2)
        .map(apply)
    }
  }

  final case class SfDictionary(values: List[(Key, Member)]) extends StructuredField {
    override def render(writer: Writer): writer.type = {
      def toRenderable(kv: (Key, Member)): Renderable =
        new Renderable {
          def render(writer: Writer): writer.type =
            kv match {
              case (key, SfItem(SfBoolean(true), params)) =>
                writer << key << params
              case (key, value) =>
                writer << key << '=' << value
            }
        }
      writer.addList(values.map(toRenderable), ", ", "", "")
    }
  }

  object SfDictionary {

    /* sf-dictionary = dict-member *( OWS "," OWS dict-member )
     * dict-member   = key ( parameters / ( "=" member ))
     */
    val parser: Parser[SfDictionary] = {
      import Parser.char, Rfc7230.ows
      val pair = Key.parser ~ (char('=') *> Member.parser).eitherOr(Parameters.parser)
      val dict = pair ~ (ows.with1 *> char(',') *> ows *> pair).rep0
      dict
        .map(t => t._1 +: t._2)
        .map(_.map(kv => (kv._1, kv._2.fold(p => SfItem(SfBoolean(true), p), identity))))
        .map(apply)
    }
  }
}
