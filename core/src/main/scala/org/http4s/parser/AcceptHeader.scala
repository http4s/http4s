package org.http4s
package parser

import org.parboiled2._
import scalaz.Validation
import org.http4s.Header.Accept

private[parser] trait AcceptHeader {

  def ACCEPT(value: String): Validation[ParseErrorInfo, Accept] = new AcceptParser(value).parse

  private class AcceptParser(value: String) extends Http4sHeaderParser[Accept](value) with MediaParser {

    def entry: Rule1[Header.Accept] = rule {
      oneOrMore(FullRange).separatedBy("," ~ OptWS) ~ EOI ~> { xs: Seq[MediaRange] =>
        Header.Accept(xs.head, xs.tail: _*)}
    }

    def FullRange: Rule1[MediaRange] = rule {
      (MediaRangeDef ~ optional( QAndExtensions )) ~> {
        (mr: MediaRange, params: Option[(Float, Seq[(String, String)])]) =>
          params.map{ case (q, extensions) => mr.withqextensions(q, extensions.toMap)}
            .getOrElse(mr)
      }
    }

    def QAndExtensions: Rule1[(Float, Seq[(String, String)])] = rule {
      AcceptParams | (oneOrMore(MediaTypeExtension) ~> {s: Seq[(String, String)] => (1.0f, s) })
    }

    def AcceptParams: Rule1[(Float, Seq[(String, String)])] = rule {
      (";" ~ OptWS ~ "q" ~ "=" ~ QValue ~ zeroOrMore(MediaTypeExtension)) ~> ((_:Float,_:Seq[(String, String)]))
    }



    /* 3.9 Quality Values */

    def QValue: Rule1[Float] = rule {
      // more loose than the spec which only allows 1 to max. 3 digits/zeros
      (capture(ch('0') ~ ch('.') ~ oneOrMore(Digit)) ~> (_.toFloat)) | (ch('1') ~
        optional(ch('.') ~ zeroOrMore(ch('0'))) ~ push(1.0f)) ~ OptWS
    }
  }
}
