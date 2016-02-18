package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

/**
  * Constructs a `Content-Length` header.
  *
  * The HTTP RFCs do not specify a maximum length.  We have decided that [[Long.MaxValue]]
  * bytes ought to be good enough for anybody in order to avoid the irritations of [[scala.math.BigInt]].
  *
  * @param length the length; throws an [[IllegalArgumentException]] if negative
  */
final case class `Content-Length`(length: Long) extends Header.Parsed {
  require(length >= 0L)

  override def key = `Content-Length`
  override def renderValue(writer: Writer): writer.type = writer.append(length)
}

object `Content-Length` extends HeaderKey.Internal[`Content-Length`] with HeaderKey.Singleton {
  def fromLong(length: Long): ParseResult[`Content-Length`] =
    if (length >= 0L) ParseResult.success(`Content-Length`(length))
    else ParseResult.fail("Invalid Content-Length", length.toString)

  def fromString(s: String): ParseResult[`Content-Length`] =
    HttpHeaderParser.CONTENT_LENGTH(s)
}

