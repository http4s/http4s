package org.http4s.parser

import org.http4s.{MediaRange, MediaType}
import org.http4s.internal.parboiled2._
import cats.syntax.eq._
import cats.instances.string._

private[parser] trait MediaParser { self: Http4sHeaderParser[_] =>

  def MediaRangeDef: Rule1[MediaRange] = rule {
    (("*/*" ~ push("*") ~ push("*")) |
      (Token ~ "/" ~ (("*" ~ push("*")) | Token)) |
      ("*" ~ push("*") ~ push("*"))) ~> (getMediaRange(_, _))
  }

  def MediaTypeExtension: Rule1[(String, String)] = rule {
    ";" ~ OptWS ~ Token ~ optional("=" ~ (Token | QuotedString)) ~> {
      (s: String, s2: Option[String]) =>
        (s, s2.getOrElse(""))
    }
  }

  private def getMediaRange(mainType: String, subType: String): MediaRange =
    if (subType === "*")
      MediaRange.standard.getOrElse(mainType.toLowerCase, new MediaRange(mainType))
    else MediaType.getOrElseCreate((mainType.toLowerCase, subType.toLowerCase))
}
