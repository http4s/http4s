package org.http4s.parser

import org.http4s.MediaRange
import org.parboiled2._

/**
 * @author Bryce Anderson
 *         Created on 12/23/13
 */
trait MediaParser extends CommonActions { self: Http4sHeaderParser[_] =>

  def MediaRangeDef: Rule1[MediaRange] = rule {
    (("*/*" ~ push("*") ~ push("*"))              |
      (Token ~ "/" ~ (("*" ~ push("*")) | Token)) |
      ("*" ~ push("*") ~ push("*"))) ~> (getMediaRange(_, _))
  }

  private def getMediaRange(mainType: String, subType: String): MediaRange = {
    if (subType == "*") {
      val mainTypeLower = mainType.toLowerCase
      MediaRange.resolve(mainTypeLower)
    } else {
      getMediaType(mainType, subType)
    }
  }
}
