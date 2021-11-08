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

import cats.parse.Rfc5234
import cats.parse.{Parser, Parser0}

/** Parsers for the rules defined in RFC8941
  *
  * @see [[https://datatracker.ietf.org/doc/html/rfc8941]]
  */
private[http4s] object Rfc8941 {

  /* sf-integer = ["-"] 1*15DIGIT
   */
  val sfInteger: Parser[Long] = {
    import Parser.char, Rfc5234.digit
    val pos = digit.rep(1, 15).string
    val neg = (char('-') *> pos).map(s => s"-$s")
    (pos | neg).map(_.toLong)
  }

  /* sf-decimal = ["-"] 1*12DIGIT "." 1*3DIGIT
   */
  val sfDecimal: Parser[BigDecimal] = {
    import Parser.char, Rfc5234.digit
    val num = digit.rep(1, 12).string
    val dec = digit.rep(1, 3).string
    val pos = (num ~ (char('.') *> dec)).map(t => s"${t._1}.${t._2}")
    val neg = (char('-') *> pos).map(s => s"-$s")
    (pos | neg).map(BigDecimal(_))
  }

  /* sf-string = DQUOTE *chr DQUOTE
   * chr       = unescaped / escaped
   * unescaped = %x20-21 / %x23-5B / %x5D-7E
   * escaped   = "\" ( DQUOTE / "\" )
   */
  val sfString: Parser[String] = {
    import Parser.{charIn, string}, Rfc5234.dquote
    val escaped = string("\\\\") | string("\\\"")
    val unescaped =
      charIn(0x20.toChar to 0x21.toChar)
        .orElse(charIn(0x23.toChar to 0x5b.toChar))
        .orElse(charIn(0x5d.toChar to 0x7e.toChar))
        .string
    val chr = unescaped | escaped
    (dquote *> chr.rep0.string <* dquote).map(s => s""""$s"""")
  }

  /* sf-token = ( ALPHA / "*" ) *( tchar / ":" / "/" )
   */
  val sfToken: Parser[String] = {
    import Parser.{char, charIn}, Rfc5234.alpha, Rfc7230.tchar
    val head = (alpha | char('*')).string
    val tail = (tchar | charIn(":/".toList)).rep0.string
    (head ~ tail).map(t => t._1 + t._2)
  }

  /* sf-binary = ":" *(base64) ":"
   * base64    = ALPHA / DIGIT / "+" / "/" / "="
   */
  val sfBinary: Parser[String] = {
    import Parser.{char, charIn}, Rfc5234.{alpha, digit}
    val base64 = (alpha | digit | charIn("+/=".toList)).rep0.string
    char(':') *> base64 <* char(':')
  }

  /* sf-boolean = "?" boolean
   * boolean    = "0" / "1"
   */
  val sfBoolean: Parser[Boolean] = {
    import Parser.string
    (string("?0").as(false) | string("?1").as(true))
  }

  /* bare-item = sf-integer / sf-decimal / sf-string / sf-token
   *           / sf-binary / sf-boolean
   */
  def bareItem[A](
      sfIntegerP: Parser[A],
      sfDecimalP: Parser[A],
      sfStringP: Parser[A],
      sfTokenP: Parser[A],
      sfBinaryP: Parser[A],
      sfBooleanP: Parser[A]
  ): Parser[A] =
    sfDecimalP.backtrack
      .orElse(sfIntegerP)
      .orElse(sfStringP)
      .orElse(sfTokenP)
      .orElse(sfBinaryP)
      .orElse(sfBooleanP)

  /* key      = ( lcalpha / "*" ) *( lcalpha / DIGIT / "_" / "-" / "." / "*" )
   * lcalpha  = %x61-7A ; a-z
   */
  val key: Parser[String] = {
    import Parser.{char, charIn}, Rfc5234.digit
    val lcalpha = charIn('a' to 'z')
    val head = (lcalpha | char('*')).string
    val tail = (lcalpha | digit | charIn("_-.*".toList)).rep0.string
    (head ~ tail).map(t => t._1 + t._2)
  }

  /* parameters = *( ";" *SP parameter )
   * parameter  = key [ "=" bare-item ]
   */
  def parameters[K, B](keyP: Parser[K], bareItemP: Parser[B]): Parser0[List[(K, Option[B])]] = {
    import Parser.char, Rfc5234.sp
    val param = char(';') *> sp.rep0 *> (keyP ~ (char('=') *> bareItemP).?)
    param.rep0
  }

  /* sf-item = bare-item parameters
   */
  def sfItem[B, P](bareItemP: Parser[B], parametersP: Parser0[P]): Parser[(B, P)] =
    bareItemP ~ parametersP

  /* inner-list = "(" *SP [ sf-item *( 1*SP sf-item ) *SP ] ")" parameters
   */
  def innerList[I, P](sfItemP: Parser[I], parametersP: Parser0[P]): Parser[(List[I], P)] = {
    import Parser.char, Rfc5234.sp
    val items = char('(') *> sp.rep0 *> sfItemP.repSep0(sp.rep) <* sp.rep0 <* char(')')
    items ~ parametersP
  }

  /* member = sf-item / inner-list
   */
  def member[M](sfItemP: Parser[M], innerListP: Parser[M]): Parser[M] =
    sfItemP | innerListP

  /* sf-list = member *( OWS "," OWS member )
   */
  def sfList[M](memberP: Parser[M]): Parser[List[M]] = {
    import Parser.char, Rfc7230.ows
    val list = memberP ~ (ows.with1 *> char(',') *> ows *> memberP).rep0
    list.map(t => t._1 +: t._2)
  }

  /* sf-dictionary = dict-member *( OWS "," OWS dict-member )
   * dict-member   = key ( parameters / ( "=" member ))
   */
  def sfDictionary[K, P, M](
      keyP: Parser[K],
      parametersP: Parser0[P],
      memberP: Parser[M]
  ): Parser[List[(K, Either[P, M])]] = {
    import Parser.char, Rfc7230.ows
    val pair = keyP ~ (char('=') *> memberP).eitherOr(parametersP)
    val dict = pair ~ (ows.with1 *> char(',') *> ows *> pair).rep0
    dict.map(t => t._1 +: t._2)
  }
}
