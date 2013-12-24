package org.http4s
package parser

import org.parboiled2._
import scalaz.Validation
import org.http4s.Header.Accept
import org.http4s.parser.MediaParser

private[parser] trait AcceptHeader {

  def ACCEPT(value: String): Validation[ParseErrorInfo, Accept] = new AcceptParser(value).parse

  private class AcceptParser(value: String) extends Http4sHeaderParser[Accept](value) with MediaParser {

    def entry: Rule1[Header.Accept] = rule {
      oneOrMore(FullRange).separatedBy("," ~ OptWS) ~ EOI ~> { xs: Seq[MediaRange] =>
        Header.Accept(xs.head, xs.tail: _*)}
    }

    def FullRange: Rule1[MediaRange] = rule {
      (MediaRangeDef ~ optional( QAndExtensions )) ~> {
        (mr: MediaRange, params: Option[(Float, Seq[(String, Option[String])])]) =>
          mr  // TODO: need to provide space for the q factor and extensions!
      }
    }

    def QAndExtensions: Rule1[(Float, Seq[(String, Option[String])])] = rule {
      AcceptParams | (oneOrMore(AcceptExtension) ~> {s: Seq[(String, Option[String])] => (1.0f, s) })
    }

//    def MediaRangeDecl: Rule2[MediaRange, Seq[(String, String)]] = rule {
//      MediaRangeDef ~ zeroOrMore(";" ~OptWS ~ Parameter) // TODO: support parameters
//    }



    def AcceptParams: Rule1[(Float, Seq[(String, Option[String])])] = rule {
      (";" ~ "q" ~ "=" ~ QValue ~ zeroOrMore(AcceptExtension)) ~> ((_:Float,_:Seq[(String, Option[String])]))
    }

    def AcceptExtension: Rule1[(String, Option[String])] = rule {
      ";" ~ Token ~ optional("=" ~ (Token | QuotedString)) ~> ((_: String, _: Option[String]))
    }

    /* 3.9 Quality Values */

    def QValue: Rule1[Float] = rule {
      // more loose than the spec which only allows 1 to max. 3 digits/zeros
      (capture(ch('0') ~ ch('.') ~ oneOrMore(Digit)) ~> (_.toFloat)) | (ch('1') ~
        optional(ch('.') ~ zeroOrMore(ch('0'))) ~ push(1.0f)) ~ OptWS
    }
  }
}
