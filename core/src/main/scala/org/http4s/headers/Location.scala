package org.http4s
package headers

import org.http4s.util.Writer

object Location extends HeaderKey.Internal[Location] with HeaderKey.Singleton

final case class Location(uri: Uri) extends ParsedHeader {
  def key = `Location`
  override def value = uri.toString
  def renderValue(writer: Writer): writer.type = writer << uri.toString
}

