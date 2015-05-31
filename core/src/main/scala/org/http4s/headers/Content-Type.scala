package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.{MediaParser, Http4sHeaderParser}
import org.http4s.util.Writer
import org.parboiled2.ParserInput

object `Content-Type` extends HeaderKey.Internal[`Content-Type`] with HeaderKey.Singleton  {

  def apply(mediaType: MediaType, charset: Charset): `Content-Type` = apply(mediaType, Some(charset))
  implicit def apply(mediaType: MediaType): `Content-Type` = apply(mediaType, None)

  override protected def parseHeader(raw: Raw): Option[`Content-Type`] =
    new ContentTypeParser(raw.value).parse.toOption

  private class ContentTypeParser(input: ParserInput) extends Http4sHeaderParser[`Content-Type`](input) with MediaParser {
    def entry: org.parboiled2.Rule1[`Content-Type`] = rule {
      (MediaRangeDef ~ optional(zeroOrMore(MediaTypeExtension)) ~ EOL) ~> { (range: MediaRange, exts: Option[Seq[(String, String)]]) =>
        val mediaType = range match {
          case m: MediaType => m
          case m =>
            throw new ParseException(ParseFailure("Invalid Content-Type header", "Content-Type header doesn't support media ranges"))
        }

        var charset: Option[Charset] = None
        var ext = Map.empty[String, String]

        exts.foreach(_.foreach { case p @ (k, v) =>
          if (k == "charset") charset = Charset.fromString(v).toOption
          else ext += p
        })

        `Content-Type`(if (ext.isEmpty) mediaType else mediaType.withExtensions(ext), charset)
      }
    }
  }
}

final case class `Content-Type`(mediaType: MediaType, definedCharset: Option[Charset]) extends Header.Parsed {
  override def key = `Content-Type`
  override def renderValue(writer: Writer): writer.type = definedCharset match {
    case Some(cs) => writer << mediaType << "; charset=" << cs
    case _        => mediaType.render(writer)
  }

  def withMediaType(mediaType: MediaType) =
    if (mediaType != this.mediaType) copy(mediaType = mediaType) else this
  def withCharset(charset: Charset) =
    if (noCharsetDefined || charset != definedCharset.get) copy(definedCharset = Some(charset)) else this
  def withoutDefinedCharset =
    if (isCharsetDefined) copy(definedCharset = None) else this

  def isCharsetDefined = definedCharset.isDefined
  def noCharsetDefined = definedCharset.isEmpty

  def charset: Charset = definedCharset.getOrElse(Charset.`ISO-8859-1`)
}

