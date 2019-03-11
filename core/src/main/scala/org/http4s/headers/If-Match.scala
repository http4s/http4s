package org.http4s
package headers

import cats.data.NonEmptyList
import cats.syntax.foldable._
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `If-Match` extends HeaderKey.Internal[`If-Match`] with HeaderKey.Singleton {

  /** Match any existing entity */
  val `*` = `If-Match`(None)

  def apply(first: ETag.EntityTag, rest: ETag.EntityTag*): `If-Match` =
    `If-Match`(Some(NonEmptyList.of(first, rest: _*)))

  override def parse(s: String): ParseResult[`If-Match`] =
    HttpHeaderParser.IF_MATCH(s)
}

final case class `If-Match`(tags: Option[NonEmptyList[ETag.EntityTag]]) extends Header.Parsed {
  override def key: `If-Match`.type = `If-Match`
  override def value: String = tags match {
    case None => "*"
    case Some(tags) => tags.mkString_("", ",", "")
  }
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
