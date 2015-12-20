package org.http4s
package headers

import java.time.Instant
import java.time.format.DateTimeFormatter

import org.http4s.util.{Renderer, Writer}

object Date extends HeaderKey.Internal[Date] with HeaderKey.Singleton

final case class Date(date: Instant) extends Header.Parsed {
  def key = `Date`
  override def value = Renderer.renderString((date))
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

