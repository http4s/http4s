package org.http4s.internal.parsing

import cats.parse.Parser
import cats.parse.Rfc5234.{crlf, vchar, wsp}
import cats.parse.Parser.{char, charIn, string, void}

object Rfc5322 {
  val FWS: Parser[Unit] = (wsp.rep0 *> crlf).backtrack.?.with1 *> void(wsp.rep)
  val ctext: Parser[Char] = charIn(((33 to 39) ++ (42 to 91) ++ (93 to 126)).map(_.toChar))
  val `quoted-pair`: Parser[String] = char('\\') *> (vchar.string | string(wsp))
  val ccontent: Parser[String] = Parser.defer(ctext.map(_.toString) | `quoted-pair` | comment)
  val comment: Parser[String] = Parser.defer(char('(').string ~
    (FWS.string.?.map(_.getOrElse("")).with1 ~ ccontent)
      .backtrack
      .map(p => p._1 + p._2)
      .rep0.map(_.mkString) ~ FWS.string.?.map(_.getOrElse("")) ~ char(')').string)
  .map { case (((s1, s2), s3), s4) => s1 + s2 + s3 + s4 }
  val CFWS: Parser[String] = ((FWS.string.?.map(_.getOrElse("")).with1 ~ comment)
    .backtrack
    .map(p => p._1 + p._2)
    .rep
    .map(_.toList.mkString) ~ FWS.string.?.map(_.getOrElse("")))
    .map{ case (s1, s2) => s1 + s2 } | string(FWS)
}
