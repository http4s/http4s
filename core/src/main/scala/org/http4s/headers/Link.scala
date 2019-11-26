package org.http4s.headers

import cats.data.NonEmptyList
import org.http4s.parser.HttpHeaderParser
import org.http4s.{Header, HeaderKey, ParseResult}

object Link extends HeaderKey.Internal[Link] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[Link] =
    HttpHeaderParser.LINK(s)
}

final case class Link(values: NonEmptyList[LinkValue]) extends Header.RecurringRenderable {
  override def key: Link.type = Link
  type Value = LinkValue
}
