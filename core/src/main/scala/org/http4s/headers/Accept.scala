package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.{MediaParser, Http4sHeaderParser}
import org.parboiled2._

import scalaz.NonEmptyList

object Accept extends HeaderKey.Internal[Accept] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[Accept] =
    new AcceptParser(raw.value).parse.toOption

  private class AcceptParser(value: String) extends Http4sHeaderParser[Accept](value) with MediaParser {

    def entry: Rule1[headers.Accept] = rule {
      oneOrMore(FullRange).separatedBy("," ~ OptWS) ~ EOL ~> { xs: Seq[MediaRange] =>
        Accept(xs.head, xs.tail: _*)}
    }

    def FullRange: Rule1[MediaRange] = rule {
      (MediaRangeDef ~ optional( QAndExtensions )) ~> {
        (mr: MediaRange, params: Option[(QValue, Seq[(String, String)])]) =>
          params.map{ case (q, extensions) =>
            val m1 = if (q != org.http4s.QValue.One) mr.withQValue(q) else mr
            if (extensions.isEmpty) m1 else m1.withExtensions(extensions.toMap)
          }.getOrElse(mr)
      }
    }

    def QAndExtensions: Rule1[(QValue, Seq[(String, String)])] = rule {
      AcceptParams | (oneOrMore(MediaTypeExtension) ~> {s: Seq[(String, String)] => (org.http4s.QValue.One, s) })
    }

    def AcceptParams: Rule1[(QValue, Seq[(String, String)])] = rule {
      (";" ~ OptWS ~ "q" ~ "=" ~ QValue ~ zeroOrMore(MediaTypeExtension)) ~> ((_:QValue,_:Seq[(String, String)]))
    }
  }
}

final case class Accept(values: NonEmptyList[MediaRange]) extends Header.RecurringRenderable {
  def key = Accept
  type Value = MediaRange
}

