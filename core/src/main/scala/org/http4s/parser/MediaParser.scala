package org.http4s.parser

import org.http4s.{MediaType, MediaRange}
import org.parboiled2._

/**
 * @author Bryce Anderson
 *         Created on 12/23/13
 */
trait MediaParser { self: Http4sHeaderParser[_] =>

  def MediaRangeDef: Rule1[MediaRange] = rule {
    (("*/*" ~ push("*") ~ push("*"))              |
      (Token ~ "/" ~ (("*" ~ push("*")) | Token)) |
      ("*" ~ push("*") ~ push("*"))) ~> (getMediaRange(_, _))
  }

  def MediaTypeExtension: Rule1[(String, String)] = rule {
    ";" ~ OptWS ~ Token ~ optional("=" ~ (Token | QuotedString)) ~> { (s: String, s2: Option[String]) =>
      (s, s2.getOrElse(""))
    }
  }

  private def getMediaRange(mainType: String, subType: String): MediaRange = {
    if (subType == "*") {
      val mainTypeLower = mainType.toLowerCase
      MediaRange.resolve(mainTypeLower)
    } else {
      MediaType.lookupOrElse((mainType.toLowerCase, subType.toLowerCase),
        new MediaType(mainType, subType))
    }
  }
}
