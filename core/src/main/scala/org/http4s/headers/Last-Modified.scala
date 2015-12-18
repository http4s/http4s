package org.http4s
package headers

import java.time.Instant

import org.http4s.util.{Renderer, Writer}

object `Last-Modified` extends HeaderKey.Internal[`Last-Modified`] with HeaderKey.Singleton

final case class `Last-Modified`(date: Instant) extends Header.Parsed {
  override def key = `Last-Modified`
  override def value = Renderer.renderString(date)
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

