package org.http4s.codec

import cats.parse.Parser
import cats.parse.Parser0
import cats.parse.Rfc5234
import cats.~>

object catsParserDecoder extends (Http1Codec.Op ~> Parser0) {
  def apply[A](op: Http1Codec.Op[A]): Parser0[A] =
    op match {
      case Http1Codec.StringLiteral(s) => Parser.string(s)
      case Http1Codec.CharLiteral(c) => Parser.char(c)
      case Http1Codec.Digit => Rfc5234.digit
      case Http1Codec.ListOf(codec) =>
        codec.foldMap(this).asInstanceOf[Parser[A]].rep0
    }
}
