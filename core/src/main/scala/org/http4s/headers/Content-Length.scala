package org.http4s
package headers

import org.http4s.util.Writer

object `Content-Length` extends HeaderKey.Internal[`Content-Length`] with HeaderKey.Singleton

final case class `Content-Length`(length: Int) extends Header.Parsed {
  override def key = `Content-Length`
  override def renderValue(writer: Writer): writer.type = writer.append(length)
}

