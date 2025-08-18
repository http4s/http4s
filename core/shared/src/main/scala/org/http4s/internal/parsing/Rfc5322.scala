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

import cats.parse.Parser
import cats.parse.Parser.char
import cats.parse.Parser.charIn
import cats.parse.Parser.string
import cats.parse.Parser.void
import cats.parse.Rfc5234.alpha
import cats.parse.Rfc5234.crlf
import cats.parse.Rfc5234.digit
import cats.parse.Rfc5234.dquote
import cats.parse.Rfc5234.vchar
import cats.parse.Rfc5234.wsp

object Rfc5322 {
  val FWS: Parser[Unit] = (wsp.rep0 *> crlf).backtrack.?.with1 *> void(wsp.rep)
  val ctext: Parser[String] = charIn(((33 to 39) ++ (42 to 91) ++ (93 to 126)).map(_.toChar)).string
  val `quoted-pair`: Parser[String] = char('\\') *> (vchar.string | string(wsp))
  val ccontent: Parser[Unit] = Parser.defer(ctext | `quoted-pair` | comment).void
  val comment: Parser[Unit] =
    Parser.defer(char('(') ~ (FWS.?.with1 ~ ccontent).backtrack.rep0 ~ FWS.? ~ char(')')).void
  val CFWS: Parser[Unit] = ((FWS.?.with1 ~ comment).backtrack.rep ~ FWS.?).void | FWS
  val atext: Parser[String] = (alpha | digit | charIn(
    '!', '#', '$', '%', '&', '\'', '*', '+', '-', '/', '=', '?', '^', '_', '`', '{', '|', '}', '~',
  )).string
  val atom: Parser[String] = CFWS.?.with1 *> atext.rep.map(_.toList.mkString) <* CFWS.?
  val `dot-atom-text`: Parser[String] = (atext.rep.map(_.toList.mkString) ~
    (char('.').string ~ atext.rep
      .map(_.toList.mkString)).map(p => p._1 + p._2).rep0.map(_.mkString))
    .map(p => p._1 + p._2)
  val `dot-atom`: Parser[String] = `dot-atom-text`.between(CFWS.?, CFWS.?)
  val qtext: Parser[String] = charIn((Seq(33) ++ (35 to 91) ++ (93 to 126)).map(_.toChar)).string
  val qcontent: Parser[String] = qtext | `quoted-pair`
  val `quoted-string`: Parser[String] =
    CFWS.?.with1 *> dquote *> (FWS.string.?.map(_.getOrElse("")).with1 ~ qcontent)
      .map(p => p._1 + p._2)
      .rep0
      .map(_.mkString) <* FWS.? <* dquote <* CFWS.?
  val word: Parser[String] = atom | `quoted-string`
  val phrase: Parser[String] = word.rep.map(_.toList.mkString)
  val `display-name`: Parser[String] = phrase
  val `local-part`: Parser[String] = (`dot-atom` | `quoted-string`).filter(_.length <= 64)
  val dtext: Parser[String] = charIn(((33 to 90) ++ (94 to 126)).map(_.toChar)).map(_.toString)
  val `domain-literal`: Parser[String] = (
    char('[').string ~
      (FWS.string.?.map(_.getOrElse("")).with1 ~ dtext).backtrack
        .map(p => p._1 + p._2)
        .rep0
        .map(_.mkString) ~
      FWS.string.?.map(_.getOrElse("")) ~
      char(']').string
  ).map { case (((s1, s2), s3), s4) => s1 + s2 + s3 + s4 }.between(CFWS.?, CFWS.?)
  val domain: Parser[String] = `dot-atom` | `domain-literal`
  val `addr-spec`: Parser[String] = (`local-part` ~ char('@').string ~ domain).map {
    case ((s1, s2), s3) => s1 + s2 + s3
  }
  val `angle-addr`: Parser[String] =
    `addr-spec`.between(char('<'), char('>')).between(CFWS.?, CFWS.?)
  val `name-addr`: Parser[String] = `display-name`.?.with1 *> `angle-addr`
  val mailbox: Parser[String] = ((`name-addr`.backtrack | `addr-spec`) ~ Parser.end).map(_._1)
}
