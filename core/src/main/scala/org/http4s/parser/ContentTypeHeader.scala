package org.http4s
package parser

import org.parboiled2._
import Header._

private[parser] trait ContentTypeHeader {

  def CONTENT_TYPE(value: String) = new ContentTypeParser(value).parse

  private class ContentTypeParser(input: ParserInput) extends Http4sHeaderParser[`Content-Type`](input) with MediaParser {
    def entry: _root_.org.parboiled2.Rule1[`Content-Type`] = rule {
      (MediaRangeDef ~ optional(zeroOrMore(MediaTypeExtension)) ~ EOL) ~> { (range: MediaRange, exts: Option[Seq[(String, String)]]) =>
        val mediaType = range match {
          case m: MediaType => m
          case m =>
            val err = new ParseErrorInfo("ContentType header doesn't accept MediaRanges", s"${m.getClass}, $m")
            throw new ParseException(err)
        }

        var charset: Option[CharacterSet] = None
        var ext = Map.empty[String, String]

        exts.foreach(_.foreach { case p @ (k, v) =>
          if (k == "charset") charset = Some(CharacterSet.resolve(v))
          else ext += p
        })

        `Content-Type`(if (ext.isEmpty) mediaType else mediaType.withExtensions(ext), charset)
      }
    }

  }

//  def CONTENT_TYPE = rule {
//    ContentTypeHeaderValue// ~~> (`Content-Type`(_))
//  }
//
//  lazy val ContentTypeHeaderValue = rule {
//    MediaTypeDef ~ EOI ~~> (createContentType(_, _, _))
//  }
//
//  private def createContentType(mainType: String, subType: String, params: Map[String, String]) = {
//    val mimeType = getMediaType(mainType, subType, params.get("boundary"))
//    val charset = params.get("charset").map(getCharset)
//    `Content-Type`(mimeType, charset)
//  }

}
