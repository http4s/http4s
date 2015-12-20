package org.http4s
package headers

import java.time.Instant

import org.http4s.util.{Renderer, Writer}

object `If-Modified-Since` extends HeaderKey.Internal[`If-Modified-Since`] with HeaderKey.Singleton

final case class `If-Modified-Since`(date: Instant) extends Header.Parsed {
  override def key: HeaderKey = `If-Modified-Since`
  override def value: String = Renderer.renderString(date)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

