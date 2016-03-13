package org.http4s
package headers

import org.http4s.util.{Renderable, Writer}
import org.http4s.parser.HttpHeaderParser

import scalaz.NonEmptyList

object Accept extends HeaderKey.Internal[Accept] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[Accept] =
    HttpHeaderParser.ACCEPT(s)
}


case class MediaRangeAndQValue(mediaRange: MediaRange, qValue: QValue = QValue.One)
  extends Renderable {
  def render(writer: Writer): writer.type = {
    writer << mediaRange.withExtensions(Map.empty) << qValue
    mediaRange.renderExtensions(writer)
    writer
  }
}

object MediaRangeAndQValue {
  implicit def withDefaultQValue(mediaRange: MediaRange): MediaRangeAndQValue =
    MediaRangeAndQValue(mediaRange, QValue.One)
}

final case class Accept(values: NonEmptyList[MediaRangeAndQValue]) extends Header.RecurringRenderable {
  implicit
  def key = Accept
  type Value = MediaRangeAndQValue
}

